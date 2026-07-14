package com.amaxonia.pos.domain.model

data class SummaryStats(
    val grossSales: Double = 0.0,
    val netSales: Double = 0.0,
    val discounts: Double = 0.0,
    val cancellations: Double = 0.0,
    val totalTransactions: Int = 0,
    val totalPaid: Int = 0,
    val totalCancelled: Int = 0,
    val ticketPromedio: Double = 0.0,
    val moneda: String = "USD",
)

data class BestSellerProduct(
    val id: String,
    val name: String,
    val price: Double,
    val salesCount: Int,
    val progress: Float,
    val colorHex: Long,
    val photoUrl: String = "",
)
