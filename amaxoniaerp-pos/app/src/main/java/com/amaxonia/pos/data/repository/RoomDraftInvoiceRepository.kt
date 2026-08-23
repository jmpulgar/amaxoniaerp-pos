package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.DraftInvoiceDao
import com.amaxonia.pos.data.local.db.DraftInvoiceEntity
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.money.MinorUnitMoney
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import java.math.BigDecimal

/**
 * Boundary dominio↔Room para borradores. La persistencia es minor-units
 * canónico (MONEY-001); la conversión usa `MinorUnitMoney`, que rechaza con
 * [com.amaxonia.pos.domain.model.money.MoneyOverflowException] cualquier
 * residuo sub-centavo material en lugar de redondear silenciosamente.
 */
class RoomDraftInvoiceRepository(
    private val dao: DraftInvoiceDao,
) : DraftInvoiceRepository {
    override suspend fun all(): List<DraftInvoice> = dao.getAll().map { entity -> entity.toDomain() }

    override suspend fun save(draft: DraftInvoice) = dao.insert(draft.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun DraftInvoiceEntity.toDomain() =
        DraftInvoice(
            id = id,
            clientId = clientId,
            clientFirstName = clientFirstName,
            clientLastName = clientLastName,
            sellerId = sellerId,
            sellerName = sellerName,
            itemsJson = itemsJson,
            total = totalMinor.toDomainMoney(),
            itemCount = itemCount,
            createdAt = createdAt,
            subtotalGross = subtotalGrossMinor.toDomainMoney(),
            itemDiscounts = itemDiscountsMinor.toDomainMoney(),
            subtotalNet = subtotalNetMinor.toDomainMoney(),
            tax = taxMinor.toDomainMoney(),
        )

    private fun DraftInvoice.toEntity() =
        DraftInvoiceEntity(
            id = id,
            clientId = clientId,
            clientFirstName = clientFirstName,
            clientLastName = clientLastName,
            sellerId = sellerId,
            sellerName = sellerName,
            itemsJson = itemsJson,
            totalMinor = MinorUnitMoney.fromDoubleAsMinor(total),
            itemCount = itemCount,
            createdAt = createdAt,
            subtotalGrossMinor = MinorUnitMoney.fromDoubleAsMinor(subtotalGross),
            itemDiscountsMinor = MinorUnitMoney.fromDoubleAsMinor(itemDiscounts),
            subtotalNetMinor = MinorUnitMoney.fromDoubleAsMinor(subtotalNet),
            taxMinor = MinorUnitMoney.fromDoubleAsMinor(tax),
        )

    private fun Long.toDomainMoney(): Double = BigDecimal.valueOf(this).movePointLeft(MinorUnitMoney.DEFAULT_SCALE).toDouble()
}
