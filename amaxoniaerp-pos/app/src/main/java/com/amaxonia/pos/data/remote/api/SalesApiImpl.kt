package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
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

class SalesApiImpl(private val apiClient: ApiClient) : SalesApi {
    override suspend fun processSale(
        authHeader: String,
        payload: ProcessSaleRequestDto
    ): Result<ProcessSaleResponseDto> {
        return try {
            val response = apiClient.httpClient.post("api/pos/ventas/procesar") {
                header("Authorization", authHeader)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            val responseText = response.bodyAsText()
            val parsed = runCatching {
                AppJson.decodeFromString(ProcessSaleResponseDto.serializer(), responseText)
            }.getOrElse {
                val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                if (json is JsonObject) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                    throw IllegalStateException(error ?: "No se pudo procesar la venta")
                }
                throw IllegalStateException("Respuesta invalida al procesar la venta")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
