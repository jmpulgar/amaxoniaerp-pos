package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto

fun interface PendingSalesReader {
    suspend fun createdBetween(
        fromMillis: Long,
        toMillis: Long,
    ): List<ProcessSaleRequestDto>
}
