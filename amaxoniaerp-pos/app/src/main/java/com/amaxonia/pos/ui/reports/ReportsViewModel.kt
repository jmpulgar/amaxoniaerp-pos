package com.amaxonia.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val reportRepository: ReportRepository,
    private val invoiceHistoryRepository: InvoiceHistoryRepository? = null,
    private val cajaRepository: CajaRepository? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private companion object {
        const val TRANSACTIONS_LIMIT = 100
        const val DAYS_IN_A_WEEK_MINUS_ONE = 6L
    }

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            if (cajaRepository != null) {
                cajaRepository.activeCaja.collect { caja ->
                    val id = caja?.idCaja
                    val name = caja?.descripcion
                    _state.update {
                        it.copy(
                            activeCajaId = id,
                            activeCajaName = name,
                        )
                    }
                    loadReportData()
                }
            } else {
                loadReportData()
            }
        }
    }

    fun selectPeriod(period: ReportPeriod) {
        if (_state.value.selectedPeriod == period) return
        _state.update { it.copy(selectedPeriod = period) }
        loadReportData()
    }

    fun toggleOnlyActiveCaja() {
        _state.update { it.copy(onlyActiveCaja = !it.onlyActiveCaja) }
        loadReportData()
    }

    fun refresh() {
        loadReportData()
    }

    fun retry() {
        loadReportData()
    }

    private fun loadReportData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val currentState = _state.value
            val filter = buildFilter(
                period = currentState.selectedPeriod,
                onlyActiveCaja = currentState.onlyActiveCaja,
                activeCajaId = currentState.activeCajaId,
                today = todayProvider(),
            )

            // Parallel loading: summary stats, best sellers, and transactions for payment breakdown
            val summaryDeferred = async { reportRepository.getSummaryStats(filter) }
            val bestSellersDeferred = async { reportRepository.getBestSellers() }
            val transactionsDeferred = async {
                invoiceHistoryRepository?.getTransactions(filter = filter, limit = TRANSACTIONS_LIMIT)
            }

            val summaryResult = summaryDeferred.await()
            val bestSellersResult = bestSellersDeferred.await()
            val transactionsResult = transactionsDeferred.await()

            val paymentBreakdown = transactionsResult?.getOrNull()?.let { page ->
                calculatePaymentBreakdown(page.transactions)
            } ?: currentState.paymentBreakdown

            val errorMessage =
                listOfNotNull(summaryResult, bestSellersResult)
                    .firstOrNull { it.isFailure }
                    ?.exceptionOrNull()
                    ?.message

            _state.update {
                it.copy(
                    isLoading = false,
                    summary = summaryResult.getOrNull() ?: it.summary,
                    bestSellers = bestSellersResult.getOrNull() ?: it.bestSellers,
                    paymentBreakdown = paymentBreakdown,
                    error =
                        if (summaryResult.isFailure && bestSellersResult.isFailure) {
                            errorMessage ?: "Error al cargar datos de reportes"
                        } else {
                            null
                        },
                )
            }
        }
    }

    internal fun buildFilter(
        period: ReportPeriod,
        onlyActiveCaja: Boolean,
        activeCajaId: String?,
        today: LocalDate,
    ): InvoiceHistoryFilter {
        val (fechaInicio, fechaFin) = when (period) {
            ReportPeriod.TODAY -> today.toString() to today.toString()
            ReportPeriod.YESTERDAY -> {
                val yesterday = today.minusDays(1)
                yesterday.toString() to yesterday.toString()
            }
            ReportPeriod.THIS_WEEK -> {
                val start = today.minusDays(DAYS_IN_A_WEEK_MINUS_ONE)
                start.toString() to today.toString()
            }
            ReportPeriod.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                start.toString() to today.toString()
            }
        }

        val cajaId = if (onlyActiveCaja) activeCajaId else null

        return InvoiceHistoryFilter(
            fechaInicio = fechaInicio,
            fechaFin = fechaFin,
            cajaId = cajaId,
        )
    }

    internal fun calculatePaymentBreakdown(transactions: List<Transaction>): List<PaymentBreakdownItem> {
        val paid = transactions.filter { it.status == TransactionStatus.PAID }
        if (paid.isEmpty()) return emptyList()

        class Acc(var amount: Double = 0.0, var count: Int = 0)
        val map = mutableMapOf<String, Acc>()

        for (tx in paid) {
            if (tx.paymentMethods.isNotEmpty()) {
                for (pm in tx.paymentMethods) {
                    val rawName = pm.description.ifBlank { tx.formaPago }
                    val name = formatPaymentMethodName(rawName)
                    val acc = map.getOrPut(name) { Acc() }
                    acc.amount += pm.amount
                    acc.count += 1
                }
            } else {
                val name = formatPaymentMethodName(tx.formaPago)
                val acc = map.getOrPut(name) { Acc() }
                acc.amount += tx.amount
                acc.count += 1
            }
        }

        val totalAmount = map.values.sumOf { it.amount }

        return map.entries
            .map { (name, acc) ->
                val pct = if (totalAmount > 0.0) (acc.amount / totalAmount).toFloat() else 0f
                PaymentBreakdownItem(
                    name = name,
                    amount = acc.amount,
                    count = acc.count,
                    percentage = pct,
                )
            }
            .sortedByDescending { it.amount }
    }

    internal fun formatPaymentMethodName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "Otros"
        val lower = trimmed.lowercase(Locale.getDefault())
        return when {
            lower.contains("efectivo") -> "Efectivo"
            lower.contains("debito") || lower.contains("débito") -> "Tarjeta de Débito"
            lower.contains("credito") || lower.contains("crédito") -> "Tarjeta de Crédito"
            lower.contains("tarjeta") -> "Tarjeta"
            lower.contains("transferencia") -> "Transferencia"
            lower.contains("pago movil") || lower.contains("pago móvil") -> "Pago Móvil"
            lower.contains("deposito") || lower.contains("depósito") -> "Depósito"
            lower.contains("cheque") -> "Cheque"
            lower.contains("contado") -> "Contado"
            else -> trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
