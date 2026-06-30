package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto

interface SalesApi {
    suspend fun processSale(
        authHeader: String,
        payload: ProcessSaleRequestDto
    ): Result<ProcessSaleResponseDto>

    suspend fun getFacturas(
        authHeader: String,
        limit: Int = 100,
        offset: Long = 0,
        search: String? = null
    ): Result<FacturasListResponseDto>

    suspend fun getFacturaDetalle(
        authHeader: String,
        facturaId: String
    ): Result<FacturaDetalleResponseDto>

    suspend fun confirmFacturaFiscal(
        authHeader: String,
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto
    ): Result<ConfirmFacturaFiscalResponseDto>

    suspend fun getPrintPayload(
        authHeader: String,
        facturaId: String
    ): Result<FacturaPrintPayloadDto>

    suspend fun sendReceiptEmail(
        authHeader: String,
        facturaId: String
    ): Result<EnviarCorreoFacturaResponseDto>
}
