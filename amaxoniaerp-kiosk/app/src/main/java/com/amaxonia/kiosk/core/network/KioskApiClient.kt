package com.amaxonia.kiosk.core.network

import com.amaxonia.kiosk.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Session-level outcomes of a kiosk call that the navigation must react to. */
sealed interface KioskSessionEvent {
    /** 401: the company token is no longer valid; the session was cleared → login. */
    data object LoggedOut : KioskSessionEvent

    /** 400 "Caja del kiosco no válida": the caja was cleared → caja setup. */
    data object InvalidCaja : KioskSessionEvent
}

class KioskApiClient(
    private val httpClient: HttpClient,
    private val tokenStorage: KioskTokenStorage,
) {
    private val _sessionEvents = MutableSharedFlow<KioskSessionEvent>(extraBufferCapacity = SESSION_EVENT_BUFFER)

    /** Emitted when a call invalidates the session or the caja (already cleared from storage). */
    val sessionEvents: SharedFlow<KioskSessionEvent> = _sessionEvents.asSharedFlow()

    private val baseUrl: String
        get() = normalizeServerUrl(tokenStorage.serverUrl ?: BuildConfig.DEFAULT_SERVER_URL)

    // --- System login (no session yet: explicit server URL and tokens) ---

    /** `POST auth/login` with the system user, exactly like the POS. */
    suspend fun login(
        serverUrl: String,
        countryCode: String,
        username: String,
        password: String,
    ): Result<KioskLoginResponse> =
        authCall {
            val response =
                httpClient.post("${normalizeServerUrl(serverUrl)}auth/login") {
                    header(KioskHeaders.COUNTRY_CODE, countryCode.trim().uppercase())
                    contentType(ContentType.Application.Json)
                    setBody(KioskLoginRequest(username = username.trim(), password = password))
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                throw KioskInvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)
            }
            checkAuthResponse(response, fallback = "No se pudo iniciar sesión")
            response.body<KioskLoginResponse>()
        }

    /** `POST auth/company` with the identity token; returns the (non-expiring) company token. */
    suspend fun selectCompany(
        serverUrl: String,
        identityToken: String,
        companyId: Int,
    ): Result<KioskSelectCompanyResponse> =
        authCall {
            val response =
                httpClient.post("${normalizeServerUrl(serverUrl)}auth/company") {
                    header(HttpHeaders.Authorization, "Bearer $identityToken")
                    contentType(ContentType.Application.Json)
                    setBody(KioskSelectCompanyRequest(companyId))
                }
            checkAuthResponse(response, fallback = "No se pudo seleccionar la empresa")
            response.body<KioskSelectCompanyResponse>()
        }

    /** `GET api/cajas` of the logged company (POS endpoint: needs `Company-DB` = adminDb). */
    suspend fun getCajas(): Result<List<KioskCajaDto>> =
        authCall {
            val response =
                httpClient.get("${baseUrl}api/cajas") {
                    tokenStorage.authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    tokenStorage.companyDb?.let { header(KioskHeaders.COMPANY_DB, it) }
                }
            checkResponse(response)
            response.body<List<KioskCajaDto>>()
        }

    // --- Kiosk endpoints (Authorization + X-Kiosk-Caja + X-Kiosk-Prefix added by the HTTP client) ---

    suspend fun getConfig(ifNoneMatch: String? = null): NetworkResult<KioskConfigResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/config"
            val response =
                httpClient.get(endpoint) {
                    if (!ifNoneMatch.isNullOrBlank()) {
                        header(HttpHeaders.IfNoneMatch, ifNoneMatch)
                    }
                }

            if (response.status == HttpStatusCode.NotModified) {
                NetworkResult.NotModified
            } else {
                checkResponse(response)
                val etag = response.headers[HttpHeaders.ETag]
                val data = response.body<KioskConfigResponse>().withAbsoluteAssetUrls()
                if (!etag.isNullOrBlank()) {
                    tokenStorage.configEtag = etag
                }
                NetworkResult.Success(data, etag)
            }
        }.getOrElse { NetworkResult.Failure(it) }

    suspend fun getCatalog(ifNoneMatch: String? = null): NetworkResult<KioskCatalogResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/catalog"
            val response =
                httpClient.get(endpoint) {
                    if (!ifNoneMatch.isNullOrBlank()) {
                        header(HttpHeaders.IfNoneMatch, ifNoneMatch)
                    }
                }

            if (response.status == HttpStatusCode.NotModified) {
                NetworkResult.NotModified
            } else {
                checkResponse(response)
                val etag = response.headers[HttpHeaders.ETag]
                val data = response.body<KioskCatalogResponse>().withAbsoluteAssetUrls()
                if (!etag.isNullOrBlank()) {
                    tokenStorage.catalogEtag = etag
                }
                NetworkResult.Success(data, etag)
            }
        }.getOrElse { NetworkResult.Failure(it) }

    suspend fun quoteOrder(
        idempotencyKey: String,
        request: KioskQuoteRequest,
    ): Result<KioskQuoteResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/orders/quote"
            val response =
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header("Idempotency-Key", idempotencyKey)
                    setBody(request)
                }
            checkResponse(response)
            response.body<KioskQuoteResponse>()
        }

    suspend fun payOrder(
        orderId: String,
        request: KioskPaymentRequest,
    ): Result<KioskPaymentResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/orders/$orderId/pay"
            val response =
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            // Accepts 200 OK (Fiscal Success) and 202 Accepted (Paid Pending Invoice)
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Accepted) {
                checkResponse(response)
            }
            response.body<KioskPaymentResponse>()
        }

    /** Creates a Yappy charge for the quoted order; the server fixes the amount to the quote total. */
    suspend fun createYappyCharge(orderId: String): Result<KioskYappyChargeResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/orders/$orderId/yappy"
            val response = httpClient.post(endpoint)
            checkResponse(response)
            response.body<KioskYappyChargeResponse>()
        }

    suspend fun getYappyStatus(
        orderId: String,
        transactionId: String,
    ): Result<KioskYappyStatusResponse> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/orders/$orderId/yappy/$transactionId"
            val response = httpClient.get(endpoint)
            checkResponse(response)
            response.body<KioskYappyStatusResponse>()
        }

    /** Best-effort cancellation of a pending Yappy charge (204 expected). */
    suspend fun cancelYappyCharge(
        orderId: String,
        transactionId: String,
    ): Result<Unit> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/orders/$orderId/yappy/$transactionId"
            val response = httpClient.delete(endpoint)
            checkResponse(response)
        }

    /**
     * Admin unlock with the kiosk password. The backend answers 401 for a wrong password, so a 401
     * here means `false` and never logs the kiosk out (a revoked token surfaces on the next call).
     */
    suspend fun unlock(password: String): Result<Boolean> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/unlock"
            val response =
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(KioskUnlockRequest(password))
                }
            if (response.status == HttpStatusCode.Unauthorized) return@runCatching false
            checkResponse(response)
            response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK
        }

    /** The backend serves asset paths relative to its root (`/api/data/...`); image loaders need absolute URLs. */
    private fun resolveAssetUrl(url: String?): String? =
        if (url != null && url.startsWith("/") && !url.startsWith("//")) baseUrl + url.removePrefix("/") else url

    private fun KioskConfigResponse.withAbsoluteAssetUrls(): KioskConfigResponse =
        copy(
            logoUrl = resolveAssetUrl(logoUrl),
            media = media.map { it.copy(url = resolveAssetUrl(it.url) ?: it.url) },
        )

    private fun KioskCatalogResponse.withAbsoluteAssetUrls(): KioskCatalogResponse =
        copy(
            categories = categories.map { it.copy(iconUrl = resolveAssetUrl(it.iconUrl)) },
            items = items.map { it.copy(imageUrl = resolveAssetUrl(it.imageUrl)) },
        )

    /** Maps transport failures to [KioskConnectivityException]; API errors pass through. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> authCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: KioskApiException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(KioskConnectivityException(e))
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun checkAuthResponse(
        response: HttpResponse,
        fallback: String,
    ) {
        if (isSuccessStatus(response.status)) return
        val serverMessage = parseErrorMessage(response.bodyAsText()).takeIf { it.isNotBlank() && !it.trimStart().startsWith("<") }
        throw KioskServerException(serverMessage ?: fallback, response.status.value, serverMessage)
    }

    private suspend fun checkResponse(response: HttpResponse) {
        if (!isSuccessStatus(response.status)) {
            throw buildApiException(response)
        }
    }

    private fun isSuccessStatus(status: HttpStatusCode): Boolean =
        status == HttpStatusCode.OK ||
            status == HttpStatusCode.Created ||
            status == HttpStatusCode.Accepted ||
            status == HttpStatusCode.NoContent

    private suspend fun buildApiException(response: HttpResponse): KioskApiException {
        val errorBody = response.bodyAsText()
        val errorMessage = parseErrorMessage(errorBody)
        return when (response.status) {
            // 403 "Se requiere token de empresa": an identity token reached a kiosk call; same as a revoked session.
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden.takeIf { isCompanyTokenRequiredError(errorMessage) } -> {
                tokenStorage.clearSession()
                _sessionEvents.tryEmit(KioskSessionEvent.LoggedOut)
                KioskAuthenticationException("Sesión del kiosco vencida o revocada (401)")
            }
            HttpStatusCode.BadRequest ->
                if (isInvalidCajaError(errorMessage)) {
                    tokenStorage.clearCaja()
                    _sessionEvents.tryEmit(KioskSessionEvent.InvalidCaja)
                    KioskInvalidCajaException(errorMessage)
                } else {
                    KioskBadRequestException("Petición inválida (400): $errorMessage", errorMessage)
                }
            HttpStatusCode.Conflict -> KioskConflictException(errorMessage)
            else ->
                KioskServerException(
                    "Error del servidor (${response.status.value}): $errorMessage",
                    response.status.value,
                    errorMessage,
                )
        }
    }

    private fun parseErrorMessage(body: String): String =
        runCatching { KioskHttpClientFactory.jsonConfig.decodeFromString(KioskErrorResponse.serializer(), body).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body

    companion object {
        private const val SESSION_EVENT_BUFFER = 4
        const val INVALID_CREDENTIALS_MESSAGE = "Usuario o contraseña incorrectos"

        /**
         * 400s that mean the caja/prefix configured on this device is unusable: "Caja del kiosco no
         * válida", or a missing/invalid `X-Kiosk-Caja` / `X-Kiosk-Prefix` header.
         */
        fun isInvalidCajaError(message: String?): Boolean {
            val normalized = message?.lowercase()?.replace('á', 'a').orEmpty()
            return (normalized.contains("caja del kiosco") && normalized.contains("no valida")) ||
                normalized.contains(KioskHeaders.CAJA.lowercase()) ||
                normalized.contains(KioskHeaders.PREFIX.lowercase())
        }

        /** Backend 403 `{"error":"Se requiere token de empresa"}`. */
        fun isCompanyTokenRequiredError(message: String?): Boolean = message?.lowercase()?.contains("token de empresa") == true

        /** Trims, adds `https://` when the scheme is missing and guarantees a trailing slash. */
        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim()
            val withScheme =
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    trimmed
                } else {
                    "https://$trimmed"
                }
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}
