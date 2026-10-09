package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.ElectronicInvoiceResultDto
import com.amaxonia.erp.data.remote.dto.FacturaDetalleResponseDto
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import com.amaxonia.erp.domain.model.InvoiceHistoryPage
import com.amaxonia.erp.domain.model.InvoiceHistorySummary
import com.amaxonia.erp.domain.model.Transaction

interface InvoiceHistoryRepository {
    suspend fun getTransactions(
        filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
        limit: Int = 100,
        offset: Long = 0,
    ): Result<InvoiceHistoryPage>

    suspend fun getSummary(filter: InvoiceHistoryFilter = InvoiceHistoryFilter()): Result<InvoiceHistorySummary>

    suspend fun getTransactionById(id: String): Result<Transaction>

    suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto>

    suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray>

    suspend fun resendElectronicInvoice(invoiceId: String): Result<ElectronicInvoiceResultDto>
}
