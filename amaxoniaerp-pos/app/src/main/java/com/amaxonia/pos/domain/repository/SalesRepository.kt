package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto

interface SalesRepository {
    suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto>

    suspend fun confirmFacturaFiscal(
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto,
    ): Result<ConfirmFacturaFiscalResponseDto>

    suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto>

    suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto>
}
