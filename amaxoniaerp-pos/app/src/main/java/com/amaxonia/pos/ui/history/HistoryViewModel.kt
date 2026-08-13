package com.amaxonia.pos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val transactionRepository: InvoiceHistoryRepository,
) : ViewModel() {
    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
        const val PAGE_SIZE = 100
    }

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        loadTransactions()
    }

    fun retry() {
        loadTransactions()
    }

    fun loadTransactions() {
        searchJob?.cancel()
        viewModelScope.launch {
            refresh(_state.value.filter)
        }
    }

    fun onSearchChanged(value: String) {
        _state.update { it.copy(filter = it.filter.copy(search = value.takeIf(String::isNotBlank))) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            refresh(_state.value.filter)
        }
    }

    fun onUsuarioChanged(value: String) {
        updateFilter { it.copy(usuario = value.takeIf(String::isNotBlank)) }
    }

    fun onSucursalChanged(value: String) {
        updateFilter { it.copy(sucursalId = value.toIntOrNull()) }
    }

    fun onFechaInicioChanged(value: String) {
        updateFilter { it.copy(fechaInicio = value.takeIf(String::isNotBlank)) }
    }

    fun onFechaFinChanged(value: String) {
        updateFilter { it.copy(fechaFin = value.takeIf(String::isNotBlank)) }
    }

    fun onEstatusChanged(value: String) {
        val estatus = value.split(",").mapNotNull { it.trim().toIntOrNull() }
        updateFilter { it.copy(estatus = estatus) }
    }

    fun applyFilters() {
        loadTransactions()
    }

    fun clearFilters() {
        searchJob?.cancel()
        _state.update { it.copy(filter = InvoiceHistoryFilter()) }
        loadTransactions()
    }

    private fun updateFilter(transform: (InvoiceHistoryFilter) -> InvoiceHistoryFilter) {
        _state.update { it.copy(filter = transform(it.filter)) }
    }

    private suspend fun refresh(filter: InvoiceHistoryFilter) {
        _state.update { it.copy(isLoading = true, error = null) }
        transactionRepository.getTransactions(filter = filter, limit = PAGE_SIZE).fold(
            onSuccess = { page ->
                transactionRepository.getSummary(filter).fold(
                    onSuccess = { summary ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                transactions = page.transactions,
                                totalTransactions = page.total,
                                summary = summary,
                                error = null,
                            )
                        }
                    },
                    onFailure = { exception ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                transactions = page.transactions,
                                totalTransactions = page.total,
                                error = exception.message ?: "Error al cargar resumen de facturas",
                            )
                        }
                    },
                )
            },
            onFailure = { exception ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Error al cargar transacciones",
                    )
                }
            },
        )
    }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }

    fun onTransactionClick(transaction: Transaction) {
        _state.update {
            it.copy(
                selectedTransaction = transaction,
                showDetalleSheet = true,
                isLoadingDetalle = true,
                detalleItems = emptyList(),
                detalleError = null,
            )
        }
        viewModelScope.launch {
            transactionRepository.getInvoiceDetail(transaction.id).fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            isLoadingDetalle = false,
                            detalleItems = response.items,
                            detalleError = null,
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoadingDetalle = false,
                            detalleError = exception.message ?: "Error al cargar detalle",
                        )
                    }
                },
            )
        }
    }

    fun dismissDetalle() {
        _state.update {
            it.copy(
                showDetalleSheet = false,
                selectedTransaction = null,
                detalleItems = emptyList(),
                detalleError = null,
            )
        }
    }
}
