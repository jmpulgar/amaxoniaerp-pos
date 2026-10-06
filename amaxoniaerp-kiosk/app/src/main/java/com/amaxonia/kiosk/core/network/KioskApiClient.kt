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

class KioskApiClient(
    private val httpClient: HttpClient,
    private val tokenStorage: KioskTokenStorage,
) {
    private val baseUrl: String
        get() {
            val raw = tokenStorage.serverUrl?.trim() ?: BuildConfig.DEFAULT_SERVER_URL
            return if (raw.endsWith("/")) raw else "$raw/"
        }

    suspend fun pair(
        serverUrl: String,
        countryCode: String,
        companyDb: String,
        pairingCode: String,
    ): Result<KioskPairingResponse> =
        runCatching {
            val root = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val endpoint = "${root}api/v1/kiosk/pairing"

            val response =
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        KioskPairingRequest(
                            countryCode = countryCode.trim().uppercase(),
                            companyDb = companyDb.trim(),
                            pairingCode = pairingCode.trim(),
                        ),
                    )
                }

            checkResponse(response)
            val body = response.body<KioskPairingResponse>()
            tokenStorage.savePairing(
                KioskDeviceCredentials(
                    deviceId = body.deviceId,
                    deviceToken = body.deviceToken,
                    deviceName = body.deviceName,
                    prefix = body.prefix,
                    countryCode = countryCode.trim().uppercase(),
                    companyDb = companyDb.trim(),
                    serverUrl = root,
                ),
            )
            body
        }

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

    suspend fun unlock(password: String): Result<Boolean> =
        runCatching {
            val endpoint = "${baseUrl}api/v1/kiosk/unlock"
            val response =
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(KioskUnlockRequest(password))
                }
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
            HttpStatusCode.Unauthorized -> {
                tokenStorage.clear()
                KioskAuthenticationException("Credenciales de kiosco inválidas o sesión expirada (401)")
            }
            HttpStatusCode.BadRequest -> KioskBadRequestException("Petición inválida (400): $errorMessage")
            HttpStatusCode.Conflict -> KioskConflictException(errorMessage)
            else -> KioskServerException("Error del servidor (${response.status.value}): $errorMessage", response.status.value)
        }
    }

    private fun parseErrorMessage(body: String): String =
        runCatching { KioskHttpClientFactory.jsonConfig.decodeFromString(KioskErrorResponse.serializer(), body).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body
}
