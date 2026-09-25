package com.amaxonia.pos.ui.reports

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats

enum class ReportPeriod(val label: String) {
    TODAY("Hoy"),
    YESTERDAY("Ayer"),
    THIS_WEEK("Esta semana"),
    THIS_MONTH("Este mes"),
}

data class PaymentBreakdownItem(
    val name: String,
    val amount: Double,
    val count: Int,
    val percentage: Float,
)

data class ReportsState(
    val isLoading: Boolean = false,
    val summary: SummaryStats? = null,
    val bestSellers: List<BestSellerProduct> = emptyList(),
    val paymentBreakdown: List<PaymentBreakdownItem> = emptyList(),
    val selectedPeriod: ReportPeriod = ReportPeriod.TODAY,
    val onlyActiveCaja: Boolean = true,
    val activeCajaName: String? = null,
    val activeCajaId: String? = null,
    val error: String? = null,
)
