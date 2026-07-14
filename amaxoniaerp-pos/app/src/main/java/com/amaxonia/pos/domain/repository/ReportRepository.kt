package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats

interface ReportRepository {
    suspend fun getSummaryStats(): Result<SummaryStats>

    suspend fun getBestSellers(): Result<List<BestSellerProduct>>
}
