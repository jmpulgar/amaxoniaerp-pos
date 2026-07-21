package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator

data class OfflineInvoice(
    val id: String,
    val localInvoiceNumber: String,
    val countryCode: String,
    val request: ProcessSaleRequestDto,
    val total: Double,
    val clientName: String,
    val createdAt: Long,
    val tenant: SaleTenant,
)

fun interface OfflineInvoiceWriter {
    suspend fun write(invoice: OfflineInvoice)
}

class QueueOfflineInvoiceUseCase(
    private val writer: OfflineInvoiceWriter,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        countryCode: String,
        request: ProcessSaleRequestDto,
        total: Double,
        clientName: String,
        tenant: SaleTenant,
    ): OfflineInvoice {
        val now = clock.now().toEpochMilli()
        val id = idGenerator.nextId()
        val localNumber = "OFF-$now"
        val invoice =
            OfflineInvoice(
                id = id,
                localInvoiceNumber = localNumber,
                countryCode = countryCode,
                request = request.copy(idFactura = id, codFactura = localNumber),
                total = total,
                clientName = clientName,
                createdAt = now,
                tenant = tenant,
            )
        writer.write(invoice)
        return invoice
    }
}
