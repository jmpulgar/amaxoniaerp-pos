package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.core.logging.SafeLog
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Utility for testing connectivity and querying status of The Factory HKA fiscal printer.
 *
 * Matches the SDK patterns:
 * - testConnection  → TCPClientTest.testConnection() — plain socket connect, no encryption
 * - checkPrinterStatus → MainController.checkStatus() — sends "05" encrypted, parses PrinterStatus
 */
class HkaConnectionHelper(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cryptography by lazy { MainFactory().createInstance(appContext) }

    /**
     * Tests raw TCP connectivity to the HKA device.
     * Matches SDK's TCPClientTest.testConnection() — simple socket connect + latency.
     */
    suspend fun testConnection(
        ip: String,
        port: Int,
    ): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    val latency = System.currentTimeMillis() - startTime
                    SafeLog.d(TAG, "Fiscal printer connection succeeded")
                    ConnectionTestResult(success = true, latencyMs = latency)
                }
            }.getOrElse { e ->
                SafeLog.e(TAG, "Fiscal printer connection failed", e)
                ConnectionTestResult(
                    success = false,
                    errorMessage =
                        when (e) {
                            is java.net.SocketTimeoutException -> "Timeout: No se pudo conectar a $ip:$port"
                            else -> e.message ?: "Error de conexión desconocido"
                        },
                )
            }
        }

    /**
     * Checks the printer status by sending command "05" encrypted.
     *
     * Matches SDK's MainController.checkStatus():
     *   1. getValueByStringWithCallback("05", callback)
     *   2. setOutPutSteamByDevice(socket, "05") — encrypts the string
     *   3. getResponse() — reads response with byte-stripping
     *   4. PrinterStatus(bytes[0], bytes[1]) — parses status/error codes
     */
    suspend fun checkPrinterStatus(
        ip: String,
        port: Int,
    ): PrinterStatusResult =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

                    // Encrypt and send "05" — matches SDK's checkStatus()
                    val encrypted = cryptography.encryptString("05")
                    if (encrypted.isError) {
                        error(encrypted.message ?: "Error de cifrado")
                    }
                    val bytes =
                        encrypted.bytes
                            ?: error("Cifrado devolvió bytes nulos")

                    socket.getOutputStream().apply {
                        write(bytes)
                        flush()
                    }
                    SafeLog.d(TAG, "Encrypted fiscal status request sent")

                    // Read response with byte-stripping (matches SDK getResponse())
                    val response = readResponseWithStripping(socket)
                    SafeLog.d(TAG, "Fiscal status response received")

                    if (response.isEmpty()) {
                        error("La impresora no respondió")
                    }

                    if (response[0].toInt() and 0xFF == NAK) {
                        error("La impresora respondió con NAK (error)")
                    }

                    parseStatus(response)
                }
            }.getOrElse { e ->
                SafeLog.e(TAG, "Fiscal printer status request failed", e)
                PrinterStatusResult(
                    success = false,
                    errorMessage =
                        when (e) {
                            is java.net.SocketTimeoutException -> "Timeout: La impresora no respondió"
                            else -> e.message ?: "Error desconocido al consultar estado"
                        },
                )
            }
        }

    /**
     * Reads response with byte-stripping, matching SDK's ResponseSocket.getResponse().
     *
     * SDK stripping logic:
     *   - If first byte of chunk is between 6 and 15 (inclusive) → include all bytes (off=0)
     *   - Otherwise → skip first byte (off=1)
     */
    private fun readResponseWithStripping(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        val originalTimeout = socket.soTimeout
        socket.soTimeout = READ_CHUNK_TIMEOUT_MS

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val first = buffer[0].toInt() and 0xFF
                val off = if (first in 6..15) 0 else 1
                if (bytesRead > off) {
                    output.write(buffer, off, bytesRead - off)
                }
            }
        } catch (_: java.net.SocketTimeoutException) {
            // Expected — device stopped sending
        } finally {
            socket.soTimeout = originalTimeout
        }

        return output.toByteArray()
    }

    /**
     * Parses printer status from response bytes.
     * Matches SDK's PrinterStatus(int status, int error) constructor.
     */
    private fun parseStatus(bytes: ByteArray): PrinterStatusResult {
        val statusByte = bytes[0].toInt() and 0xFF
        val errorByte = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0x40

        val statusHex = Integer.toHexString(statusByte)
        val errorHex = Integer.toHexString(errorByte)

        val statusDescription =
            when (statusHex) {
                "40" -> "Modo Entrenamiento, en Espera"
                "41" -> "Modo Entrenamiento, en Transacción Fiscal"
                "42" -> "Modo Entrenamiento, en Transacción No Fiscal"
                "60" -> "Modo Fiscal, en Espera"
                "68" -> "Modo Fiscal, MF llena, en Espera"
                "61" -> "Modo Fiscal, en Transacción Fiscal"
                "69" -> "Modo Fiscal, MF llena, en Transacción Fiscal"
                "62" -> "Modo Fiscal, en Transacción No Fiscal"
                "6a" -> "Modo Fiscal, MF llena, en Transacción No Fiscal"
                else -> "Estado desconocido (0x$statusHex)"
            }

        val errorDescription =
            when (errorHex) {
                "40" -> "Ningún error"
                "48" -> "Error gaveta"
                "41" -> "Sin papel"
                "42" -> "Error mecánico / papel"
                "43" -> "Error mecánico y fin de papel"
                "60" -> "Error fiscal"
                "64" -> "Error en memoria fiscal"
                "6c" -> "Memoria fiscal llena"
                else -> "Error desconocido (0x$errorHex)"
            }

        SafeLog.d(TAG, "Fiscal printer status parsed")

        return PrinterStatusResult(
            success = true,
            statusDescription = statusDescription,
            errorDescription = errorDescription,
        )
    }

    companion object {
        private const val TAG = "HkaConnection"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 10000
        private const val READ_CHUNK_TIMEOUT_MS = 2000
        private const val NAK = 21
    }
}

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val errorMessage: String? = null,
)

data class PrinterStatusResult(
    val success: Boolean,
    val statusDescription: String = "",
    val errorDescription: String = "",
    val errorMessage: String? = null,
)
