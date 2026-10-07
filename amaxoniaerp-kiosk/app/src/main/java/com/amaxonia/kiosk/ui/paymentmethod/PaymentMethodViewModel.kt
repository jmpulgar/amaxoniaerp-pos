package com.amaxonia.kiosk.ui.paymentmethod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PaymentMethodStep {
    /** Spec §3 "Quote" step: the server validates prices/stock and fixes the total. */
    data object Quoting : PaymentMethodStep

    data object Ready : PaymentMethodStep

    data class QuoteFailed(val message: String) : PaymentMethodStep

    data object NoMethodsAvailable : PaymentMethodStep
}

data class PaymentMethodUiState(
    val step: PaymentMethodStep = PaymentMethodStep.Quoting,
    val total: Money? = null,
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    val methods: List<PaymentMethod> = emptyList(),
    /** Company card methods (VISA, MASTERCARD...): CARD shows one tile per option instead of one generic tile. */
    val cardOptions: List<KioskCardOption> = emptyList(),
) {
    /**
     * With a single usable choice the selection screen is skipped right after the quote. CARD with
     * several card options is not a single choice: the customer still picks the card type.
     */
    val autoSelectedMethod: PaymentMethod?
        get() =
            if (step == PaymentMethodStep.Ready) {
                methods.singleOrNull()?.takeUnless { it == PaymentMethod.CARD && cardOptions.size > 1 }
            } else {
                null
            }

    /** Card option implied by an auto-selected CARD (the only one listed), else null. */
    val autoSelectedCardOption: KioskCardOption?
        get() = if (autoSelectedMethod == PaymentMethod.CARD) cardOptions.singleOrNull() else null
}

class PaymentMethodViewModel(
    private val quoteOrder: QuoteOrderUseCase,
    private val orderGraph: OrderGraph,
    methods: List<PaymentMethod>,
    cardOptions: List<KioskCardOption> = emptyList(),
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PaymentMethodUiState(
                methods = methods,
                cardOptions = if (PaymentMethod.CARD in methods) cardOptions else emptyList(),
                currency = orderGraph.currencyConfig.value,
            ),
        )
    val uiState: StateFlow<PaymentMethodUiState> = _uiState.asStateFlow()

    private var quoteJob: Job? = null

    init {
        ensureQuote()
    }

    /**
     * Makes sure a valid quote exists. Called on entry and whenever the screen is shown again
     * (e.g. back from a payment whose quote expired); a still-valid quote is reused, not re-quoted.
     */
    fun ensureQuote() {
        if (_uiState.value.methods.isEmpty()) {
            _uiState.update { it.copy(step = PaymentMethodStep.NoMethodsAvailable) }
            return
        }
        if (quoteJob?.isActive == true) return
        if (_uiState.value.step != PaymentMethodStep.Ready) {
            _uiState.update { it.copy(step = PaymentMethodStep.Quoting) }
        }
        quoteJob =
            viewModelScope.launch {
                quoteOrder(orderGraph).fold(
                    onSuccess = { quote ->
                        _uiState.update {
                            it.copy(
                                step = PaymentMethodStep.Ready,
                                total = Money.fromString(quote.total),
                                currency = orderGraph.currencyConfig.value,
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(step = PaymentMethodStep.QuoteFailed(error.message.orEmpty())) }
                    },
                )
            }
    }
}
