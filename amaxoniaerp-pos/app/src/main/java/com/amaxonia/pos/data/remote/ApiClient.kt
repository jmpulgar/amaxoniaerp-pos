package com.amaxonia.pos.data.remote

import android.os.Build
import com.amaxonia.pos.data.local.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.HttpTimeout
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP de Ktor configurado dinámicamente según el país seleccionado.
 * En Android 10 (API 29) se fuerza TLS 1.2 para evitar cierres en el handshake SSL.
 */
class ApiClient(
    private val apiConfigManager: ApiConfigManager
) {
    /**
     * Crea un nuevo HttpClient con la URL base actual.
     * En API 29 usa un OkHttpClient con TLS 1.2 para compatibilidad.
     */
    fun createHttpClient(): HttpClient {
        val okHttpClient = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Android 10 y anteriores: forzar TLS 1.2 para evitar crashes en el SSL handshake
            val tls12Spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
            OkHttpClient.Builder()
                .connectionSpecs(listOf(tls12Spec, ConnectionSpec.CLEARTEXT))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        } else {
            null
        }

        return HttpClient(OkHttp) {
            if (okHttpClient != null) {
                engine {
                    preconfigured = okHttpClient
                }
            }
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 10000
            }
            defaultRequest {
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
