package com.amaxonia.pos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val transactionRepository: InvoiceHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        loadTransactions()
    }

    fun retry() {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            transactionRepository.getAllTransactions().fold(
                onSuccess = { transactions ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            transactions = transactions,
                            error = null,
                        )
                    }
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
