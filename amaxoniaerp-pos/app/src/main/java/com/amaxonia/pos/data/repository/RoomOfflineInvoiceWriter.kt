package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.db.PendingInvoiceEntity
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.usecase.payment.OfflineInvoice
import com.amaxonia.pos.domain.usecase.payment.OfflineInvoiceWriter

class RoomOfflineInvoiceWriter(
    private val dao: PendingInvoiceDao,
) : OfflineInvoiceWriter {
    override suspend fun write(invoice: OfflineInvoice) {
        dao.insert(
            PendingInvoiceEntity(
                id = invoice.id,
                countryCode = invoice.countryCode,
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), invoice.request),
                localInvoiceNumber = invoice.localInvoiceNumber,
                total = invoice.total,
                clientName = invoice.clientName,
                createdAt = invoice.createdAt,
                updatedAt = invoice.createdAt,
                tenantId = invoice.tenant.tenantId,
                tenantCompanyId = invoice.tenant.companyId,
                tenantLabel = invoice.tenant.label,
            ),
        )
    }
}
