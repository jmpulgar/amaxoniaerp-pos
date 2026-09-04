package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto

data class InvoiceHistoryFilter(
    val search: String? = null,
    val usuario: String? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val cajaId: String? = null,
)

data class InvoiceHistoryPage(
    val transactions: List<com.amaxonia.pos.domain.model.Transaction>,
    val total: Long,
)

data class InvoiceHistorySummary(
    val ventasNetas: Double = 0.0,
    val totalFacturas: Int = 0,
    val moneda: String = "USD",
)

interface InvoiceHistoryRepository : TransactionRepository {
    suspend fun getTransactions(
        filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
        limit: Int = 100,
        offset: Long = 0,
    ): Result<InvoiceHistoryPage>

    suspend fun getSummary(filter: InvoiceHistoryFilter = InvoiceHistoryFilter()): Result<InvoiceHistorySummary>

    suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto>

    suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray>

    suspend fun resendElectronicInvoice(invoiceId: String): Result<ElectronicInvoiceResultDto>
}
