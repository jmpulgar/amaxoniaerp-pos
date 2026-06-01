package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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

    override suspend fun getFacturas(
        authHeader: String,
        limit: Int,
        offset: Long,
        search: String?
    ): Result<FacturasListResponseDto> {
        return try {
            val response = apiClient.httpClient.get("facturas") {
                header("Authorization", authHeader)
                parameter("limit", limit)
                parameter("offset", offset)
                if (!search.isNullOrBlank()) {
                    parameter("search", search)
                }
            }

            val responseText = response.bodyAsText()
            val parsed = runCatching {
                AppJson.decodeFromString(FacturasListResponseDto.serializer(), responseText)
            }.getOrElse {
                val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                if (json is JsonObject) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                    throw IllegalStateException(error ?: "Error al obtener facturas")
                }
                throw IllegalStateException("Respuesta invalida al obtener facturas")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFacturaDetalle(
        authHeader: String,
        facturaId: String
    ): Result<FacturaDetalleResponseDto> {
        return try {
            val response = apiClient.httpClient.get("facturas/$facturaId/detalle") {
                header("Authorization", authHeader)
            }

            val responseText = response.bodyAsText()
            val parsed = runCatching {
                AppJson.decodeFromString(FacturaDetalleResponseDto.serializer(), responseText)
            }.getOrElse {
                val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                if (json is JsonObject) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                    throw IllegalStateException(error ?: "Error al obtener detalle de factura")
                }
                throw IllegalStateException("Respuesta invalida al obtener detalle")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmFacturaFiscal(
        authHeader: String,
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto
    ): Result<ConfirmFacturaFiscalResponseDto> {
        return try {
            val response = apiClient.httpClient.patch("facturas/$facturaId/confirmacion-fiscal") {
                header("Authorization", authHeader)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            val responseText = response.bodyAsText()
            val parsed = runCatching {
                AppJson.decodeFromString(ConfirmFacturaFiscalResponseDto.serializer(), responseText)
            }.getOrElse {
                val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                if (json is JsonObject) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                    throw IllegalStateException(error ?: "Error al confirmar factura fiscal")
                }
                throw IllegalStateException("Respuesta invalida al confirmar factura fiscal")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPrintPayload(
        authHeader: String,
        facturaId: String
    ): Result<FacturaPrintPayloadDto> {
        return try {
            val response = apiClient.httpClient.get("facturas/$facturaId/print-payload") {
                header("Authorization", authHeader)
            }

            val responseText = response.bodyAsText()
            val parsed = runCatching {
                AppJson.decodeFromString(FacturaPrintPayloadDto.serializer(), responseText)
            }.getOrElse {
                val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                if (json is JsonObject) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                    throw IllegalStateException(error ?: "Error al obtener payload de impresión")
                }
                throw IllegalStateException("Respuesta invalida al obtener payload de impresión")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
