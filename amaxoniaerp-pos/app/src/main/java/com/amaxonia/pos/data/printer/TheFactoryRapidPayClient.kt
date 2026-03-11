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
                val responseBytes = sendEncryptedJsonCommand(
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
     * Wraps the command in a JSON envelope and encrypts the FULL JSON string
     * before sending over TCP.
     *
     * This matches the HKA SDK protocol exactly:
     *   1. Command.java wraps: "KRV..." → '{"cmd":"KRV..."}'
     *   2. TCPClient.setOutPutSteamByDevice encrypts the JSON string
     *   3. Encrypted bytes are written to the socket
     *   4. Response is read with getResponseJson() (raw, no byte stripping)
     *
     * The command string (e.g. "KRV00000000000348") is NOT encrypted directly.
     * The JSON envelope '{"cmd":"KRV00000000000348"}' is what gets encrypted.
     */
    private fun sendEncryptedJsonCommand(ipAddress: String, port: Int, command: String): ByteArray {
        // Step 1: Wrap in JSON — matches Command.java toString()
        val jsonPayload = JSONObject().put("cmd", command).toString()

        // Step 2: Encrypt the full JSON string — matches setOutPutSteamByDevice()
        val encryptedBytes = encryptCommand(jsonPayload)

        // Step 3: Send and read response — matches sendCommandJson() + getResponseJson()
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)

            val output = socket.getOutputStream()
            output.write(encryptedBytes)
            output.flush()

            return readResponseJson(socket)
        }
    }

    private fun encryptCommand(payload: String): ByteArray {
        val response = cryptography.encryptString(payload)
        if (response.isError) {
            throw IllegalStateException(
                response.message ?: "No se pudo encriptar el comando de pasarela"
            )
        }
        return response.bytes
            ?: throw IllegalStateException("Respuesta de cifrado invalida")
    }

    /**
     * Reads the JSON response from the HKA POS app.
     *
     * Mirrors ResponseSocket.getResponseJson(): reads ALL bytes raw
     * (no byte stripping) until EOF or timeout.
     *
     * The device may close the connection after sending (EOF = -1), or it may
     * keep it open. We use a short read-timeout to handle both cases.
     */
    private fun readResponseJson(socket: Socket): ByteArray {
        val inputStream = socket.getInputStream()
        val buffer = ByteArray(1024)
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

        return output.toByteArray()
    }

    /**
     * Interprets the raw byte response from The Factory HKA.
     *
     * The response may be:
     * - A JSON payload (possibly preceded by protocol bytes) when the gateway
     *   transaction completes — parse for code/message.
     * - A single protocol byte: ACK (6) = success, NAK (21) = rejected.
     */
    private fun parseResponse(responseBytes: ByteArray): RapidPayResult {
        if (responseBytes.isEmpty()) {
            return RapidPayResult(
                approved = false,
                message = "El dispositivo HKA no envio una respuesta"
            )
        }

        // Try to extract JSON from the response (may follow leading protocol bytes)
        val rawJson = extractJsonString(responseBytes)

        if (rawJson != null) {
            return parseJsonResponse(rawJson)
        }

        // No JSON found — interpret the protocol byte
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
     * Tries to find and extract a JSON object or array from the raw byte response.
     * The JSON may start after one or more leading protocol bytes.
     */
    private fun extractJsonString(bytes: ByteArray): String? {
        val raw = String(bytes, Charsets.UTF_8)
        // Look for JSON object
        val objStart = raw.indexOf('{')
        if (objStart != -1) {
            val objEnd = raw.lastIndexOf('}')
            if (objEnd > objStart) return raw.substring(objStart, objEnd + 1)
        }
        // Look for JSON array
        val arrStart = raw.indexOf('[')
        if (arrStart != -1) {
            val arrEnd = raw.lastIndexOf(']')
            if (arrEnd > arrStart) return raw.substring(arrStart, arrEnd + 1)
        }
        return null
    }

    private fun parseJsonResponse(rawJson: String): RapidPayResult {
        // Try as JSON object first
        val parsed = runCatching { JSONObject(rawJson) }.getOrNull()
        if (parsed != null) {
            return parseJsonObject(parsed, rawJson)
        }

        // Try as JSON array — take the first element
        val arrayParsed = runCatching {
            val arr = org.json.JSONArray(rawJson)
            if (arr.length() > 0) arr.getJSONObject(0) else null
        }.getOrNull()

        if (arrayParsed != null) {
            return parseJsonObject(arrayParsed, rawJson)
        }

        return RapidPayResult(
            approved = false,
            message = "La respuesta del dispositivo no es JSON valido: $rawJson"
        )
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
        private const val ACK = 6
        private const val NAK = 21
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null
)
