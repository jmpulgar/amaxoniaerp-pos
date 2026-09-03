package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasResumenDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.usecase.payment.DuplicateInvoiceException
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class SalesApiImpl(
    private val apiClient: ApiClient,
) : SalesApi {
    private companion object {
        const val HTTP_CONFLICT = 409
        const val HTTP_NOT_FOUND: Int = 404
    }

    override suspend fun processSale(
        authHeader: String,
        payload: ProcessSaleRequestDto,
    ): Result<ProcessSaleResponseDto> =
        catchingResult {
            val response =
                apiClient.httpClient.post("api/pos/ventas/procesar") {
                    header("Authorization", authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

            val responseText = response.bodyAsText()
            if (response.status.value == HTTP_CONFLICT) {
                val correlationId = payload.idFactura.orEmpty()
                val reason =
                    runCatching {
                        val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                        if (json is JsonObject) {
                            json["error"]?.jsonPrimitive?.contentOrNull
                        } else {
                            null
                        }
                    }.getOrNull() ?: "La factura ya fue procesada en un intento anterior"
                throw DuplicateInvoiceException(
                    clientCorrelationId = correlationId,
                    message = reason,
                )
            }
            val parsed =
                runCatching {
                    AppJson.decodeFromString(ProcessSaleResponseDto.serializer(), responseText)
                }.getOrElse {
                    SafeLog.e("SalesApi", "Sale response could not be parsed; status=${response.status.value}", it)
                    val json =
                        runCatching {
                            AppJson.decodeFromString(JsonElement.serializer(), responseText)
                        }.getOrNull()
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "No se pudo procesar la venta")
                    }
                    error(responseText.ifBlank { "Respuesta invalida al procesar la venta" })
                }

            Result.success(parsed)
        }

    override suspend fun getFacturas(
        authHeader: String,
        limit: Int,
        offset: Long,
        filter: InvoiceHistoryFilter,
    ): Result<FacturasListResponseDto> =
        catchingResult {
            val response =
                apiClient.httpClient.get("facturas") {
                    header("Authorization", authHeader)
                    parameter("limit", limit)
                    parameter("offset", offset)
                    applyInvoiceHistoryFilter(filter)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(FacturasListResponseDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al obtener facturas")
                    }
                    error("Respuesta invalida al obtener facturas")
                }

            Result.success(parsed)
        }

    override suspend fun getFacturasResumen(
        authHeader: String,
        filter: InvoiceHistoryFilter,
    ): Result<FacturasResumenDto> =
        catchingResult {
            val response =
                apiClient.httpClient.get("facturas/resumen") {
                    header("Authorization", authHeader)
                    applyInvoiceHistoryFilter(filter)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(FacturasResumenDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al obtener resumen de facturas")
                    }
                    error("Respuesta invalida al obtener resumen de facturas")
                }

            Result.success(parsed)
        }

    override suspend fun getFacturaDetalle(
        authHeader: String,
        facturaId: String,
    ): Result<FacturaDetalleResponseDto> =
        catchingResult {
            val response =
                apiClient.httpClient.get("facturas/$facturaId/detalle") {
                    header("Authorization", authHeader)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(FacturaDetalleResponseDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al obtener detalle de factura")
                    }
                    error("Respuesta invalida al obtener detalle")
                }

            Result.success(parsed)
        }

    override suspend fun findByCorrelationId(
        authHeader: String,
        clientCorrelationId: String,
    ): Result<ReconciledInvoice?> =
        catchingResult {
            // Auditoría ítem 2 (INT-BE-001): after an HTTP 409, the POS queries
            // the existing invoice by its canonical idFactura. A 404 means
            // "backend has no row for this id" — surfaced as null so the
            // caller can fall back to manual DuplicateInvoice handling.
            val response =
                apiClient.httpClient.get("facturas/by-id-factura/$clientCorrelationId") {
                    header("Authorization", authHeader)
                }
            when (response.status.value) {
                HTTP_NOT_FOUND -> Result.success(null)
                else -> {
                    val text = response.bodyAsText()
                    val parsed =
                        runCatching {
                            AppJson.decodeFromString(ReconciledInvoice.serializer(), text)
                        }.getOrElse {
                            val json = AppJson.decodeFromString(JsonElement.serializer(), text)
                            if (json is JsonObject) {
                                val err = json["error"]?.jsonPrimitive?.contentOrNull
                                error(err ?: "Respuesta invalida al reconciliar factura")
                            }
                            error(text.ifBlank { "Respuesta invalida al reconciliar factura" })
                        }
                    Result.success(parsed)
                }
            }
        }

    override suspend fun confirmFacturaFiscal(
        authHeader: String,
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto,
    ): Result<ConfirmFacturaFiscalResponseDto> =
        catchingResult {
            val response =
                apiClient.httpClient.patch("facturas/$facturaId/confirmacion-fiscal") {
                    header("Authorization", authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(ConfirmFacturaFiscalResponseDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al confirmar factura fiscal")
                    }
                    error("Respuesta invalida al confirmar factura fiscal")
                }

            Result.success(parsed)
        }

    override suspend fun getPrintPayload(
        authHeader: String,
        facturaId: String,
    ): Result<FacturaPrintPayloadDto> =
        catchingResult {
            val response =
                apiClient.httpClient.get("facturas/$facturaId/print-payload") {
                    header("Authorization", authHeader)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(FacturaPrintPayloadDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al obtener payload de impresión")
                    }
                    error("Respuesta invalida al obtener payload de impresión")
                }

            Result.success(parsed)
        }

    override suspend fun sendReceiptEmail(
        authHeader: String,
        facturaId: String,
    ): Result<EnviarCorreoFacturaResponseDto> =
        catchingResult {
            val response =
                apiClient.httpClient.post("facturas/$facturaId/enviar-correo") {
                    header("Authorization", authHeader)
                }

            val responseText = response.bodyAsText()
            val parsed =
                runCatching {
                    AppJson.decodeFromString(EnviarCorreoFacturaResponseDto.serializer(), responseText)
                }.getOrElse {
                    val json = AppJson.decodeFromString(JsonElement.serializer(), responseText)
                    if (json is JsonObject) {
                        val error = json["error"]?.jsonPrimitive?.contentOrNull
                        error(error ?: "Error al enviar recibo por correo")
                    }
                    error("Respuesta invalida al enviar recibo por correo")
                }

            Result.success(parsed)
        }

    override suspend fun getInvoicePdf(
        authHeader: String,
        facturaId: String,
    ): Result<ByteArray> =
        catchingResult {
            val response =
                apiClient.httpClient.get("facturas/$facturaId/pdf") {
                    header("Authorization", authHeader)
                }

            if (response.status.value in 200..299) {
                Result.success(response.body())
            } else {
                val fallbackResponse =
                    apiClient.httpClient.get("api/facturacion-electronica/$facturaId/pdf") {
                        header("Authorization", authHeader)
                    }
                if (fallbackResponse.status.value in 200..299) {
                    Result.success(fallbackResponse.body())
                } else {
                    val errorText = runCatching { response.bodyAsText() }.getOrNull()
                    error(errorText?.takeIf(String::isNotBlank) ?: "El PDF de la factura no está disponible")
                }
            }
        }

    override suspend fun resendElectronicInvoice(
        authHeader: String,
        invoiceId: String,
    ): Result<ElectronicInvoiceResultDto> =
        catchingResult {
            val response =
                apiClient.httpClient.post("api/facturacion-electronica/$invoiceId/enviar") {
                    header("Authorization", authHeader)
                    contentType(ContentType.Application.Json)
                }

            val responseText = response.bodyAsText()
            if (response.status.value in 200..299) {
                val parsed = AppJson.decodeFromString(ElectronicInvoiceResultDto.serializer(), responseText)
                Result.success(parsed)
            } else {
                val json = runCatching { AppJson.decodeFromString(JsonElement.serializer(), responseText) }.getOrNull()
                val errorMsg =
                    extraerMensajeErrorFe(json as? JsonObject)
                        ?: "Error al reenviar factura electrónica"
                error(errorMsg)
            }
        }
}

internal fun HttpRequestBuilder.applyInvoiceHistoryFilter(filter: InvoiceHistoryFilter) {
    filter.search?.takeIf(String::isNotBlank)?.let { parameter("search", it) }
    filter.usuario?.takeIf(String::isNotBlank)?.let { parameter("usuario", it) }
    filter.fechaInicio?.takeIf(String::isNotBlank)?.let { parameter("fecha_inicio", it) }
    filter.fechaFin?.takeIf(String::isNotBlank)?.let { parameter("fecha_fin", it) }
    filter.cajaId?.takeIf(String::isNotBlank)?.let { parameter("caja_id", it) }
}

/**
 * Q3/Q4: extrae el mensaje de error del contrato FE del backend PA
 * (`{codigo, mensaje, reintentable, ...}`), manteniendo compatibilidad con
 * las claves legadas `error`/`message`. El [codigo] se prefija para que el
 * detalle (incluidas las incidencias DGI de 4 dígitos) llegue al operador.
 */
internal fun extraerMensajeErrorFe(json: JsonObject?): String? =
    json?.let { body ->
        val codigo = (body["codigo"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val mensaje =
            listOf("mensaje", "error", "message")
                .firstNotNullOfOrNull { key -> (body[key] as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() }
        if (mensaje != null && codigo != null) "[$codigo] $mensaje" else mensaje
    }
