package com.amaxonia.kiosk.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
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

open class KioskApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

class KioskAuthenticationException(message: String) : KioskApiException(message, statusCode = 401)

class KioskBadRequestException(message: String) : KioskApiException(message, statusCode = 400)

/** 409: the order is expired or not in a state that allows the requested operation. */
class KioskConflictException(message: String) : KioskApiException(message, statusCode = 409)

class KioskServerException(
    message: String,
    statusCode: Int = 500,
) : KioskApiException(message, statusCode)

object KioskHttpClientFactory {
    val jsonConfig =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
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

    private fun io.ktor.client.HttpClientConfig<*>.configureClient(tokenStorage: KioskTokenStorage) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(DefaultRequest) {
            header(HttpHeaders.Accept, "application/json")
            val token = tokenStorage.deviceToken
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
