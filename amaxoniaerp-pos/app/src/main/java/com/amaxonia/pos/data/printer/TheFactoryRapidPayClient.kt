package com.amaxonia.pos.data.printer

import android.content.Context
import android.util.Log
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
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
                Log.d(TAG, "charge() → comando: $command | IP: ${settings.ipAddress} | puerto: $port")

                val responseBytes = sendEncryptedJsonCommand(
                    ipAddress = settings.ipAddress,
                    port = port,
                    command = command
                )

                Log.d(TAG, "charge() → respuesta bytes: ${responseBytes.size} | raw hex: ${responseBytes.take(64).joinToString(" ") { "%02X".format(it) }}")

                parseResponse(responseBytes)
            }.getOrElse { error ->
                Log.e(TAG, "charge() → excepcion: ${error.message}", error)
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
        Log.d(TAG, "sendEncryptedJsonCommand() → JSON a cifrar: $jsonPayload")

        // Step 2: Encrypt the full JSON string — matches setOutPutSteamByDevice()
        val encryptedBytes = encryptCommand(jsonPayload)
        Log.d(TAG, "sendEncryptedJsonCommand() → cifrado OK, ${encryptedBytes.size} bytes")

        // Step 3: Send and read response — matches sendCommandJson() + getResponseJson()
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
            Log.d(TAG, "sendEncryptedJsonCommand() → conectado a $ipAddress:$port")

            val output = socket.getOutputStream()
            output.write(encryptedBytes)
            output.flush()
            Log.d(TAG, "sendEncryptedJsonCommand() → bytes enviados, esperando respuesta...")

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
                if (bytesRead == -1) {
                    Log.d(TAG, "readResponseJson() → EOF recibido")
                    break
                }
                Log.d(TAG, "readResponseJson() → chunk: $bytesRead bytes")
                output.write(buffer, 0, bytesRead)
            }
        } catch (_: java.net.SocketTimeoutException) {
            Log.d(TAG, "readResponseJson() → timeout de lectura (esperado, fin de respuesta)")
        } finally {
            socket.soTimeout = originalTimeout
        }

        return output.toByteArray()
    }

    /**
     * Interprets the raw byte response from The Factory HKA.
     *
     * Uses ISO-8859-1 encoding to match the SDK's ResponseSocket.getResponseJson()
     * which decodes with StandardCharsets.ISO_8859_1. This preserves accented
     * characters (e.g. "Operación inválida") that would be corrupted with UTF-8.
     */
    private fun parseResponse(responseBytes: ByteArray): RapidPayResult {
        if (responseBytes.isEmpty()) {
            Log.w(TAG, "parseResponse() → respuesta vacia")
            return RapidPayResult(
                approved = false,
                message = "El dispositivo HKA no envio una respuesta"
            )
        }

        // Decode with ISO-8859-1 to match the SDK (StandardCharsets.ISO_8859_1)
        val rawString = String(responseBytes, RESPONSE_CHARSET)
        Log.d(TAG, "parseResponse() → raw string (ISO-8859-1): $rawString")

        // Try to extract JSON from the response (may follow leading protocol bytes)
        val rawJson = extractJsonString(rawString)

        if (rawJson != null) {
            Log.d(TAG, "parseResponse() → JSON extraido: $rawJson")
            return parseJsonResponse(rawJson)
        }

        // No JSON found — interpret the protocol byte
        val firstByte = responseBytes.first().toInt()
        Log.d(TAG, "parseResponse() → sin JSON, primer byte: $firstByte (0x${"%02X".format(responseBytes.first())})")
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
     * Tries to find and extract a JSON object or array from the raw string response.
     * The JSON may start after one or more leading protocol bytes.
     */
    private fun extractJsonString(raw: String): String? {
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

        Log.w(TAG, "parseJsonResponse() → JSON invalido: $rawJson")
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

        Log.d(TAG, "parseJsonObject() → code: $code | message: $message")

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
        private const val TAG = "RapidPay"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 30000
        /** Short timeout to detect end-of-response (device stops sending). */
        private const val READ_CHUNK_TIMEOUT_MS = 2000
        private const val APPROVED_CODE = 200
        private const val ACK = 6
        private const val NAK = 21
        /** Matches SDK's ResponseSocket.getResponseJson() which uses ISO_8859_1. */
        private val RESPONSE_CHARSET: Charset = Charsets.ISO_8859_1
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null
)
