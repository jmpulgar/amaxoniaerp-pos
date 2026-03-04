package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
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
}
