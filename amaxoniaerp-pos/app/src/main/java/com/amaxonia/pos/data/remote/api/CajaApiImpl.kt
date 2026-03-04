package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class CajaApiImpl(private val apiClient: ApiClient) : CajaApi {
    override suspend fun getCajas(authHeader: String, companyDb: String): Result<List<Caja>> {
        return try {
            val response = apiClient.httpClient.get("api/cajas") {
                header("Authorization", authHeader)
                header("Company-DB", companyDb)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkCajaStatus(cajaId: String, authHeader: String, companyDb: String): Result<CajaStatusResponse> {
        return try {
            val response = apiClient.httpClient.get("api/cajas/$cajaId/status") {
                header("Authorization", authHeader)
                header("Company-DB", companyDb)
            }

            val responseText = response.bodyAsText()
            Result.success(parseCajaStatusResponse(responseText, "No se pudo validar el estado de la caja"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openCaja(request: AperturaRequest, authHeader: String, companyDb: String): Result<CajaStatusResponse> {
        return try {
            val response = apiClient.httpClient.post("api/cajas/open") {
                header("Authorization", authHeader)
                header("Company-DB", companyDb)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Result.success(parseCajaStatusResponse(responseText, "No se pudo abrir la caja"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCajaStatusResponse(responseText: String, fallbackMessage: String): CajaStatusResponse {
        return runCatching {
            AppJson.decodeFromString(CajaStatusResponse.serializer(), responseText)
        }.getOrElse {
            val json = runCatching {
                AppJson.decodeFromString(JsonElement.serializer(), responseText)
            }.getOrNull()

            if (json is JsonObject) {
                val error = json["error"]?.jsonPrimitive?.contentOrNull
                if (!error.isNullOrBlank()) {
                    throw IllegalStateException(error)
                }
            }

            throw IllegalStateException(fallbackMessage)
        }
    }
}
