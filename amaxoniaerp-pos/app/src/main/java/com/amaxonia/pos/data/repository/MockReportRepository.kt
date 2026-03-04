package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.repository.ReportRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

class MockReportRepository : ReportRepository {
    private val failureRate = 0.1

    override suspend fun getSummaryStats(): Result<SummaryStats> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar estadisticas de resumen"))
        }
        val summary = SummaryStats(
            grossSales = 31200.00,
            netSales = 11000.00,
            discounts = 430.00,
            cancellations = 0.00,
            totalTransactions = 112,
            totalPaid = 110,
            totalCancelled = 2,
            ticketPromedio = 100.00,
            moneda = "USD",
        )
        return Result.success(summary)
    }

    override suspend fun getBestSellers(): Result<List<BestSellerProduct>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar productos mas vendidos"))
        }
        val bestSellers = listOf(
            BestSellerProduct(
                id = "1",
                name = "Ensalada de Atun",
                price = 12.50,
                salesCount = 45,
                progress = 1.0f,
                colorHex = 0xFF1565C0
            ),
            BestSellerProduct(
                id = "2",
                name = "Ensalada Cesar",
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

    private suspend fun simulateNetworkDelay() {
        delay((400..1200).random().toLong())
    }

    private fun shouldSimulateError(): Boolean {
        return Random.nextFloat() < failureRate
    }
}
