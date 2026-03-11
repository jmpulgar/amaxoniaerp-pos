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

    private fun sendJsonCommand(ipAddress: String, port: Int, command: String): String {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)

            val payload = JSONObject().put("cmd", command).toString() + "\n"
            val output = socket.getOutputStream()
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.flush()

            val response = readSocketResponse(socket).trim()
            if (response.isBlank()) {
                throw IllegalStateException("El dispositivo HKA no envio una respuesta valida")
            }
            return response
        }
    }

    private fun readSocketResponse(socket: Socket): String {
        val input = socket.getInputStream()
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
        }

        return output.toString(Charsets.UTF_8.name())
    }

    private fun parseResponse(rawResponse: String): RapidPayResult {
        val parsed = runCatching { JSONObject(rawResponse) }.getOrNull()
        if (parsed == null) {
            return RapidPayResult(
                approved = false,
                message = "La respuesta del dispositivo no es JSON valido: $rawResponse"
            )
        }

        val code = parsed.optInt("code", Int.MIN_VALUE)
        val message = parsed.optString("message")
            .ifBlank { parsed.optString("msg") }
            .ifBlank { "Sin mensaje del dispositivo" }

        return RapidPayResult(
            approved = code == APPROVED_CODE,
            message = if (code == APPROVED_CODE) message else "$message. Codigo: ${if (code == Int.MIN_VALUE) "N/A" else code}",
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
        private const val APPROVED_CODE = 200
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null
)
