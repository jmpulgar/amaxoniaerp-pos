package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
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

                buildFiscalCommands(transaction).forEach { command ->
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = command
                    )
                }
                true
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
        val amountField = formatAmount(transaction.amount.coerceAtLeast(0.01))
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
     * Formats a monetary amount as cents padded to 8 digits.
     * e.g., 12.50 -> "00001250"
     */
    private fun formatAmount(amount: Double): String {
        return ((amount * 100).roundToInt())
            .toString()
            .padStart(8, '0')
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
            val outputStream = socket.getOutputStream()
            outputStream.write(encryptCommand(command))
            outputStream.flush()

            val response = readSocketResponse(socket)
            if (!isSuccessfulResponse(response)) {
                throw IllegalStateException(
                    "The Factory rechazo el comando fiscal '${command.take(12)}'"
                )
            }
        }
    }

    private fun readSocketResponse(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break
            val firstByte = buffer[0].toInt()
            val offset = if (firstByte in 6..15) 0 else 1
            val length = (bytesRead - offset).coerceAtLeast(0)
            if (length > 0) {
                output.write(buffer, offset, length)
            }
        }

        return output.toByteArray()
    }

    private fun isSuccessfulResponse(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        val firstByte = response.first().toInt()
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
        const val CONNECT_TIMEOUT_MS = 3000
        const val SOCKET_TIMEOUT_MS = 10000
        const val NUL = 0
        const val ENQ = 5
        const val ACK = 6
    }
}
