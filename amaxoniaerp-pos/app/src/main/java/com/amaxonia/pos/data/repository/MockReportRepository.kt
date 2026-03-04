package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.PaymentMethodStats
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.repository.ReportRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

class MockReportRepository : ReportRepository {
    private val failureRate = 0.1

    override suspend fun getSummaryStats(): Result<SummaryStats> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar estadísticas de resumen"))
        }
        val summary = SummaryStats(
            grossSales = 31200.00,
            netSales = 11000.00,
            discounts = 430.00,
            cancellations = 0.00,
            totalTransactions = 112
        )
        return Result.success(summary)
    }

    override suspend fun getBestSellers(): Result<List<BestSellerProduct>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar productos más vendidos"))
        }
        val bestSellers = listOf(
            BestSellerProduct(
                id = "1",
                name = "Ensalada de Atún",
                price = 12.50,
                salesCount = 45,
                progress = 1.0f,
                colorHex = 0xFF1565C0
            ),
            BestSellerProduct(
                id = "2",
                name = "Ensalada César",
                price = 10.99,
                salesCount = 38,
                progress = 0.84f,
                colorHex = 0xFF2E7D32
            ),
            BestSellerProduct(
                id = "3",
                name = "Brochetas Wagyu",
                price = 25.00,
                salesCount = 32,
                progress = 0.71f,
                colorHex = 0xFFFFA000
            )
        )
        return Result.success(bestSellers)
    }

    override suspend fun getChartData(): Result<List<Float>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar datos del gráfico"))
        }
        val chartData = listOf(0.2f, 0.5f, 0.8f, 0.6f, 0.9f, 0.7f, 1.0f)
        return Result.success(chartData)
    }

    override suspend fun getPaymentMethodStats(): Result<PaymentMethodStats> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar estadísticas de métodos de pago"))
        }
        val stats = PaymentMethodStats(
            method = "Cash",
            amount = 8500.00,
            count = 78,
            percentage = 70
        )
        return Result.success(stats)
    }

    private suspend fun simulateNetworkDelay() {
        delay((400..1200).random().toLong())
    }

    private fun shouldSimulateError(): Boolean {
        return Random.nextFloat() < failureRate
    }
}
