package com.amaxonia.pos.ui.reports

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.PaymentMethodStats
import com.amaxonia.pos.domain.model.SummaryStats

data class ReportsState(
    val isLoading: Boolean = false,
    val summary: SummaryStats = SummaryStats(),
    val bestSellers: List<BestSellerProduct> = emptyList(),
    val chartData: List<Float> = emptyList(),
    val paymentStats: PaymentMethodStats = PaymentMethodStats(),
    val error: String? = null
)
