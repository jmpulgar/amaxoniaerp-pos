package com.amaxonia.kiosk.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CARD_PAYMENT_TIMEOUT_SECONDS = 90
private const val SECOND_MS = 1000L

enum class PaymentFailure {
    QUOTE_FAILED,
    TIMEOUT,
    CANCELLED,
    TERMINAL_ERROR,
}

sealed interface PaymentStep {
    /** Waiting for the server quote; the countdown has not started yet. */
    data object Quoting : PaymentStep

    data class AwaitingPayment(
        val secondsRemaining: Int,
        val totalSeconds: Int = CARD_PAYMENT_TIMEOUT_SECONDS,
    ) : PaymentStep

    /**
     * "Sin pasarela": no card terminal is connected, so the customer confirms the chosen card
     * method and the payment is registered manually (nothing is charged on the device).
     */
    data object AwaitingManualConfirmation : PaymentStep

    /** Card approved; registering the payment and fiscal invoice. Not cancellable. */
    data object Processing : PaymentStep

    data class Declined(val reason: String) : PaymentStep

    data class Failed(
        val reason: PaymentFailure,
        val message: String = "",
    ) : PaymentStep

    data class Completed(val info: CompletedOrderInfo) : PaymentStep
}

data class PaymentUiState(
    val step: PaymentStep = PaymentStep.Quoting,
    val totalAmount: Money? = null,
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    /** Card method picked on the method screen (VISA, MASTERCARD...); null = generic card. */
    val cardOption: KioskCardOption? = null,
) {
    /** True while the payment runs on its own timeout (the idle timer must not interrupt it). */
    val isPaymentInFlight: Boolean
        get() = step is PaymentStep.Quoting || step is PaymentStep.AwaitingPayment || step is PaymentStep.Processing
}

/**
 * Card payment. Charges exactly the server quote total (never the client-side subtotal); the
 * 90 s countdown starts only once the quote is known and the terminal is waiting for the card.
 *
 * Without a card terminal ([PaymentTerminal.isAvailable] false) and with a company card method
 * chosen ([cardOption]), the payment is registered manually after the customer confirms it
 * ("sin pasarela", for testing until a certified terminal is integrated): no money is charged.
 */
@Suppress("LongParameterList")
class PaymentViewModel(
    private val checkoutUseCase: CheckoutOrderUseCase,
    private val quoteOrder: QuoteOrderUseCase,
    private val paymentTerminal: PaymentTerminal,
    private val checkoutSession: CheckoutSession,
    private val orderGraph: OrderGraph,
    private val dispatch: String,
    private val cardOption: KioskCardOption? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentUiState(currency = orderGraph.currencyConfig.value, cardOption = cardOption))
    private val isManual: Boolean
        get() = !paymentTerminal.isAvailable && cardOption != null
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var checkoutJob: Job? = null
    private var timedOut = false

    init {
        startCheckout()
    }

    fun startCheckout() {
        countdownJob?.cancel()
        checkoutJob?.cancel()
        timedOut = false
        _uiState.update { it.copy(step = PaymentStep.Quoting, currency = orderGraph.currencyConfig.value) }

        checkoutJob =
            viewModelScope.launch {
                val quote =
                    quoteOrder(orderGraph).getOrElse { error ->
                        _uiState.update { it.copy(step = PaymentStep.Failed(PaymentFailure.QUOTE_FAILED, error.message.orEmpty())) }
                        return@launch
                    }
                if (isManual) {
                    _uiState.update {
                        it.copy(totalAmount = Money.fromString(quote.total), step = PaymentStep.AwaitingManualConfirmation)
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        totalAmount = Money.fromString(quote.total),
                        step = PaymentStep.AwaitingPayment(CARD_PAYMENT_TIMEOUT_SECONDS),
                    )
                }
                checkoutSession.setCardPaymentInFlight(true)
                startCountdown()
                val result =
                    checkoutUseCase.payWithCard(
                        quote,
                        onApproved = {
                            countdownJob?.cancel()
                            checkoutSession.setCardPaymentInFlight(false)
                            _uiState.update { it.copy(step = PaymentStep.Processing) }
                        },
                        paymentMethodId = cardOption?.id,
                    )
                countdownJob?.cancel()
                checkoutSession.setCardPaymentInFlight(false)
                if (!timedOut) handleCheckoutResult(result)
            }
    }

    /** "Confirmar pago" on the manual (no terminal) step: registers the payment and the invoice. */
    fun confirmManualPayment() {
        val option = cardOption ?: return
        if (_uiState.value.step != PaymentStep.AwaitingManualConfirmation) return
        _uiState.update { it.copy(step = PaymentStep.Processing) }
        checkoutJob =
            viewModelScope.launch {
                val quote =
                    quoteOrder(orderGraph).getOrElse { error ->
                        _uiState.update { it.copy(step = PaymentStep.Failed(PaymentFailure.QUOTE_FAILED, error.message.orEmpty())) }
                        return@launch
                    }
                handleCheckoutResult(checkoutUseCase.registerManualCardPayment(quote, option))
            }
    }

    private fun handleCheckoutResult(result: CheckoutResult) {
        val step =
            when (result) {
                is CheckoutResult.Success, is CheckoutResult.PaidPendingSync ->
                    result.toCompletedOrderInfo(orderGraph, dispatch)?.let { PaymentStep.Completed(it) }
                        ?: PaymentStep.Failed(PaymentFailure.TERMINAL_ERROR)
                is CheckoutResult.PaymentDeclined -> PaymentStep.Declined(result.reason)
                is CheckoutResult.PaymentCancelled -> PaymentStep.Failed(PaymentFailure.CANCELLED)
                is CheckoutResult.PaymentTerminalError -> PaymentStep.Failed(PaymentFailure.TERMINAL_ERROR, result.message)
            }
        _uiState.update { it.copy(step = step) }
    }

    private fun startCountdown() {
        countdownJob =
            viewModelScope.launch {
                for (remaining in (CARD_PAYMENT_TIMEOUT_SECONDS - 1) downTo 0) {
                    delay(SECOND_MS)
                    _uiState.update {
                        if (it.step is PaymentStep.AwaitingPayment) {
                            it.copy(step = PaymentStep.AwaitingPayment(remaining))
                        } else {
                            it
                        }
                    }
                }
                timedOut = true
                checkoutSession.setCardPaymentInFlight(false)
                paymentTerminal.cancelPayment()
                _uiState.update { it.copy(step = PaymentStep.Failed(PaymentFailure.TIMEOUT)) }
            }
    }

    /** Customer cancelled: stops the terminal and leaves. Ignored once the card was approved. */
    fun cancelPayment(onCancelled: () -> Unit) {
        val step = _uiState.value.step
        if (step is PaymentStep.Processing || step is PaymentStep.Completed) return
        countdownJob?.cancel()
        checkoutJob?.cancel()
        checkoutSession.setCardPaymentInFlight(false)
        viewModelScope.launch {
            paymentTerminal.cancelPayment()
            onCancelled()
        }
    }

    fun retry() {
        startCheckout()
    }

    override fun onCleared() {
        // The screen left (idle reset, navigation): never leave the terminal waiting for a card.
        checkoutSession.abortCardPayment()
        super.onCleared()
    }
}
