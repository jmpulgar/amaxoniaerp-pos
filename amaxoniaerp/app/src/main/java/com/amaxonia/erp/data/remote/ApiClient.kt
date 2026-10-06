package com.amaxonia.erp.data.remote

import android.os.Build
import com.amaxonia.erp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

val AppJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

class ApiClient(
    private var baseUrl: String = BuildConfig.BASE_URL,
) {
    val currentBaseUrl: String
        get() = baseUrl

    private var client: HttpClient? = null

    fun getHttpClient(): HttpClient = client ?: createHttpClient().also { client = it }

    fun setBaseUrl(newUrl: String) {
        if (baseUrl != newUrl) {
            baseUrl = newUrl
            client?.close()
            client = createHttpClient()
        }
    }

    private fun createHttpClient(): HttpClient {
        val okHttpClient =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                val tls12Spec =
                    ConnectionSpec
                        .Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build()
                val connectionSpecs =
                    if (BuildConfig.DEBUG) {
                        listOf(tls12Spec, ConnectionSpec.CLEARTEXT)
                    } else {
                        listOf(tls12Spec)
                    }
                OkHttpClient
                    .Builder()
                    .connectionSpecs(connectionSpecs)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
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
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
            }
        }
    }
}
