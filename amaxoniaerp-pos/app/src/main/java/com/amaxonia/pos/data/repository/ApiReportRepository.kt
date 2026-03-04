package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.PaymentMethodStats
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.repository.ReportRepository

/**
 * Implementación de ReportRepository que usa la API real para best sellers.
 * El resto de métodos se delegan al mock (pantalla Reportes).
 */
class ApiReportRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val fallback: ReportRepository
) : ReportRepository {

    private val bestSellerColors = longArrayOf(
        0xFF1565C0,
        0xFF2E7D32,
        0xFFFFA000,
        0xFFC62828,
        0xFF6A1B9A,
        0xFF00838F,
        0xFF558B2F,
        0xFFEF6C00
    )

    override suspend fun getSummaryStats(): Result<SummaryStats> = fallback.getSummaryStats()

    override suspend fun getBestSellers(): Result<List<BestSellerProduct>> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val list = apiService.getBestSellers(token, 20)
            val maxCount = list.maxOfOrNull { it.salesCount }?.toFloat() ?: 1f
            list.mapIndexed { index, dto ->
                BestSellerProduct(
                    id = dto.id,
                    name = dto.name,
                    price = dto.price,
                    salesCount = dto.salesCount,
                    progress = if (maxCount > 0) dto.salesCount / maxCount else 0f,
                    colorHex = bestSellerColors[index % bestSellerColors.size],
                    photoUrl = dto.photoUrl
                )
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun getChartData(): Result<List<Float>> = fallback.getChartData()

    override suspend fun getPaymentMethodStats(): Result<PaymentMethodStats> = fallback.getPaymentMethodStats()
}
