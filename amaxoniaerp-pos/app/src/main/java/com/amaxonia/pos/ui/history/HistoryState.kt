package com.amaxonia.pos.ui.history

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.sales.FacturaDetalleItemDto
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary

data class HistoryState(
    val isLoading: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val totalTransactions: Long = 0,
    val filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
    val summary: InvoiceHistorySummary = InvoiceHistorySummary(),
    val error: String? = null,
    val isOffline: Boolean = false,
    // Detail bottom sheet state
    val selectedTransaction: Transaction? = null,
    val detalleItems: List<FacturaDetalleItemDto> = emptyList(),
    val isLoadingDetalle: Boolean = false,
    val detalleError: String? = null,
    val showDetalleSheet: Boolean = false,
    val isReprinting: Boolean = false,
    val isDownloadingPdf: Boolean = false,
    val isResendingFE: Boolean = false,
    val detalleMessage: String? = null,
    val detalleActionError: String? = null,
)
