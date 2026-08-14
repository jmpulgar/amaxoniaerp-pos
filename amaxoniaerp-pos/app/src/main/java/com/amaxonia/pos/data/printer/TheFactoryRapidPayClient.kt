package com.amaxonia.pos.data.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.printer.GatewayOption
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.math.roundToLong

/**
 * Rapid Pay client that communicates with The Factory HKA POS app via Android Intents.
 *
 * The flow is:
 * 1. Build a sale command (e.g. "KRV0000000000000100")
 * 2. Wrap in JSON: {"cmd":"KRV0000000000000100"}
 * 3. Encrypt with hkacryptolib
 * 4. Create an Intent targeting the HKA POS app with the encrypted bytes
 * 5. The calling Activity launches the Intent
 * 6. HKA POS processes the payment and re-launches our Activity with result extras
 *
 * The result is received in MainActivity.onNewIntent() and delivered via [RapidPayBridge].
 */
class TheFactoryRapidPayClient(
    context: Context,
    private val localStore: LocalStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    /**
     * Builds an Intent to launch the HKA POS gateway for a card payment.
     *
     * @param amount The amount to charge in bolivares
     * @param commandPrefix The gateway command prefix (e.g. "KRV")
     * @param customerIdentifier CI/RIF del cliente (solo dÃ­gitos, mÃ¡x. 9)
     * @param commerceRif RIF del comercio desde parametros_generales.rif (mÃ¡x. 11)
     * @return The Intent to launch, or a failure with the error message
     */
    fun buildGatewayIntent(
        amount: Double,
        commandPrefix: String,
        customerIdentifier: String,
        commerceRif: String,
    ): Result<Intent> =
        buildGatewayLaunchPayload(amount, commandPrefix, customerIdentifier, commerceRif).map { payload ->
            Intent().apply {
                component = ComponentName(payload.packageName, payload.activityClassName)
                putExtra(EXTRA_COMMAND, payload.encryptedCommand)
                putExtra(EXTRA_COLOR_BACKGROUND, payload.backgroundColor)
                putExtra(EXTRA_COLOR_TEXT, payload.textColor)
                putExtra(EXTRA_MESSAGE, payload.message)
            }
        }

    fun buildGatewayLaunchPayload(
        amount: Double,
        commandPrefix: String,
        customerIdentifier: String,
        commerceRif: String,
    ): Result<GatewayLaunchPayload> =
        runCatching {
            val command =
                buildSaleCommand(
                    commandPrefix = commandPrefix,
                    amount = amount,
                    customerIdentifier = customerIdentifier,
                    commerceRif = commerceRif,
                )
            val encryptedBytes = encryptCommand(command)
            SafeLog.d(TAG, "Gateway command encrypted")

            val targetPackage = resolveHkaPackage()
            SafeLog.d(TAG, "Gateway application resolved")

            GatewayLaunchPayload(
                packageName = targetPackage,
                activityClassName = TARGET_ACTIVITY,
                encryptedCommand = encryptedBytes,
                backgroundColor = COLOR_PRIMARY,
                textColor = COLOR_WHITE,
                message = "Procesando pago...",
            )
        }

    /**
     * Queries available gateway providers from the HKA device (command: K?).
     * Mirrors SDK flow used in GatewayPay:
     * - sends encrypted {"cmd":"K?"}
     * - expects JSON array response containing message with gateway list
     */
    suspend fun listGateways(): Result<List<GatewayOption>> =
        withContext(ioDispatcher) {
            runCatching {
                val settings = localStore.readTheFactorySettings()
                val ip = settings.ipAddress.trim()
                val port =
                    settings.port.toIntOrNull()
                        ?: error("Puerto HKA invalido para consultar pasarelas")
                require(ip.isNotBlank()) { "IP HKA requerida para consultar pasarelas" }

                val payload = JSONObject().put("cmd", "K?").toString()
                val encrypted = encryptRawPayload(payload)

                val responseText =
                    Socket().use { socket ->
                        socket.soTimeout = SOCKET_TIMEOUT_MS
                        socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                        socket.getOutputStream().apply {
                            write(encrypted)
                            flush()
                        }
                        readRawResponse(socket).toString(StandardCharsets.ISO_8859_1).trim()
                    }

                parseGatewayList(responseText)
            }
        }

    /**
     * Parses the result Intent extras returned by the HKA POS app via onNewIntent().
     *
     * The HKA POS app re-launches our MainActivity with these extras:
     * - "codeRapidPay" â†’ "200" (approved) or "400" (rejected)
     * - "resultRapidPay" â†’ JSON string with transaction details
     * - "messageRapidPay" â†’ error/status message
     */
    fun parseResultIntent(intent: Intent): RapidPayResult {
        val code = intent.getStringExtra(EXTRA_RESULT_CODE)
        val resultJson = intent.getStringExtra(EXTRA_RESULT_DATA)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        SafeLog.d(TAG, "Gateway result received; hasCode=${code != null}")

        if (code == null) {
            SafeLog.w(TAG, "Gateway result did not include a result code")
            return RapidPayResult(
                approved = false,
                message = "No se recibio respuesta de la pasarela de pago",
            )
        }

        val approved = code == APPROVED_CODE
        val displayMessage =
            when {
                approved && !resultJson.isNullOrBlank() -> parseApprovedMessage(resultJson)
                approved -> message ?: "Transaccion aprobada"
                !message.isNullOrBlank() -> message
                else -> "Transaccion rechazada (codigo: $code)"
            }

        return RapidPayResult(
            approved = approved,
            message = displayMessage,
            rawResponse = resultJson,
        )
    }

    private fun parseApprovedMessage(resultJson: String): String =
        try {
            val json = JSONObject(resultJson)
            json
                .optString("message")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("responseMessage") }
                .ifBlank { "Transaccion aprobada" }
        } catch (_: Exception) {
            "Transaccion aprobada"
        }

    private fun parseGatewayList(responseText: String): List<GatewayOption> {
        if (responseText.isBlank()) return emptyList()

        return runCatching {
            val rootArray = JSONArray(responseText)
            if (rootArray.length() == 0) return@runCatching emptyList<GatewayOption>()
            val firstObj = rootArray.optJSONObject(0) ?: return@runCatching emptyList<GatewayOption>()
            val message = firstObj.optString("message")
            if (message.isBlank()) return@runCatching emptyList<GatewayOption>()

            val gatewaysArray = JSONArray(message)
            buildList {
                for (index in 0 until gatewaysArray.length()) {
                    val gateway = gatewaysArray.optJSONObject(index) ?: continue
                    val key = gateway.optString("key").trim()
                    val value = gateway.optString("value").trim()
                    if (key.isNotBlank()) {
                        add(
                            GatewayOption(
                                key = key,
                                label = value.ifBlank { "Pasarela $key" },
                            ),
                        )
                    }
                }
            }
        }.getOrElse {
            SafeLog.w(TAG, "Gateway list response could not be parsed")
            emptyList()
        }
    }

    /**
     * Builds the gateway sale command string.
     *
     * Format: prefix + 16-digit zero-padded amount in cents
     * Example: "KRV" + "0000000000000348" = "KRV0000000000000348" (for $3.48)
     *
     * The 16-digit amount matches the SDK format exactly
     * (see GatewayPay.optionSelected: "KRV0000000000000100").
     */
    private fun buildSaleCommand(
        commandPrefix: String,
        amount: Double,
        customerIdentifier: String,
        commerceRif: String,
    ): String {
        val prefix =
            commandPrefix.trim().ifBlank {
                error("No hay comando de pasarela configurado para esta forma de pago")
            }
        if (amount > MAX_GATEWAY_AMOUNT) {
            error("Monto excede mÃ¡ximo permitido por plataforma financiera (999999999.99)")
        }
        val amountCents =
            (amount.coerceAtLeast(0.01) * 100)
                .roundToLong()
                .toString()
                .padStart(16, '0')

        val customer = customerIdentifier.filter(Char::isDigit).take(9).ifBlank { "0" }
        val commerce =
            commerceRif
                .uppercase()
                .filter { it.isLetterOrDigit() }
                .take(11)
                .ifBlank { error("RIF de comercio invÃ¡lido. Configura parametros_generales.rif") }

        // Manual HKA V1.0.2: K{gateway}V{amount16}|{ci/rif}|{rifComercio}|
        return "$prefix$amountCents|$customer|$commerce|"
    }

    /**
     * Wraps the command in a JSON envelope {"cmd":"..."} and encrypts it.
     * Matches the SDK's GatewayController.generateCommandEncrypt():
     *   1. Command.java wraps: "KRV..." â†’ '{"cmd":"KRV..."}'
     *   2. iCryptography.encryptString() encrypts the JSON string
     */
    private fun encryptCommand(command: String): ByteArray {
        val jsonPayload = JSONObject().put("cmd", command).toString()
        return encryptRawPayload(jsonPayload)
    }

    private fun encryptRawPayload(payload: String): ByteArray {
        val response = cryptography.encryptString(payload)
        if (response.isError) {
            error(
                response.message ?: "No se pudo encriptar el comando de pasarela",
            )
        }
        return response.bytes
            ?: error("Respuesta de cifrado invalida")
    }

    /**
     * Resolves the installed HKA POS package name.
     * Checks release first, then demo variants.
     */
    private fun resolveHkaPackage(): String {
        val pm = appContext.packageManager
        for (pkg in HKA_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                SafeLog.d(TAG, "Compatible gateway application found")
                return pkg
            } catch (_: Exception) {
                // Not installed, try next
            }
        }
        error(
            "La aplicacion The Factory HKA POS no esta instalada en este dispositivo",
        )
    }

    private fun readRawResponse(socket: Socket): ByteArray {
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
            // Expected: device stopped sending
        } finally {
            socket.soTimeout = originalTimeout
        }
        return output.toByteArray()
    }

    companion object {
        private const val TAG = "RapidPay"

        // HKA POS target activity (same across all package variants)
        private const val TARGET_ACTIVITY = "com.thefactory.hkapos.ui.main.HomeActivity"

        // Package names to check (in priority order)
        private val HKA_PACKAGES =
            listOf(
                "com.thefactory.hkapos.fiscal",
                "com.thefactory.hkapos.fiscal.release",
                "com.thefactory.hkapos.fiscal.demo",
                "com.thefactory.hkapos.fiscal.demo.demo",
            )

        // Intent extra keys â€” from SDK Constants.java
        private const val EXTRA_COMMAND = "commandRapidPay"
        private const val EXTRA_RESULT_CODE = "codeRapidPay"
        private const val EXTRA_RESULT_DATA = "resultRapidPay"
        private const val EXTRA_MESSAGE = "messageRapidPay"
        private const val EXTRA_COLOR_BACKGROUND = "colorBackgroundLoading"
        private const val EXTRA_COLOR_TEXT = "colorText"

        // UI customization
        private const val COLOR_PRIMARY = "#6750A4"
        private const val COLOR_WHITE = "#FFFFFFFF"

        // Result codes from HKA POS
        private const val APPROVED_CODE = "200"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val SOCKET_TIMEOUT_MS = 10000
        private const val READ_CHUNK_TIMEOUT_MS = 2000
        private const val MAX_GATEWAY_AMOUNT = 999_999_999.99
    }
}

data class RapidPayResult(
    val approved: Boolean,
    val message: String,
    val rawResponse: String? = null,
)
