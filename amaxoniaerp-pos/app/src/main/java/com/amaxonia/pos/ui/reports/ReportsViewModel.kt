package com.amaxonia.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val reportRepository: ReportRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        loadReportData()
    }

    fun retry() {
        loadReportData()
    }

    private fun loadReportData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val summaryResult = reportRepository.getSummaryStats()
            val bestSellersResult = reportRepository.getBestSellers()
            val chartDataResult = reportRepository.getChartData()
            val paymentStatsResult = reportRepository.getPaymentMethodStats()
            val allResults = listOf(summaryResult, bestSellersResult, chartDataResult, paymentStatsResult)
            val hasError = allResults.any { it.isFailure }
            if (hasError) {
                val errorMessage = allResults.firstOrNull { it.isFailure }?.exceptionOrNull()?.message
                    ?: "Error al cargar datos de reportes"
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        summary = summaryResult.getOrNull() ?: it.summary,
                        bestSellers = bestSellersResult.getOrNull() ?: it.bestSellers,
                        chartData = chartDataResult.getOrNull() ?: it.chartData,
                        paymentStats = paymentStatsResult.getOrNull() ?: it.paymentStats,
                        error = null
                    )
                }
            }
        }
    }
}
