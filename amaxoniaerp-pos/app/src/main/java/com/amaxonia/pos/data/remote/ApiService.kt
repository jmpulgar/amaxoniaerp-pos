package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.dto.ErrorResponse
import com.amaxonia.pos.data.remote.dto.FacturasResumenDto
import com.amaxonia.pos.data.remote.dto.LoginRequest
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.data.remote.dto.SelectCompanyRequest
import com.amaxonia.pos.data.remote.dto.SelectCompanyResponse
import com.amaxonia.pos.domain.error.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class ApiService(
    private val apiClient: ApiClient,
) {
    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }

    // Acceso al cliente HTTP actual (se recrea automáticamente si cambia la URL).
    // Internal: las extensiones de catálogo/directorio del mismo package lo reutilizan.
    internal val client: HttpClient
        get() = apiClient.httpClient

    suspend fun login(
        request: LoginRequest,
        countryCode: String,
    ): LoginResponse {
        val response =
            client.post("auth/login") {
                // Enviar el código de país al backend para que sepa a qué BD de config conectarse
                header("X-Country-Code", countryCode)
                setBody(request)
            }
        if (!response.status.isSuccess()) {
            if (response.status.value == HTTP_UNAUTHORIZED) {
                throw UnauthorizedException("Usuario o contrasena incorrectos")
            }
            val message =
                runCatching {
                    AppJson
                        .decodeFromString(
                            ErrorResponse.serializer(),
                            response.bodyAsText(),
                        ).error
                }.getOrNull()
            error(message ?: "No se pudo iniciar sesion")
        }
        return response.body()
    }

    suspend fun selectCompany(
        token: String,
        request: SelectCompanyRequest,
    ): SelectCompanyResponse =
        client
            .post("auth/company") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun getFacturasResumen(token: String): FacturasResumenDto =
        client
            .get("facturas/resumen") {
                authHeaders(token)
            }.body()
}

/** Headers comunes de autenticación para los endpoints del API. */
internal fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
    // IMPORTANTE: Forzamos UTF-8 para evitar los "?"
    header(HttpHeaders.AcceptCharset, "utf-8")
}

/** Página solicitada a un endpoint de listado de catálogo (items, clientes, catálogos). */
data class CatalogPage(
    val limit: Int,
    val offset: Int,
    val search: String? = null,
    val includeTotal: Boolean? = null,
)
