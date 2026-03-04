package com.amaxonia.pos.domain.model

data class SummaryStats(
    val grossSales: Double = 0.0,
    val netSales: Double = 0.0,
    val discounts: Double = 0.0,
    val cancellations: Double = 0.0,
    val totalTransactions: Int = 0
)

data class BestSellerProduct(
    val id: String,
    val name: String,
    val price: Double,
    val salesCount: Int,
    val progress: Float,
    val colorHex: Long,
    val photoUrl: String = ""
)

data class PaymentMethodStats(
    val method: String = "Cash",
    val amount: Double = 0.0,
    val count: Int = 0,
    val percentage: Int = 0
)
