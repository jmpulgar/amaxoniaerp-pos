package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.R
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readTheFactorySettings
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult
import com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Adaptador TCP de la impresora fiscal The Factory HKA (TASK-074): sólo
 * transporte (conexión, cifrado, lectura de respuesta) y orquestación de
 * impresión. La construcción de comandos del protocolo vive en
 * [TheFactoryFiscalCommandBuilder] (pura, congelada por characterization
 * tests); este adaptador se queda aquí porque depende de red/criptografía.
 */
class TheFactoryPrinterImpl(
    context: Context,
    private val localStore: LocalStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val commandBuilder: TheFactoryFiscalCommandBuilder = TheFactoryFiscalCommandBuilder(),
) : PrinterRepository {
    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    override suspend fun printReceipt(transaction: Transaction): Result<ReceiptPrintResult> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                SafeLog.d(TAG, "Preparing fiscal printer for receipt")
                cancelOpenFiscalDocument(settings)

                val commands =
                    commandBuilder.buildFiscalCommands(
                        transaction = transaction,
                        brandReceiptName = appContext.getString(R.string.brand_receipt_name),
                    )
                SafeLog.d(TAG, "Sending ${commands.size} fiscal receipt operations")
                commands.forEachIndexed { index, command ->
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = command,
                    )
                    SafeLog.d(TAG, "Fiscal receipt operation ${index + 1}/${commands.size} completed")
                }
                SafeLog.d(TAG, "Fiscal receipt operations completed")

                val printerState = readPrinterState(settings)
                val fiscalNumber =
                    commandBuilder
                        .padFiscalNumber(printerState.lastInvoiceNumber)
                        .orEmpty()

                if (fiscalNumber.isBlank()) {
                    SafeLog.w(TAG, "Fiscal printer did not return a receipt number")
                } else {
                    SafeLog.i(TAG, "Fiscal printer returned a receipt number")
                }

                ReceiptPrintResult(
                    fiscalNumber = fiscalNumber,
                    printerSerial = printerState.registeredMachineNumber,
                )
            }.onFailure { error ->
                SafeLog.e(TAG, "Fiscal receipt printing failed", error)
            }
        }

    override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)
                cancelOpenFiscalDocument(settings)

                val localSerial =
                    commandBuilder.sanitizeText(settings.printerSerial, maxLength = PRINTER_SERIAL_MAX_LENGTH)
                val documentSerial =
                    commandBuilder.sanitizeText(document.printerSerial, maxLength = PRINTER_SERIAL_MAX_LENGTH)
                val (resolvedPrinterSerial, serialSource) =
                    when {
                        localSerial.isNotBlank() -> localSerial to "local_settings"
                        documentSerial.isNotBlank() -> documentSerial to "backend_document"
                        else -> "" to "none"
                    }
                if (resolvedPrinterSerial.isBlank()) {
                    error(
                        "No se pudo obtener el serial fiscal de la impresora. " +
                            "Configura el serial en ajustes HKA o verifica respuesta S1.",
                    )
                }
                SafeLog.i(TAG, "Fiscal printer serial resolved from $serialSource")

                val commands =
                    commandBuilder.buildCreditNoteCommands(
                        document = document,
                        printerSerial = resolvedPrinterSerial,
                    )

                SafeLog.d(TAG, "Sending ${commands.size} fiscal credit-note operations")
                commands.forEachIndexed { index, command ->
                    if (command == CLOSE_DOCUMENT_COMMAND_199) {
                        sendCloseDocumentWithFallback(settings, command)
                    } else {
                        sendTcpCommand(
                            ipAddress = settings.ipAddress,
                            port = settings.port.toInt(),
                            command = command,
                        )
                    }
                    SafeLog.d(TAG, "Fiscal credit-note operation ${index + 1}/${commands.size} completed")
                }

                val printerStateAfter = readPrinterState(settings)
                val fiscalNumber =
                    commandBuilder.padFiscalNumber(printerStateAfter.lastCreditNoteNumber)
                        ?: error("No se pudo determinar el número fiscal de la nota de crédito")

                CreditNotePrintResult(
                    fiscalNumber = fiscalNumber,
                    printerSerial = printerStateAfter.registeredMachineNumber,
                )
            }.onFailure { error ->
                SafeLog.e(TAG, "Fiscal credit-note printing failed", error)
            }
        }

    override suspend fun printReportX(): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                SafeLog.d(TAG, "Starting fiscal X report")
                sendTcpCommand(
                    ipAddress = settings.ipAddress,
                    port = settings.port.toInt(),
                    command = "I0X",
                    socketTimeoutMs = REPORT_SOCKET_TIMEOUT_MS,
                )
                SafeLog.d(TAG, "Fiscal X report accepted by printer")
                delay(REPORT_DNF_DELAY_MS)
                SafeLog.d(TAG, "Fiscal X report completed")
                Unit
            }.onFailure { error ->
                SafeLog.e(TAG, "Fiscal X report failed", error)
            }
        }

    override suspend fun printReportZ(): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                SafeLog.d(TAG, "Starting fiscal Z report")
                sendTcpCommand(
                    ipAddress = settings.ipAddress,
                    port = settings.port.toInt(),
                    command = "I0Z",
                    socketTimeoutMs = REPORT_Z_SOCKET_TIMEOUT_MS,
                )
                SafeLog.d(TAG, "Fiscal Z report accepted by printer")
                delay(REPORT_Z_TRANSMISSION_DELAY_MS)
                SafeLog.d(TAG, "Fiscal Z report is finalizing")
                delay(REPORT_DNF_DELAY_MS)
                SafeLog.d(TAG, "Fiscal Z report completed")
                Unit
            }.onFailure { error ->
                SafeLog.e(TAG, "Fiscal Z report failed", error)
            }
        }

    /** Cancela un documento fiscal abierto (best-effort antes de imprimir). */
    private fun cancelOpenFiscalDocument(settings: TheFactorySettings) {
        runCatching {
            sendTcpCommand(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = CANCEL_DOCUMENT_COMMAND,
            )
            SafeLog.d(TAG, "Fiscal printer is ready")
        }.onFailure { error ->
            SafeLog.e(TAG, "No open fiscal document needed cancellation", error)
        }
    }

    private fun readPrinterState(settings: TheFactorySettings): PrinterStateSnapshot {
        val response =
            sendTcpCommandForResponse(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = "S1",
            )
        return commandBuilder.parsePrinterState(response.toString(Charsets.UTF_8))
    }

    private fun sendTcpCommandForResponse(
        ipAddress: String,
        port: Int,
        command: String,
    ): ByteArray {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
            val encrypted = encryptCommand(command)
            val outputStream = socket.getOutputStream()
            outputStream.write(encrypted)
            outputStream.flush()
            val response = readSocketResponse(socket)
            if (response.isEmpty()) {
                error("La impresora no respondió al comando $command")
            }
            return response
        }
    }

    private fun encryptCommand(command: String): ByteArray {
        val response = cryptography.encryptString(command)
        if (response.isError) {
            error(response.message ?: "No se pudo encriptar el comando para impresion")
        }
        return response.bytes ?: error("Respuesta de cifrado invalida")
    }

    private fun sendTcpCommand(
        ipAddress: String,
        port: Int,
        command: String,
        socketTimeoutMs: Int = SOCKET_TIMEOUT_MS,
    ) {
        Socket().use { socket ->
            socket.soTimeout = socketTimeoutMs
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
            val encrypted = encryptCommand(command)
            SafeLog.d(TAG, "Encrypted fiscal operation is being sent")
            val outputStream = socket.getOutputStream()
            outputStream.write(encrypted)
            outputStream.flush()

            val response = readSocketResponse(socket)
            SafeLog.d(TAG, "Fiscal printer response received")
            if (!isSuccessfulResponse(response)) {
                val firstByte = response.firstOrNull()?.toInt()?.and(BYTE_MASK)
                error(
                    if (firstByte == NAK) {
                        "The Factory rechazo el comando fiscal '${command.take(COMMAND_LOG_PREVIEW_LENGTH)}' (NAK 0x15)"
                    } else {
                        "The Factory rechazo el comando fiscal '${command.take(COMMAND_LOG_PREVIEW_LENGTH)}'"
                    },
                )
            }
        }
    }

    private fun sendCloseDocumentWithFallback(
        settings: TheFactorySettings,
        command: String,
    ) {
        runCatching {
            sendTcpCommand(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = command,
            )
        }.getOrElse { closeError ->
            SafeLog.e(TAG, "Fiscal document close was rejected; using compatible fallback", closeError)
            sendTcpCommand(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = CLOSE_DOCUMENT_COMMAND_101,
            )
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
        val buffer = ByteArray(SOCKET_BUFFER_SIZE)
        val output = ByteArrayOutputStream()

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == EOF) break
                // Preserve one-byte control responses (ACK/NAK/ENQ/NUL) for proper diagnostics.
                val first = buffer[0].toInt() and BYTE_MASK
                val keepAsControl = bytesRead == 1 && (first == ACK || first == NAK || first == ENQ || first == NUL)
                val offset = if (keepAsControl || first in CONTROL_BYTE_RANGE) 0 else 1
                if (bytesRead > offset) {
                    output.write(buffer, offset, bytesRead - offset)
                }
            }
        } catch (_: IOException) {
            // Timeout from soTimeout — treat whatever we have as the full response
        }

        return output.toByteArray()
    }

    private fun isSuccessfulResponse(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        val firstByte = response.first().toInt() and BYTE_MASK
        return firstByte == ACK || firstByte == ENQ || firstByte == NUL || response.size > MIN_PAYLOAD_RESPONSE_SIZE
    }

    private fun validateSettings(settings: TheFactorySettings) {
        if (!settings.isConfigured()) {
            error("Configura la IP y el puerto de The Factory HKA antes de imprimir")
        }
        if (settings.port.toIntOrNull() == null) {
            error("El puerto configurado para The Factory HKA no es valido")
        }
    }

    private companion object {
        const val TAG = "HkaPrinter"
        const val CONNECT_TIMEOUT_MS = 3000
        const val SOCKET_TIMEOUT_MS = 10000
        const val REPORT_SOCKET_TIMEOUT_MS = 30000
        const val REPORT_Z_SOCKET_TIMEOUT_MS = 45000
        const val REPORT_Z_TRANSMISSION_DELAY_MS = 20_000L
        const val REPORT_DNF_DELAY_MS = 3_000L
        const val NUL = 0
        const val ENQ = 5
        const val ACK = 6
        const val NAK = 21
        const val EOF = -1
        const val BYTE_MASK = 0xFF
        const val SOCKET_BUFFER_SIZE = 1024
        const val COMMAND_LOG_PREVIEW_LENGTH = 12
        const val MIN_PAYLOAD_RESPONSE_SIZE = 10
        const val PRINTER_SERIAL_MAX_LENGTH = 10
        const val CANCEL_DOCUMENT_COMMAND = "7"
        const val CLOSE_DOCUMENT_COMMAND_199 = "199"
        const val CLOSE_DOCUMENT_COMMAND_101 = "101"
        val CONTROL_BYTE_RANGE = 6..15
    }
}
