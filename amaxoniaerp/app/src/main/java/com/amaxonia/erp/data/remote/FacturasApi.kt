package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.ElectronicInvoiceResultDto
import com.amaxonia.erp.data.remote.dto.FacturaDetalleResponseDto
import com.amaxonia.erp.data.remote.dto.FacturasListResponseDto
import com.amaxonia.erp.data.remote.dto.FacturasResumenDto
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun ApiService.getFacturas(
    token: String,
    companyDb: String,
    limit: Int = 100,
    offset: Long = 0,
    filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
): FacturasListResponseDto =
    client.get("facturas") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        parameter("limit", limit)
        parameter("offset", offset)
        filter.search?.takeIf(String::isNotBlank)?.let { parameter("search", it) }
        filter.usuario?.takeIf(String::isNotBlank)?.let { parameter("usuario", it) }
        filter.fechaInicio?.takeIf(String::isNotBlank)?.let { parameter("fecha_inicio", it) }
        filter.fechaFin?.takeIf(String::isNotBlank)?.let { parameter("fecha_fin", it) }
        filter.cajaId?.takeIf(String::isNotBlank)?.let { parameter("caja_id", it) }
    }.body()

suspend fun ApiService.getFacturasResumen(
    token: String,
    companyDb: String,
    filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
): FacturasResumenDto =
    client.get("facturas/resumen") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        filter.search?.takeIf(String::isNotBlank)?.let { parameter("search", it) }
        filter.usuario?.takeIf(String::isNotBlank)?.let { parameter("usuario", it) }
        filter.fechaInicio?.takeIf(String::isNotBlank)?.let { parameter("fecha_inicio", it) }
        filter.fechaFin?.takeIf(String::isNotBlank)?.let { parameter("fecha_fin", it) }
        filter.cajaId?.takeIf(String::isNotBlank)?.let { parameter("caja_id", it) }
    }.body()

suspend fun ApiService.getFacturaDetalle(
    token: String,
    companyDb: String,
    facturaId: String,
): FacturaDetalleResponseDto =
    client.get("facturas/$facturaId/detalle") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
    }.body()

suspend fun ApiService.getInvoicePdf(
    token: String,
    companyDb: String,
    facturaId: String,
): ByteArray {
    val response = client.get("facturas/$facturaId/pdf") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
    }
    if (response.status.value in 200..299) {
        return response.body()
    }
    val fallbackResponse = client.get("api/facturacion-electronica/$facturaId/pdf") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
    }
    if (fallbackResponse.status.value in 200..299) {
        return fallbackResponse.body()
    }
    val errorText = runCatching { response.bodyAsText() }.getOrNull()
    error(errorText?.takeIf(String::isNotBlank) ?: "El PDF de la factura no está disponible")
}

suspend fun ApiService.resendElectronicInvoice(
    token: String,
    companyDb: String,
    invoiceId: String,
): ElectronicInvoiceResultDto {
    val response = client.post("api/facturacion-electronica/$invoiceId/enviar") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        contentType(ContentType.Application.Json)
    }
    val text = response.bodyAsText()
    if (response.status.value in 200..299) {
        return AppJson.decodeFromString(text)
    }
    val json = runCatching { AppJson.decodeFromString<JsonElement>(text) }.getOrNull()
    val errorMsg = (json as? JsonObject)?.get("error")?.jsonPrimitive?.content
        ?: (json as? JsonObject)?.get("message")?.jsonPrimitive?.content
        ?: "Error al reenviar factura electrónica"
    error(errorMsg)
}
