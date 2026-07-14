package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.repository.PendingSalesReader

class RoomPendingSalesReader(
    private val dao: PendingInvoiceDao,
) : PendingSalesReader {
    override suspend fun createdBetween(
        fromMillis: Long,
        toMillis: Long,
    ): List<ProcessSaleRequestDto> =
        dao.getCreatedBetween(fromMillis, toMillis).mapNotNull { invoice ->
            runCatching {
                AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), invoice.payloadJson)
            }.getOrNull()
        }
}
