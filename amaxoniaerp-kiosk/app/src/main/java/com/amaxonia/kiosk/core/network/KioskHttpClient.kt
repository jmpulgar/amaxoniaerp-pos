package com.amaxonia.kiosk.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T, val etag: String? = null) : NetworkResult<T>()

    object NotModified : NetworkResult<Nothing>()

    data class Failure(val error: Throwable) : NetworkResult<Nothing>()
}

/** [serverMessage] is the backend's `{"error": "..."}` text, when it sent one. */
open class KioskApiException(
    message: String,
    val statusCode: Int? = null,
    val serverMessage: String? = null,
) : Exception(message)

/** 401 on a kiosk/caja call: the company token was revoked. The session is cleared. */
class KioskAuthenticationException(message: String) : KioskApiException(message, statusCode = 401)

class KioskBadRequestException(
    message: String,
    serverMessage: String? = null,
) : KioskApiException(message, statusCode = 400, serverMessage = serverMessage)

/** 400 "Caja del kiosco no válida": the configured caja was deleted/disabled. The caja is cleared. */
class KioskInvalidCajaException(message: String) : KioskApiException(message, statusCode = 400, serverMessage = message)

/** 409: the order is expired or not in a state that allows the requested operation. */
class KioskConflictException(message: String) : KioskApiException(message, statusCode = 409, serverMessage = message)

class KioskServerException(
    message: String,
    statusCode: Int = 500,
    serverMessage: String? = null,
) : KioskApiException(message, statusCode, serverMessage)

/** `auth/login` answered 401: wrong user or password (mirrors the POS). */
class KioskInvalidCredentialsException(message: String) : KioskApiException(message, statusCode = 401, serverMessage = message)

/** The server could not be reached (DNS, timeout, no network, wrong URL). */
class KioskConnectivityException(cause: Throwable) : KioskApiException(cause.message ?: "Sin conexión") {
    init {
        initCause(cause)
    }
}

/** Kiosk request headers (backend contract for every kiosk endpoint under api/v1/kiosk). */
object KioskHeaders {
    const val CAJA = "X-Kiosk-Caja"
    const val PREFIX = "X-Kiosk-Prefix"
    const val COUNTRY_CODE = "X-Country-Code"
    const val COMPANY_DB = "Company-DB"
    const val KIOSK_PATH = "/api/v1/kiosk/"
}

object KioskHttpClientFactory {
    val jsonConfig =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
            coerceInputValues = true
        }

    fun create(
        tokenStorage: KioskTokenStorage,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        return if (engine != null) {
            HttpClient(engine) {
                configureClient(tokenStorage)
            }
        } else {
            HttpClient(OkHttp) {
                configureClient(tokenStorage)
            }
        }
    }

    private fun HttpClientConfig<*>.configureClient(tokenStorage: KioskTokenStorage) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(DefaultRequest) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.AcceptCharset, "utf-8")
        }

        // Every kiosk call carries the company token and the caja/prefix configured on this device.
        install(
            createClientPlugin("KioskSessionHeaders") {
                onRequest { request, _ ->
                    if (request.url.buildString().contains(KioskHeaders.KIOSK_PATH)) {
                        val token = tokenStorage.authToken
                        if (!token.isNullOrBlank() && !request.headers.contains(HttpHeaders.Authorization)) {
                            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                        }
                        tokenStorage.cajaId?.takeIf { it.isNotBlank() }?.let { request.headers.append(KioskHeaders.CAJA, it) }
                        tokenStorage.prefix?.takeIf { it.isNotBlank() }?.let { request.headers.append(KioskHeaders.PREFIX, it) }
                    }
                }
            },
        )
    }
}
