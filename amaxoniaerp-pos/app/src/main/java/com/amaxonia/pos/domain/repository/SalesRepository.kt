package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto

interface SalesRepository {
    suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto>
    suspend fun confirmFacturaFiscal(facturaId: String, payload: ConfirmFacturaFiscalRequestDto): Result<ConfirmFacturaFiscalResponseDto>
    suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto>
}
