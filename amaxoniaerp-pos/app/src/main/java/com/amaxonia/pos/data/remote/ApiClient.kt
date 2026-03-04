package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.local.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

import io.ktor.client.plugins.HttpTimeout

/**
 * Cliente HTTP de Ktor configurado dinámicamente según el país seleccionado.
 * A diferencia de la implementación estática anterior, este cliente usa un provider
 * de URL base que puede cambiar en runtime cuando el usuario selecciona otro país.
 */
class ApiClient(
    private val apiConfigManager: ApiConfigManager
) {
    /**
     * Crea un nuevo HttpClient con la URL base actual.
     * Este método debe llamarse cada vez que se necesita un cliente actualizado.
     */
    fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10000 // 10 segundos
                connectTimeoutMillis = 10000 // 10 segundos
                socketTimeoutMillis = 10000
            }
            defaultRequest {
                // Usa la URL base actual del ApiConfigManager
                url(apiConfigManager.baseUrl.value)
                contentType(ContentType.Application.Json)
            }
        }
    }

    /**
     * Cliente HTTP cacheado. Se recrea solo cuando cambia la URL base.
     */
    private var cachedClient: HttpClient? = null

    /**
     * Obtiene el HttpClient actual, recreándolo si es necesario.
     */
    val httpClient: HttpClient
        get() {
            // En una implementación más robusta, esto usaría un flow
            // para recrear el cliente automáticamente cuando cambie la URL
            return cachedClient ?: createHttpClient().also { cachedClient = it }
        }

    /**
     * Fuerza la recreación del cliente HTTP (llamar después de cambiar el país)
     */
    fun recreateClient() {
        cachedClient?.close()
        cachedClient = createHttpClient()
    }
}
