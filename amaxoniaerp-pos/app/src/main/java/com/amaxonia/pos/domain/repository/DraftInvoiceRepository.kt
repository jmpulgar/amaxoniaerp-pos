package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.DraftInvoice

interface DraftInvoiceRepository {
    suspend fun all(): List<DraftInvoice>

    suspend fun save(draft: DraftInvoice)

    suspend fun delete(id: String)
}
