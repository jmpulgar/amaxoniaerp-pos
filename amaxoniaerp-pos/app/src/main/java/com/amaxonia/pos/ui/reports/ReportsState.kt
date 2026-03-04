package com.amaxonia.pos.ui.reports

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats

data class ReportsState(
    val isLoading: Boolean = false,
    val summary: SummaryStats? = null,
    val bestSellers: List<BestSellerProduct> = emptyList(),
    val error: String? = null,
)
