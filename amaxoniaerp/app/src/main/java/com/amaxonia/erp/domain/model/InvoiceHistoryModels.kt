package com.amaxonia.erp.domain.model

data class InvoiceHistoryFilter(
    val search: String? = null,
    val usuario: String? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val cajaId: String? = null,
)

data class InvoiceHistoryPage(
    val transactions: List<Transaction>,
    val total: Long,
    val isOffline: Boolean = false,
)

data class InvoiceHistorySummary(
    val ventasNetas: Double = 0.0,
    val totalFacturas: Int = 0,
    val moneda: String = "USD",
)
