package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
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

    suspend fun charge(amount: Double, commandPrefix: String): RapidPayResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                val port = validateSettings(settings)

                val command = buildSaleCommand(commandPrefix, amount)
                val rawResponse = sendJsonCommand(
                    ipAddress = settings.ipAddress,
                    port = port,
                    command = command
                )

                parseResponse(rawResponse)
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
     * Sends the gateway command as a JSON payload over TCP, matching the
     * protocol used by the HK20 POS app (Node.js reference implementation).
     *
     * The POS app expects: {"cmd":"KRV00000000000348"}\n
     * — plain JSON, NOT encrypted. Encryption (hkacryptolib) is only used
     *   for fiscal printer commands, not for Rapid Pay gateway commands.
     */
    private fun sendJsonCommand(ipAddress: String, port: Int, command: String): String {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)

            val payload = JSONObject().put("cmd", command).toString() + "\n"
            val output = socket.getOutputStream()
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.flush()

            return readSocketResponse(socket)
        }
    }

    /**
     * Reads the response from the HKA POS app.
     *
     * The device does NOT close the connection after responding, so we cannot
     * loop until EOF (-1). Instead we use a short read-timeout: read available
     * data and return once the device stops sending (SocketTimeoutException).
     *
     * This mirrors the Node.js HK20 client which listens for "data" events
     * and resolves on "end" or timeout.
     */
    private fun readSocketResponse(socket: Socket): String {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(4096)
        val output = ByteArrayOutputStream()

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

        return output.toString(Charsets.UTF_8.name()).trim()
    }

    private fun parseResponse(rawResponse: String): RapidPayResult {
        if (rawResponse.isBlank()) {
            return RapidPayResult(
                approved = false,
                message = "El dispositivo HKA no envio una respuesta"
            )
        }

        val parsed = runCatching { JSONObject(rawResponse) }.getOrNull()

        // If response is a JSON array, take the first element
        if (parsed == null) {
            val arrayParsed = runCatching {
                val arr = org.json.JSONArray(rawResponse)
                if (arr.length() > 0) arr.getJSONObject(0) else null
            }.getOrNull()

            if (arrayParsed != null) {
                return parseJsonObject(arrayParsed, rawResponse)
            }

            return RapidPayResult(
                approved = false,
                message = "La respuesta del dispositivo no es JSON valido: $rawResponse"
            )
        }

        return parseJsonObject(parsed, rawResponse)
    }

    private fun parseJsonObject(parsed: JSONObject, rawResponse: String): RapidPayResult {
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
            rawResponse = rawResponse
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
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null
)
