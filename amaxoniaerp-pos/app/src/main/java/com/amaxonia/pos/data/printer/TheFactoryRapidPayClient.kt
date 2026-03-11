package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToLong

class TheFactoryRapidPayClient(
    context: Context,
    private val localStore: LocalStore
) {

    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    suspend fun charge(amount: Double, commandPrefix: String): RapidPayResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                val port = validateSettings(settings)

                val command = buildSaleCommand(commandPrefix, amount)
                val responseBytes = sendEncryptedCommand(
                    ipAddress = settings.ipAddress,
                    port = port,
                    command = command
                )

                parseResponse(responseBytes)
            }.getOrElse { error ->
                RapidPayResult(
                    approved = false,
                    message = error.message ?: "No se pudo completar el cobro en The Factory"
                )
            }
        }
    }

    private fun buildSaleCommand(commandPrefix: String, amount: Double): String {
        val prefix = commandPrefix.trim().ifBlank {
            throw IllegalStateException("No hay comando de pasarela configurado para esta forma de pago")
        }
        val total = (amount.coerceAtLeast(0.01) * 100)
            .roundToLong()
            .toString()
            .padStart(14, '0')
        return prefix + total
    }

    /**
     * Encrypts the command using hkacryptolib and sends the raw bytes over TCP,
     * matching the protocol used by the HKA SDK (GatewayController + TCPClient).
     */
    private fun sendEncryptedCommand(ipAddress: String, port: Int, command: String): ByteArray {
        val encryptedBytes = encryptCommand(command)

        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)

            val output = socket.getOutputStream()
            output.write(encryptedBytes)
            output.flush()

            return readSocketResponse(socket)
        }
    }

    private fun encryptCommand(command: String): ByteArray {
        val response = cryptography.encryptString(command)
        if (response.isError) {
            throw IllegalStateException(
                response.message ?: "No se pudo encriptar el comando de pasarela"
            )
        }
        return response.bytes
            ?: throw IllegalStateException("Respuesta de cifrado invalida")
    }

    /**
     * Reads the response from the HKA device.
     *
     * The device does NOT close the connection after responding, so we cannot
     * loop until EOF (-1). Instead we rely on soTimeout: read available data
     * and return once the device stops sending (SocketTimeoutException).
     * This matches the behaviour of the SDK's ResponseSocket.getResponse().
     */
    private fun readSocketResponse(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(4096)
        val output = ByteArrayOutputStream()

        // Use a short read-timeout so we return as soon as the device
        // finishes sending instead of hanging until the full soTimeout.
        val originalTimeout = socket.soTimeout
        socket.soTimeout = READ_CHUNK_TIMEOUT_MS

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
            }
        } catch (_: java.net.SocketTimeoutException) {
            // Expected: device stopped sending — we have the full response
        } finally {
            socket.soTimeout = originalTimeout
        }

        return output.toByteArray()
    }

    /**
     * Interprets the raw byte response from The Factory HKA.
     *
     * The device responds with a JSON payload when the gateway transaction completes.
     * If the response is large enough to contain JSON, we attempt to parse it.
     * Otherwise we fall back to checking the first-byte ACK/NAK protocol codes.
     */
    private fun parseResponse(responseBytes: ByteArray): RapidPayResult {
        if (responseBytes.isEmpty()) {
            return RapidPayResult(
                approved = false,
                message = "El dispositivo HKA no envio una respuesta"
            )
        }

        // The response may have a leading protocol byte (ACK=6, NAK=21, etc.)
        // followed by a JSON payload. Try to extract JSON from the response.
        val rawString = extractJsonString(responseBytes)

        if (rawString != null) {
            return parseJsonResponse(rawString)
        }

        // No JSON found — fall back to protocol byte interpretation
        val firstByte = responseBytes.first().toInt()
        return when (firstByte) {
            ACK -> RapidPayResult(approved = true, message = "Transaccion aprobada")
            NAK -> RapidPayResult(approved = false, message = "Transaccion rechazada por el dispositivo")
            else -> RapidPayResult(
                approved = false,
                message = "Respuesta no reconocida del dispositivo (codigo: $firstByte)"
            )
        }
    }

    /**
     * Tries to find and extract a JSON object from the raw byte response.
     * The JSON may start after one or more leading protocol bytes.
     */
    private fun extractJsonString(bytes: ByteArray): String? {
        val raw = String(bytes, Charsets.UTF_8)
        val jsonStart = raw.indexOf('{')
        if (jsonStart == -1) return null
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonEnd == -1 || jsonEnd <= jsonStart) return null
        return raw.substring(jsonStart, jsonEnd + 1)
    }

    private fun parseJsonResponse(rawJson: String): RapidPayResult {
        val parsed = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return RapidPayResult(
                approved = false,
                message = "La respuesta del dispositivo no es JSON valido"
            )

        val code = parsed.optInt("code", Int.MIN_VALUE)
        val message = parsed.optString("message")
            .ifBlank { parsed.optString("msg") }
            .ifBlank { parsed.optString("responseMessage") }
            .ifBlank { "Sin mensaje del dispositivo" }

        return RapidPayResult(
            approved = code == APPROVED_CODE,
            message = if (code == APPROVED_CODE) {
                message
            } else {
                "$message. Codigo: ${if (code == Int.MIN_VALUE) "N/A" else code}"
            },
            rawResponse = rawJson
        )
    }

    private fun validateSettings(settings: TheFactorySettings): Int {
        if (settings.ipAddress.isBlank()) {
            throw IllegalStateException("Configura la IP de The Factory HKA antes de cobrar")
        }

        return settings.port
            .toIntOrNull()
            ?: throw IllegalStateException("Configura un puerto valido de The Factory HKA antes de cobrar")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 30000
        /** Short timeout to detect end-of-response (device stops sending). */
        private const val READ_CHUNK_TIMEOUT_MS = 2000
        private const val APPROVED_CODE = 200
        private const val ACK = 6
        private const val NAK = 21
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null
)
