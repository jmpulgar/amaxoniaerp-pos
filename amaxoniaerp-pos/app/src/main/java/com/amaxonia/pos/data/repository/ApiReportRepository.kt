package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.repository.ReportRepository

/**
 * Real implementation of ReportRepository that uses the API for both
 * summary stats (GET /facturas/resumen) and best sellers (GET /items/best-sellers).
 * No mock fallback — all data comes from the backend.
 */
class ApiReportRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
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

    override suspend fun getSummaryStats(): Result<SummaryStats> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val dto = apiService.getFacturasResumen(token)
            SummaryStats(
                grossSales = dto.ventasBrutas,
                netSales = dto.ventasNetas,
                discounts = dto.descuentos,
                cancellations = dto.cancelaciones,
                totalTransactions = dto.totalFacturas,
                totalPaid = dto.totalFacturasPagadas,
                totalCancelled = dto.totalFacturasAnuladas,
                ticketPromedio = dto.ticketPromedio,
                moneda = dto.moneda,
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

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
}
