package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.FormasPagoResponseDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

suspend fun ApiService.processSale(
    token: String,
    companyDb: String,
    request: ProcessSaleRequestDto,
): ProcessSaleResponseDto =
    client.post("api/pos/ventas/procesar") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        setBody(request)
    }.body()

suspend fun ApiService.getFormasPago(
    token: String,
    companyDb: String,
    cajaId: String? = null,
): FormasPagoResponseDto =
    client.get("api/pos/formas-pago") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
        if (!cajaId.isNullOrBlank()) {
            parameter("cajaId", cajaId)
        }
    }.body()

suspend fun ApiService.getFacturaPrintPayload(
    token: String,
    companyDb: String,
    facturaId: String,
): com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto =
    client.get("facturas/$facturaId/print-payload") {
        header("Authorization", "Bearer $token")
        header("Company-DB", companyDb)
    }.body()

