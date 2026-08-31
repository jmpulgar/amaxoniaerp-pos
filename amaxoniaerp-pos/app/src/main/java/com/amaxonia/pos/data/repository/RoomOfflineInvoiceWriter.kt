package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.db.PendingInvoiceEntity
import com.amaxonia.pos.domain.model.money.MinorUnitMoney
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.usecase.payment.OfflineInvoice
import com.amaxonia.pos.domain.usecase.payment.OfflineInvoiceWriter

class RoomOfflineInvoiceWriter(
    private val dao: PendingInvoiceDao,
    private val onQueued: (() -> Unit)? = null,
) : OfflineInvoiceWriter {
    override suspend fun write(invoice: OfflineInvoice) {
        dao.insert(
            PendingInvoiceEntity(
                id = invoice.id,
                countryCode = invoice.countryCode,
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), invoice.request),
                localInvoiceNumber = invoice.localInvoiceNumber,
                clientName = invoice.clientName,
                createdAt = invoice.createdAt,
                updatedAt = invoice.createdAt,
                tenantId = invoice.tenant.tenantId,
                tenantCompanyId = invoice.tenant.companyId,
                tenantLabel = invoice.tenant.label,
                // Canonical minor-units (MONEY-001). Antes de v18 esta ruta
                // dejaba el default 0L: la columna era escrita pero jamás leída.
                totalMinor = MinorUnitMoney.fromDoubleAsMinor(invoice.total),
            ),
        )
        onQueued?.invoke()
    }
}
