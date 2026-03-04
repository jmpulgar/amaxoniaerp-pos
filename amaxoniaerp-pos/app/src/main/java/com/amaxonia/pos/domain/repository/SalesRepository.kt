package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto

interface SalesRepository {
    suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto>
}
