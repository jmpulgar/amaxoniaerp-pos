package com.amaxonia.erp.data.printer

import android.content.Context
import android.util.Log
import com.amaxonia.erp.domain.model.printer.FiscalConnectionResult
import com.amaxonia.erp.domain.model.printer.FiscalDeviceDiagnostics
import com.amaxonia.erp.domain.model.printer.FiscalStatusResult
import com.amaxonia.erp.domain.model.printer.GatewayOption
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class HkaConnectionHelper(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FiscalDeviceDiagnostics {
    private val appContext = context.applicationContext
    private val cryptography by lazy { MainFactory().createInstance(appContext) }

    override suspend fun gateways(): Result<List<GatewayOption>> =
        withContext(ioDispatcher) {
            runCatching {
                listOf(
                    GatewayOption("HKA20", "The Factory HKA 2.0 (Direct TCP)"),
                )
            }
        }

    override suspend fun testConnection(
        ip: String,
        port: Int,
    ): FiscalConnectionResult =
        withContext(ioDispatcher) {
            runCatching {
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    val latency = System.currentTimeMillis() - startTime
                    FiscalConnectionResult(success = true, latencyMs = latency)
                }
            }.getOrElse { e ->
                Log.e(TAG, "Fiscal printer connection failed: ${e.message}")
                FiscalConnectionResult(
                    success = false,
                    errorMessage =
                        when (e) {
                            is java.net.SocketTimeoutException -> "Timeout: No se pudo conectar a $ip:$port"
                            else -> e.message ?: "Error de conexión desconocido"
                        },
                )
            }
        }

    override suspend fun printerStatus(
        ip: String,
        port: Int,
    ): FiscalStatusResult =
        withContext(ioDispatcher) {
            runCatching {
                Socket().use { socket ->
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

                    val encrypted = cryptography.encryptString("05")
                    if (encrypted.isError) {
                        error(encrypted.message ?: "Error de cifrado")
                    }
                    val bytes = encrypted.bytes ?: error("Cifrado devolvió bytes nulos")

                    socket.getOutputStream().apply {
                        write(bytes)
                        flush()
                    }

                    val response = readResponseWithStripping(socket)
                    if (response.isEmpty()) {
                        error("La impresora no respondió")
                    }

                    if (response[0].toInt() and BYTE_MASK == NAK) {
                        error("La impresora respondió con NAK (error)")
                    }

                    parseStatus(response)
                }
            }.getOrElse { e ->
                Log.e(TAG, "Fiscal printer status request failed: ${e.message}")
                FiscalStatusResult(
                    success = false,
                    errorMessage =
                        when (e) {
                            is java.net.SocketTimeoutException -> "Timeout: La impresora no respondió"
                            else -> e.message ?: "Error desconocido al consultar estado"
                        },
                )
            }
        }

    private fun readResponseWithStripping(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(SOCKET_BUFFER_SIZE)
        val output = ByteArrayOutputStream()

        val originalTimeout = socket.soTimeout
        socket.soTimeout = READ_CHUNK_TIMEOUT_MS

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val first = buffer[0].toInt() and BYTE_MASK
                val off = if (first in 6..15) 0 else 1
                if (bytesRead > off) {
                    output.write(buffer, off, bytesRead - off)
                }
            }
        } catch (_: java.net.SocketTimeoutException) {
            // Expected - device finished stream
        } finally {
            socket.soTimeout = originalTimeout
        }

        return output.toByteArray()
    }

    private fun parseStatus(bytes: ByteArray): FiscalStatusResult {
        val statusByte = bytes[0].toInt() and BYTE_MASK
        val errorByte = if (bytes.size > 1) bytes[1].toInt() and BYTE_MASK else DEFAULT_ERROR_BYTE

        return FiscalStatusResult(
            success = true,
            statusDescription = describePrinterStatus(statusByte),
            errorDescription = describePrinterError(errorByte),
        )
    }

    private fun describePrinterStatus(statusByte: Int): String {
        val hex = Integer.toHexString(statusByte)
        return when (hex) {
            "40" -> "Modo Entrenamiento, en Espera"
            "41" -> "Modo Entrenamiento, en Transacción Fiscal"
            "42" -> "Modo Entrenamiento, en Transacción No Fiscal"
            "60" -> "Modo Fiscal, en Espera"
            "68" -> "Modo Fiscal, MF llena, en Espera"
            "61" -> "Modo Fiscal, en Transacción Fiscal"
            "69" -> "Modo Fiscal, MF llena, en Transacción Fiscal"
            "62" -> "Modo Fiscal, en Transacción No Fiscal"
            "6a" -> "Modo Fiscal, MF llena, en Transacción No Fiscal"
            else -> "Estado desconocido (0x$hex)"
        }
    }

    private fun describePrinterError(errorByte: Int): String {
        val hex = Integer.toHexString(errorByte)
        return when (hex) {
            "40" -> "Ningún error"
            "48" -> "Error gaveta"
            "41" -> "Sin papel"
            "42" -> "Error mecánico / papel"
            "43" -> "Error mecánico y fin de papel"
            "60" -> "Error fiscal"
            "64" -> "Error en memoria fiscal"
            "6c" -> "Memoria fiscal llena"
            else -> "Error desconocido (0x$hex)"
        }
    }

    companion object {
        private const val TAG = "HkaConnectionHelper"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 10000
        private const val READ_CHUNK_TIMEOUT_MS = 2000
        private const val NAK = 21
        private const val BYTE_MASK = 0xFF
        private const val DEFAULT_ERROR_BYTE = 0x40
        private const val SOCKET_BUFFER_SIZE = 1024
    }
}
