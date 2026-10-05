package com.amaxonia.kiosk.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
            val raw = tokenStorage.serverUrl?.trim() ?: "http://10.0.2.2:8080"
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
                val data = response.body<KioskConfigResponse>()
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
                val data = response.body<KioskCatalogResponse>()
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
        return when (response.status) {
            HttpStatusCode.Unauthorized -> {
                tokenStorage.clear()
                KioskAuthenticationException("Credenciales de kiosco inválidas o sesión expirada (401)")
            }
            HttpStatusCode.BadRequest -> KioskBadRequestException("Petición inválida (400): $errorBody")
            else -> KioskServerException("Error del servidor (${response.status.value}): $errorBody")
        }
    }
}
