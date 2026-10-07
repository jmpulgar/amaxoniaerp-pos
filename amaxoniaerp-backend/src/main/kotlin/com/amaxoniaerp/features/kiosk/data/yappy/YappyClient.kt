package com.amaxoniaerp.features.kiosk.data.yappy

import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCharge
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCredentials
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyDevice
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyGateway
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQr
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.amaxoniaerp.features.kiosk.domain.yappy.YappySessionExpiredException
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyTransactionStatus
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyUpstreamException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.channels.UnresolvedAddressException

/**
 * Adaptador HTTP de [YappyGateway] sobre el `HttpClient` de Ktor.
 *
 * El cuerpo se serializa a mano con [JsonPrimitive] de [BigDecimal] para que los montos viajen
 * como números JSON con exactamente 2 decimales (ADR-004: la conversión ocurre solo aquí).
 * El cliente recibido debe configurar los timeouts (15 s en el composition root).
 *
 * Seguridad: los logs solo incluyen ruta, código HTTP e ids de transacción; nunca headers.
 */
class YappyClient(
    private val httpClient: HttpClient,
) : YappyGateway {
    private val logger = LoggerFactory.getLogger(YappyClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun openSession(
        credentials: YappyCredentials,
        device: YappyDevice,
    ): String {
        val payload =
            buildJsonObject {
                putJsonObject("body") {
                    putJsonObject("device") { put("id", device.deviceId) }
                    put("group_id", device.groupId)
                }
            }
        val response = send(HttpMethod.Post, credentials, SESSION_PATH, SESSION_PATH, sessionToken = null, payload = payload)
        val root = parseSuccess(response, SESSION_PATH, sessionScoped = false)
        return firstString(root, "body.token", "token", "data.token", "session_token", "body.session_token")
            ?: throw YappyUpstreamException("Yappy no devolvió token de sesión")
    }

    override suspend fun generateQr(
        credentials: YappyCredentials,
        sessionToken: String,
        qrType: YappyQrType,
        charge: YappyCharge,
    ): YappyQr {
        val path = "/qr/generate/${qrType.name}"
        val payload =
            buildJsonObject {
                putJsonObject("body") {
                    putJsonObject("charge_amount") {
                        put("sub_total", money(charge.subTotal))
                        put("tax", money(charge.tax))
                        put("tip", money(BigDecimal.ZERO))
                        put("discount", money(BigDecimal.ZERO))
                        put("total", money(charge.total))
                    }
                    put("order_id", charge.orderId)
                    put("description", charge.description)
                }
            }
        val response = send(HttpMethod.Post, credentials, path, path, sessionToken, payload)
        val root = parseSuccess(response, path, sessionScoped = true)
        val transactionId =
            firstString(root, "body.transactionId", "body.id", "data.transactionId")
                ?: throw YappyUpstreamException("Yappy no devolvió transactionId del QR")
        val hash =
            firstString(root, "body.hash", "body.qr", "data.hash")
                ?: throw YappyUpstreamException("Yappy no devolvió el contenido del QR")
        logger.info("[YAPPY] QR generado transactionId={} orderId={}", transactionId, charge.orderId)
        return YappyQr(transactionId = transactionId, hash = hash)
    }

    override suspend fun getTransactionStatus(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    ): YappyTransactionStatus {
        val path = "/transaction/${transactionId.encodeURLPathPart()}"
        val response = send(HttpMethod.Get, credentials, path, TRANSACTION_LOG_PATH, sessionToken, payload = null)
        val root = parseSuccess(response, TRANSACTION_LOG_PATH, sessionScoped = true)
        return YappyTransactionStatus.fromYappy(firstString(root, "body.status", "data.status", "status"))
    }

    override suspend fun cancelTransaction(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    ) {
        val path = "/transaction/${transactionId.encodeURLPathPart()}"
        val response = send(HttpMethod.Put, credentials, path, TRANSACTION_LOG_PATH, sessionToken, payload = null)
        parseSuccess(response, TRANSACTION_LOG_PATH, sessionScoped = true)
        logger.info("[YAPPY] Transacción anulada transactionId={}", transactionId)
    }

    private suspend fun send(
        method: HttpMethod,
        credentials: YappyCredentials,
        path: String,
        logPath: String,
        sessionToken: String?,
        payload: JsonObject?,
    ): HttpResponse {
        val url = credentials.baseUrl.trim().trimEnd('/') + path
        return try {
            httpClient.request(url) {
                this.method = method
                applyHeaders(credentials, sessionToken)
                if (payload != null) {
                    setBody(TextContent(payload.toString(), ContentType.Application.Json))
                }
            }
        } catch (e: IOException) {
            // Incluye timeouts de Ktor (HttpRequestTimeoutException/ConnectTimeoutException).
            logger.warn("[YAPPY] Error de transporte en {} {}: {}", method.value, logPath, e.javaClass.simpleName)
            throw YappyUpstreamException("No se pudo contactar a Yappy", e)
        } catch (e: UnresolvedAddressException) {
            logger.warn("[YAPPY] Host de Yappy no resuelto ({})", e.javaClass.simpleName)
            throw YappyUpstreamException("No se pudo contactar a Yappy", e)
        }
    }

    private fun HttpRequestBuilder.applyHeaders(
        credentials: YappyCredentials,
        sessionToken: String?,
    ) {
        header("api-key", credentials.apiKey)
        header("secret-key", credentials.secretKey)
        if (sessionToken != null) {
            header("authorization", sessionToken)
        }
    }

    private suspend fun parseSuccess(
        response: HttpResponse,
        logPath: String,
        sessionScoped: Boolean,
    ): JsonObject? {
        ensureSuccessStatus(response.status, logPath, sessionScoped)
        val text = response.bodyAsText()
        if (text.isBlank()) return null
        val element =
            try {
                json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                logger.warn("[YAPPY] Respuesta no JSON en {}", logPath)
                throw YappyUpstreamException("Respuesta inválida de Yappy", e)
            }
        return element as? JsonObject ?: run {
            logger.warn("[YAPPY] Respuesta JSON no es un objeto en {}", logPath)
            throw YappyUpstreamException("Respuesta inválida de Yappy")
        }
    }

    private fun ensureSuccessStatus(
        status: HttpStatusCode,
        logPath: String,
        sessionScoped: Boolean,
    ) {
        if (sessionScoped && (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden)) {
            logger.info("[YAPPY] Sesión rechazada ({}) en {}", status.value, logPath)
            throw YappySessionExpiredException("Sesión Yappy expirada (HTTP ${status.value})")
        }
        if (status.value !in HTTP_SUCCESS_RANGE) {
            logger.warn("[YAPPY] Respuesta HTTP {} en {}", status.value, logPath)
            throw YappyUpstreamException("Yappy respondió con error (HTTP ${status.value})")
        }
    }

    private fun firstString(
        root: JsonElement?,
        vararg paths: String,
    ): String? =
        paths.firstNotNullOfOrNull { path ->
            val node =
                path.split('.').fold(root) { current, key ->
                    (current as? JsonObject)?.get(key)
                }
            (node as? JsonPrimitive)
                ?.takeIf { it.isString || it.contentOrNull?.isNotBlank() == true }
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" }
        }

    private fun money(value: BigDecimal): JsonPrimitive = JsonPrimitive(value.setScale(MONEY_SCALE, RoundingMode.HALF_UP))

    private companion object {
        const val MONEY_SCALE = 2
        const val SESSION_PATH = "/session/device"
        const val TRANSACTION_LOG_PATH = "/transaction/{id}"
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
