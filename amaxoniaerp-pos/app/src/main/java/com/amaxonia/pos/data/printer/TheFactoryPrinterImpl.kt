package com.amaxonia.pos.data.printer

import android.content.Context
import android.util.Log
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class TheFactoryPrinterImpl(
    context: Context,
    private val localStore: LocalStore
) : PrinterRepository {

    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    override suspend fun printReceipt(transaction: Transaction): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                // Cancelar cualquier documento fiscal que haya quedado abierto por un fallo previo
                Log.d(TAG, "printReceipt() → enviando comando '7' para cancelar documento fiscal abierto (si existe)")
                try {
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = "7"
                    )
                    Log.d(TAG, "printReceipt() → comando '7' enviado OK")
                } catch (e: Exception) {
                    // Si no habia documento abierto, la impresora puede rechazar el comando; es esperado
                    Log.d(TAG, "printReceipt() → comando '7' ignorado (${e.message}) — no habia documento abierto")
                }

                val commands = buildFiscalCommands(transaction)
                Log.d(TAG, "printReceipt() → ${commands.size} comandos a enviar a ${settings.ipAddress}:${settings.port}")
                commands.forEachIndexed { index, command ->
                    Log.d(TAG, "printReceipt() → [${index + 1}/${commands.size}] enviando: '${command.take(40)}'")
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = command
                    )
                    Log.d(TAG, "printReceipt() → [${index + 1}/${commands.size}] OK")
                }
                Log.d(TAG, "printReceipt() → todos los comandos enviados exitosamente")
                true
            }.onFailure { error ->
                Log.e(TAG, "printReceipt() → fallo: ${error.message}", error)
            }
        }
    }

    override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                val printerStateBefore = runCatching {
                    readPrinterState(settings)
                }.getOrNull()

                cancelOpenFiscalDocument(settings)

                val commands = buildCreditNoteCommands(
                    document = document,
                    printerSerial = document.printerSerial.ifBlank { printerStateBefore?.registeredMachineNumber.orEmpty() }
                )

                Log.d(TAG, "printCreditNote() → ${commands.size} comandos a enviar a ${settings.ipAddress}:${settings.port}")
                commands.forEachIndexed { index, command ->
                    Log.d(TAG, "printCreditNote() → [${index + 1}/${commands.size}] enviando: '${command.take(40)}'")
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = command
                    )
                    Log.d(TAG, "printCreditNote() → [${index + 1}/${commands.size}] OK")
                }

                val printerStateAfter = readPrinterState(settings)
                val fiscalNumber = printerStateAfter.lastCreditNoteNumber
                    .takeIf { it > 0 }
                    ?.toString()
                    ?.padStart(8, '0')
                    ?: throw IllegalStateException("No se pudo determinar el número fiscal de la nota de crédito")

                CreditNotePrintResult(
                    fiscalNumber = fiscalNumber,
                    printerSerial = printerStateAfter.registeredMachineNumber
                )
            }.onFailure { error ->
                Log.e(TAG, "printCreditNote() → fallo: ${error.message}", error)
            }
        }
    }

    /**
     * Builds the fiscal command list for The Factory HKA protocol.
     *
     * Protocol commands:
     * - "iR*{clientId}" — customer tax id / identification
     * - "iS*{clientName}" — customer name
     * - "@{text}"      — free text / comment line (non-fiscal)
     * - " {amount}{qty}{description}" — item line (space prefix = taxable item)
     *     - amount: price in cents, padded to 8 digits
     *     - qty: quantity * 1000, padded to 10 digits
     *     - description: up to 30 chars
     * - "3"   — subtotal
     * - "101" — close with cash payment
     * - "102" — close with debit card payment
     * - "103" — close with credit card payment
     * - "104" — close with other payment method
     * - "199" — close without specifying payment method
     */
    private fun buildFiscalCommands(transaction: Transaction): List<String> {
        val invoice = sanitizeText(transaction.invoiceNumber, maxLength = 12).ifBlank { "SINFACTURA" }
        val description = sanitizeText("VENTA $invoice", maxLength = 30)
        val fiscalAmount = (transaction.fiscalAmountBs ?: transaction.amount).coerceAtLeast(0.01)
        val amountField = formatAmount(fiscalAmount)
        val quantityField = "0000001000" // qty=1.000
        val itemLine = " $amountField$quantityField$description"

        val lines = mutableListOf<String>()

        // 1. Customer identification (if available)
        val clientId = sanitizeText(transaction.clienteIdentificacion, maxLength = 20)
        if (clientId.isNotBlank()) {
            lines += "iR*$clientId"
        }

        // 2. Customer name (if available)
        val clientName = sanitizeText(transaction.clienteNombre, maxLength = 30)
        if (clientName.isNotBlank()) {
            lines += "iS*$clientName"
        }

        // 3. Comment lines
        lines += "@AMAXONIA POS"
        lines += "@$description"

        // 4. Item line
        lines += itemLine

        // 5. Subtotal
        lines += "3"

        // 6. Payment close command
        lines += resolvePaymentCommand(transaction.paymentMethods, transaction.formaPago)

        return lines
    }

    private fun buildCreditNoteCommands(
        document: CreditNoteFiscalDocumentDto,
        printerSerial: String,
    ): List<String> {
        val lines = mutableListOf<String>()
        val referenceNumber = normalizeOriginalFiscalNumber(
            document.originalFiscalNumber.ifBlank { document.originalInvoiceCode }
        )
        val referenceDate = normalizePrinterDate(document.originalInvoiceDate.ifBlank { document.date })
        val normalizedPrinterSerial = sanitizeText(printerSerial, maxLength = 10)
        val taxCodes = resolveCreditNoteTaxCodes(document.lines)

        lines += "PH01${sanitizeText("NC ${document.creditNoteCode}", maxLength = 40)}"
        lines += "PH08${sanitizeText("FACT ${document.originalInvoiceCode}", maxLength = 40)}"

        val customerId = sanitizeText(document.customerIdentifier, maxLength = 20)
        if (customerId.isNotBlank()) {
            lines += "iR*$customerId"
        }

        val customerName = sanitizeText(document.customerName, maxLength = 30)
        if (customerName.isNotBlank()) {
            lines += "iS*$customerName"
        }

        lines += "iF*$referenceNumber"
        lines += "iD*$referenceDate"
        if (normalizedPrinterSerial.isNotBlank()) {
            lines += "iI*$normalizedPrinterSerial"
        }

        val address = sanitizeText(document.customerAddress, maxLength = 30)
        if (address.isNotBlank()) {
            lines += "i01$address"
        }

        val phone = sanitizeText(document.customerPhone, maxLength = 30)
        if (phone.isNotBlank()) {
            lines += "i02$phone"
        }

        val comment = sanitizeText(document.comment.ifBlank { "NC ${document.creditNoteCode}" }, maxLength = 30)
        lines += "A$comment"

        document.lines.forEach { line ->
            val taxCode = taxCodes[line.taxRate] ?: 0
            lines += buildCreditNoteItemLine(line, taxCode)
        }

        lines += "3"
        lines += "101"
        return lines
    }

    private fun buildCreditNoteItemLine(line: CreditNoteFiscalLineDto, taxCode: Int): String {
        val quantity = line.quantity.coerceAtLeast(0.001)
        val unitAmount = (line.totalWithTax / quantity).coerceAtLeast(0.01)
        val amountField = formatAmount(unitAmount)
        val quantityField = formatQuantity(quantity)
        val description = sanitizeText(line.description, maxLength = 30).ifBlank { "DEVOLUCION" }
        return "d$taxCode$amountField$quantityField$description"
    }

    private fun resolveCreditNoteTaxCodes(lines: List<CreditNoteFiscalLineDto>): Map<Double, Int> {
        return lines
            .map { it.taxRate }
            .distinct()
            .associateWith { taxRate ->
                when {
                    taxRate <= 0.0 -> 0
                    taxRate >= 20.0 -> 3
                    taxRate <= 8.0 -> 2
                    else -> 1
                }
            }
    }

    /**
     * Maps the app's formaPago string to the HKA fiscal payment close command.
     *
     * 101 = Cash (Efectivo / Contado)
     * 102 = Debit card
     * 103 = Credit card
     * 104 = Other (transfer, check, etc.)
     * 199 = Unspecified
     */
    private fun resolvePaymentCommand(paymentMethods: List<TransactionPaymentMethod>, formaPago: String): String {
        paymentMethods
            .maxByOrNull { it.amount }
            ?.fiscalCode
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return mapPaymentCommand(formaPago)
    }

    private fun mapPaymentCommand(formaPago: String): String {
        val normalized = formaPago.lowercase().trim()
        return when {
            normalized.contains("efectivo") || normalized.contains("contado") || normalized.contains("divisa") -> "101"
            normalized.contains("punto de venta") || normalized.contains("debito") || normalized.contains("debit") -> "102"
            normalized.contains("credito") || normalized.contains("credit") || normalized.contains("tarjeta") -> "103"
            normalized.contains("transfer") || normalized.contains("cheque") || normalized.contains("deposito") ||
                normalized.contains("zelle") || normalized.contains("pago movil") || normalized.contains("yappy") ||
                normalized.contains("nequi") || normalized.contains("solutech") || normalized.contains("sunmi") -> "104"
            normalized.isBlank() -> "101" // Default to cash
            else -> "199" // Unspecified
        }
    }

    /**
     * Formats a monetary amount as an 8-digit integer field.
     * The Factory fiscal item commands expect whole-unit amount in this field.
     * e.g., 580.00 -> "00000580"
     */
    private fun formatAmount(amount: Double): String {
        return amount.roundToInt()
            .toString()
            .padStart(8, '0')
    }

    private fun formatQuantity(quantity: Double): String {
        return (quantity * 1000).roundToInt()
            .toString()
            .padStart(10, '0')
    }

    private fun normalizeOriginalFiscalNumber(value: String): String {
        val digits = value.filter(Char::isDigit).takeLast(8)
        return digits.padStart(8, '0')
    }

    private fun normalizePrinterDate(value: String): String {
        return runCatching {
            LocalDate.parse(value).format(PRINTER_DATE_FORMATTER)
        }.getOrDefault(value.takeIf { it.matches(PRINTER_DATE_REGEX) } ?: LocalDate.now().format(PRINTER_DATE_FORMATTER))
    }

    private fun cancelOpenFiscalDocument(settings: TheFactorySettings) {
        try {
            sendTcpCommand(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = "7"
            )
        } catch (e: Exception) {
            Log.d(TAG, "cancelOpenFiscalDocument() → comando '7' ignorado (${e.message})")
        }
    }

    private fun readPrinterState(settings: TheFactorySettings): PrinterStateSnapshot {
        val response = sendTcpCommandForResponse(
            ipAddress = settings.ipAddress,
            port = settings.port.toInt(),
            command = "S1"
        )
        return parsePrinterState(response.toString(Charsets.UTF_8))
    }

    private fun sendTcpCommandForResponse(ipAddress: String, port: Int, command: String): ByteArray {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
            val encrypted = encryptCommand(command)
            socket.getOutputStream().use { outputStream ->
                outputStream.write(encrypted)
                outputStream.flush()
            }
            val response = readSocketResponse(socket)
            if (response.isEmpty()) {
                throw IllegalStateException("La impresora no respondió al comando $command")
            }
            return response
        }
    }

    private fun parsePrinterState(rawState: String): PrinterStateSnapshot {
        val parts = rawState
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.size >= 16) {
            return PrinterStateSnapshot(
                registeredMachineNumber = parts.getOrNull(13).orEmpty(),
                lastCreditNoteNumber = parts.getOrNull(6)?.toIntOrNull() ?: 0
            )
        }

        return PrinterStateSnapshot(
            registeredMachineNumber = parts.getOrNull(9).orEmpty(),
            lastCreditNoteNumber = parts.getOrNull(12)?.toIntOrNull() ?: 0
        )
    }

    private fun encryptCommand(command: String): ByteArray {
        val response = cryptography.encryptString(command)
        if (response.isError) {
            throw IllegalStateException(response.message ?: "No se pudo encriptar el comando para impresion")
        }
        return response.bytes ?: throw IllegalStateException("Respuesta de cifrado invalida")
    }

    private fun sendTcpCommand(ipAddress: String, port: Int, command: String) {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
            val encrypted = encryptCommand(command)
            Log.d(TAG, "sendTcpCommand() → cifrado OK (${encrypted.size} bytes), enviando...")
            val outputStream = socket.getOutputStream()
            outputStream.write(encrypted)
            outputStream.flush()

            val response = readSocketResponse(socket)
            Log.d(TAG, "sendTcpCommand() → respuesta: ${response.size} bytes, hex: ${response.take(8).joinToString(" ") { "%02X".format(it) }}")
            if (!isSuccessfulResponse(response)) {
                throw IllegalStateException(
                    "The Factory rechazo el comando fiscal '${command.take(12)}'"
                )
            }
        }
    }

    /**
     * Reads the response from the HKA device.
     *
     * Matches the SDK's ResponseSocket.getResponse() behaviour:
     * - Blocking read until EOF (-1), relying on the socket's soTimeout
     *   (SOCKET_TIMEOUT_MS) to guard against hangs.
     * - Byte-stripping: if the first byte of a chunk is in range 6..15,
     *   keep it; otherwise skip byte[0] (protocol framing byte).
     */
    private fun readSocketResponse(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                // SDK byte-stripping: skip first byte unless it's in 6..15
                val first = buffer[0].toInt() and 0xFF
                val offset = if (first in 6..15) 0 else 1
                if (bytesRead > offset) {
                    output.write(buffer, offset, bytesRead - offset)
                }
            }
        } catch (_: java.net.SocketTimeoutException) {
            // Timeout from soTimeout — treat whatever we have as the full response
        }

        return output.toByteArray()
    }

    private fun isSuccessfulResponse(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        val firstByte = response.first().toInt() and 0xFF
        return firstByte == ACK || firstByte == ENQ || firstByte == NUL || response.size > 10
    }

    private fun validateSettings(settings: TheFactorySettings) {
        if (!settings.isConfigured()) {
            throw IllegalStateException("Configura la IP y el puerto de The Factory HKA antes de imprimir")
        }
        if (settings.port.toIntOrNull() == null) {
            throw IllegalStateException("El puerto configurado para The Factory HKA no es valido")
        }
    }

    private fun sanitizeText(value: String, maxLength: Int): String {
        return value
            .uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
            .take(maxLength)
    }
    private companion object {
        const val TAG = "HkaPrinter"
        const val CONNECT_TIMEOUT_MS = 3000
        const val SOCKET_TIMEOUT_MS = 10000
        const val NUL = 0
        const val ENQ = 5
        const val ACK = 6
        val PRINTER_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val PRINTER_DATE_REGEX = Regex("\\d{2}/\\d{2}/\\d{4}")
    }
}

private data class PrinterStateSnapshot(
    val registeredMachineNumber: String,
    val lastCreditNoteNumber: Int,
)
