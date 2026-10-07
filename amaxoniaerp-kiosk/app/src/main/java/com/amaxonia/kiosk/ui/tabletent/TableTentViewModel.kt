package com.amaxonia.kiosk.ui.tabletent

import androidx.lifecycle.ViewModel
import com.amaxonia.kiosk.domain.cart.OrderGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_TENT_LENGTH = 3

data class TableTentUiState(
    val tentNumber: String = "",
) {
    val isValid: Boolean
        get() = tentNumber.isNotBlank() && (tentNumber.toIntOrNull() ?: 0) > 0
}

class TableTentViewModel(
    private val orderGraph: OrderGraph,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TableTentUiState())
    val uiState: StateFlow<TableTentUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        if (!digit.isDigit()) return
        _uiState.update { current ->
            if (current.tentNumber.length < MAX_TENT_LENGTH) {
                if (current.tentNumber.isEmpty() && digit == '0') {
                    current
                } else {
                    current.copy(tentNumber = current.tentNumber + digit)
                }
            } else {
                current
            }
        }
    }

    fun onBackspace() {
        _uiState.update { current ->
            current.copy(tentNumber = current.tentNumber.dropLast(1))
        }
    }

    fun onClear() {
        _uiState.update { current ->
            current.copy(tentNumber = "")
        }
    }

    fun confirm(onSuccess: () -> Unit) {
        val number = _uiState.value.tentNumber
        if (number.isNotBlank()) {
            orderGraph.setTableTent(number)
            onSuccess()
        }
    }

    fun skip(onSuccess: () -> Unit) {
        orderGraph.setTableTent(null)
        onSuccess()
    }
}
