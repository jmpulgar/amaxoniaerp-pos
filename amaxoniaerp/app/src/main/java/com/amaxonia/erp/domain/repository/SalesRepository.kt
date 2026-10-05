package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleResponseDto

interface SalesRepository {
    suspend fun processSale(request: ProcessSaleRequestDto): Result<ProcessSaleResponseDto>
    suspend fun getFormasPago(cajaId: String? = null): Result<List<FormaPagoDto>>
    suspend fun getPrintPayload(facturaId: String): Result<com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto>
}

