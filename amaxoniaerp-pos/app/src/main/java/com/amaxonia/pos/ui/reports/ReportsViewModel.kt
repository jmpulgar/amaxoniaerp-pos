package com.amaxonia.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.repository.ReportRepository
import kotlinx.coroutines.async
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

            // Load summary and best sellers in parallel
            val summaryDeferred = async { reportRepository.getSummaryStats() }
            val bestSellersDeferred = async { reportRepository.getBestSellers() }

            val summaryResult = summaryDeferred.await()
            val bestSellersResult = bestSellersDeferred.await()

            // Partial success: show whatever data we got
            val errorMessage = listOf(summaryResult, bestSellersResult)
                .firstOrNull { it.isFailure }
                ?.exceptionOrNull()?.message

            _state.update {
                it.copy(
                    isLoading = false,
                    summary = summaryResult.getOrNull() ?: it.summary,
                    bestSellers = bestSellersResult.getOrNull() ?: it.bestSellers,
                    error = if (summaryResult.isFailure && bestSellersResult.isFailure) {
                        errorMessage ?: "Error al cargar datos de reportes"
                    } else null
                )
            }
        }
    }
}
