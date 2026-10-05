package com.amaxonia.erp.data.printer

import android.content.Context
import android.util.Log
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.domain.model.printer.ReceiptPrintResult
import com.amaxonia.erp.domain.model.printer.TheFactorySettings
import com.amaxonia.erp.domain.repository.PrinterRepository
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TheFactoryPrinterImpl(
    context: Context,
    private val localStore: LocalStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val commandBuilder: TheFactoryFiscalCommandBuilder = TheFactoryFiscalCommandBuilder(),
) : PrinterRepository {
    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    override suspend fun printReceipt(payload: FacturaPrintPayloadDto): Result<ReceiptPrintResult> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                cancelOpenFiscalDocument(settings)

                val commands = commandBuilder.buildFiscalCommandsFromPayload(
                    payload = payload,
                    brandReceiptName = "AMAXONIA ERP",
                )
                commands.forEachIndexed { index, command ->
                    sendTcpCommand(
                        ipAddress = settings.ipAddress,
                        port = settings.port.toInt(),
                        command = command,
                    )
                }

                val printerState = readPrinterState(settings)
                val fiscalNumber =
                    commandBuilder
                        .padFiscalNumber(printerState.lastInvoiceNumber)
                        .orEmpty()

                ReceiptPrintResult(
                    fiscalNumber = fiscalNumber,
                    printerSerial = printerState.registeredMachineNumber,
                )
            }.onFailure { error ->
                Log.e(TAG, "Fiscal receipt printing failed: ${error.message}", error)
            }
        }

    override suspend fun printReportX(): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                sendTcpCommand(
                    ipAddress = settings.ipAddress,
                    port = settings.port.toInt(),
                    command = "I0X",
                    socketTimeoutMs = REPORT_SOCKET_TIMEOUT_MS,
                )
                delay(REPORT_DNF_DELAY_MS)
                Unit
            }.onFailure { error ->
                Log.e(TAG, "Fiscal X report failed: ${error.message}", error)
            }
        }

    override suspend fun printReportZ(): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                validateSettings(settings)

                sendTcpCommand(
                    ipAddress = settings.ipAddress,
                    port = settings.port.toInt(),
                    command = "I0Z",
                    socketTimeoutMs = REPORT_Z_SOCKET_TIMEOUT_MS,
                )
                delay(REPORT_Z_TRANSMISSION_DELAY_MS)
                delay(REPORT_DNF_DELAY_MS)
                Unit
            }.onFailure { error ->
                Log.e(TAG, "Fiscal Z report failed: ${error.message}", error)
            }
        }

    private fun cancelOpenFiscalDocument(settings: TheFactorySettings) {
        runCatching {
            sendTcpCommand(
                ipAddress = settings.ipAddress,
                port = settings.port.toInt(),
                command = CANCEL_DOCUMENT_COMMAND,
            )
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
            error(response.message ?: "No se pudo encriptar el comando para impresión")
        }
        return response.bytes ?: error("Respuesta de cifrado inválida")
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
            val outputStream = socket.getOutputStream()
            outputStream.write(encrypted)
            outputStream.flush()

            val response = readSocketResponse(socket)
            if (!isSuccessfulResponse(response)) {
                val firstByte = response.firstOrNull()?.toInt()?.and(BYTE_MASK)
                error(
                    if (firstByte == NAK) {
                        "The Factory rechazó el comando fiscal '${command.take(COMMAND_LOG_PREVIEW_LENGTH)}' (NAK 0x15)"
                    } else {
                        "The Factory rechazó el comando fiscal '${command.take(COMMAND_LOG_PREVIEW_LENGTH)}'"
                    },
                )
            }
        }
    }

    private fun readSocketResponse(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(SOCKET_BUFFER_SIZE)
        val output = ByteArrayOutputStream()

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == EOF) break
                val first = buffer[0].toInt() and BYTE_MASK
                val keepAsControl = bytesRead == 1 && (first == ACK || first == NAK || first == ENQ || first == NUL)
                val offset = if (keepAsControl || first in CONTROL_BYTE_RANGE) 0 else 1
                if (bytesRead > offset) {
                    output.write(buffer, offset, bytesRead - offset)
                }
            }
        } catch (_: IOException) {
            // Timeout — treated as end of transmission
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
            error("El puerto configurado para The Factory HKA no es válido")
        }
    }

    private companion object {
        const val TAG = "TheFactoryPrinterImpl"
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
        const val CANCEL_DOCUMENT_COMMAND = "7"
        val CONTROL_BYTE_RANGE = 6..15
    }
}
