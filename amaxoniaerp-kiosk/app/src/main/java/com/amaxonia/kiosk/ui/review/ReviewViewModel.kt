package com.amaxonia.kiosk.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.domain.cart.CartLine
import com.amaxonia.kiosk.domain.cart.OrderGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class ReviewUiState(
    val lines: List<CartLine> = emptyList(),
    val diningMode: String = "COMER_AQUI",
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
) {
    val totalItemCount: Int
        get() = lines.sumOf { it.quantity }

    val subtotal: Money
        get() = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }

    val isEmpty: Boolean
        get() = lines.isEmpty()
}

class ReviewViewModel(
    val orderGraph: OrderGraph,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            ReviewUiState(
                lines = orderGraph.lines.value,
                diningMode = orderGraph.diningMode.value,
                currency = orderGraph.currencyConfig.value,
            ),
        )
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        combine(
            orderGraph.lines,
            orderGraph.diningMode,
            orderGraph.currencyConfig,
        ) { lines, mode, currency ->
            ReviewUiState(
                lines = lines,
                diningMode = mode,
                currency = currency,
            )
        }
            .onEach { updated ->
                _uiState.update { updated }
            }
            .launchIn(viewModelScope)
    }

    fun incrementQuantity(lineId: String) {
        val line = orderGraph.lines.value.firstOrNull { it.id == lineId } ?: return
        orderGraph.updateQuantity(lineId, line.quantity + 1)
        syncState()
    }

    fun decrementQuantity(lineId: String) {
        val line = orderGraph.lines.value.firstOrNull { it.id == lineId } ?: return
        orderGraph.updateQuantity(lineId, line.quantity - 1)
        syncState()
    }

    fun removeLine(lineId: String) {
        orderGraph.removeLine(lineId)
        syncState()
    }

    fun setDiningMode(mode: String) {
        orderGraph.setDiningMode(mode)
        syncState()
    }

    fun clearCart() {
        orderGraph.reset()
        syncState()
    }

    private fun syncState() {
        _uiState.update {
            it.copy(
                lines = orderGraph.lines.value,
                diningMode = orderGraph.diningMode.value,
                currency = orderGraph.currencyConfig.value,
            )
        }
    }
}
