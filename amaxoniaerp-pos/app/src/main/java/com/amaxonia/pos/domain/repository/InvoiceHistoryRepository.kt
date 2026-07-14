package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto

interface InvoiceHistoryRepository : TransactionRepository {
    suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto>
}
