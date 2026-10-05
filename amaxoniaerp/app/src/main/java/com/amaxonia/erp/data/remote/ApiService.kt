package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.ErrorResponse
import com.amaxonia.erp.data.remote.dto.LoginRequest
import com.amaxonia.erp.data.remote.dto.LoginResponse
import com.amaxonia.erp.domain.usecase.ConnectivityException
import com.amaxonia.erp.domain.usecase.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException

class ApiService(
    private val apiClient: ApiClient,
) {
    internal val client: HttpClient
        get() = apiClient.getHttpClient()

    suspend fun login(
        request: LoginRequest,
        countryCode: String,
    ): LoginResponse {
        val response =
            try {
                client.post("auth/login") {
                    header("X-Country-Code", countryCode)
                    setBody(request)
                }
            } catch (e: IOException) {
                throw ConnectivityException("No se pudo conectar con el servidor", e)
            } catch (e: Exception) {
                throw ConnectivityException(e.message ?: "Error de red", e)
            }

        if (!response.status.isSuccess()) {
            if (response.status.value == 401) {
                throw UnauthorizedException("Usuario o contraseña incorrectos")
            }
            val errorBody =
                runCatching {
                    AppJson.decodeFromString<ErrorResponse>(response.bodyAsText()).error
                }.getOrNull()
            throw Exception(errorBody ?: "Error al iniciar sesión (${response.status.value})")
        }

        return response.body()
    }

    suspend fun selectCompany(
        token: String,
        request: com.amaxonia.erp.data.remote.dto.SelectCompanyRequest,
    ): com.amaxonia.erp.data.remote.dto.SelectCompanyResponse {
        val response =
            try {
                client.post("auth/company") {
                    header("Authorization", "Bearer $token")
                    header("Accept-Charset", "utf-8")
                    setBody(request)
                }
            } catch (e: IOException) {
                throw ConnectivityException("No se pudo conectar con el servidor", e)
            } catch (e: Exception) {
                throw ConnectivityException(e.message ?: "Error de red", e)
            }

        if (!response.status.isSuccess()) {
            val errorBody =
                runCatching {
                    AppJson.decodeFromString<ErrorResponse>(response.bodyAsText()).error
                }.getOrNull()
            throw Exception(errorBody ?: "Error al seleccionar empresa (${response.status.value})")
        }

        return response.body()
    }
}
