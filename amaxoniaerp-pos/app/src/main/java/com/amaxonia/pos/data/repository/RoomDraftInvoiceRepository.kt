package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.DraftInvoiceDao
import com.amaxonia.pos.data.local.db.DraftInvoiceEntity
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository

class RoomDraftInvoiceRepository(
    private val dao: DraftInvoiceDao,
) : DraftInvoiceRepository {
    override suspend fun all(): List<DraftInvoice> = dao.getAll().map { entity -> entity.toDomain() }

    override suspend fun save(draft: DraftInvoice) = dao.insert(draft.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun DraftInvoiceEntity.toDomain() =
        DraftInvoice(id, clientId, clientFirstName, clientLastName, sellerId, sellerName, itemsJson, total, itemCount, createdAt)

    private fun DraftInvoice.toEntity() =
        DraftInvoiceEntity(id, clientId, clientFirstName, clientLastName, sellerId, sellerName, itemsJson, total, itemCount, createdAt)
}
