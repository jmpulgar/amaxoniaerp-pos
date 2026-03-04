package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.PaymentMethodStats
import com.amaxonia.pos.domain.model.SummaryStats
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    suspend fun getSummaryStats(): Result<SummaryStats>
    suspend fun getBestSellers(): Result<List<BestSellerProduct>>
    suspend fun getChartData(): Result<List<Float>>
    suspend fun getPaymentMethodStats(): Result<PaymentMethodStats>
}
