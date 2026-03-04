package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto

interface SalesApi {
    suspend fun processSale(
        authHeader: String,
        payload: ProcessSaleRequestDto
    ): Result<ProcessSaleResponseDto>
}
