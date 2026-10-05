package com.amaxonia.kiosk.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskReceipt
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TIMEOUT_SECONDS = 90
private const val SECOND_MS = 1000L

data class CompletedOrderInfo(
    val orderNumber: String,
    val diningMode: String,
    val tableTent: String?,
    val total: Money,
    val paymentResponse: KioskPaymentResponse,
)

sealed interface PaymentStep {
    data class AwaitingPayment(val secondsRemaining: Int) : PaymentStep

    data object Processing : PaymentStep

    data class Declined(val reason: String) : PaymentStep

    data class Failed(val message: String) : PaymentStep
}

data class PaymentUiState(
    val step: PaymentStep = PaymentStep.AwaitingPayment(TIMEOUT_SECONDS),
    val totalAmount: Money = Money.ZERO,
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
)

class PaymentViewModel(
    private val checkoutUseCase: CheckoutOrderUseCase,
    private val paymentTerminal: PaymentTerminal,
    private val orderGraph: OrderGraph,
    private val onOrderSuccess: (CompletedOrderInfo) -> Unit,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PaymentUiState(
                totalAmount = orderGraph.subtotal,
                currency = orderGraph.currencyConfig.value,
            ),
        )
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var checkoutJob: Job? = null

    init {
        startCheckout()
    }

    fun startCheckout() {
        countdownJob?.cancel()
        checkoutJob?.cancel()

        _uiState.update {
            it.copy(
                step = PaymentStep.AwaitingPayment(TIMEOUT_SECONDS),
                totalAmount = orderGraph.subtotal,
                currency = orderGraph.currencyConfig.value,
            )
        }

        startCountdown()

        checkoutJob =
            viewModelScope.launch {
                val result = checkoutUseCase.execute(orderGraph)
                countdownJob?.cancel()
                handleCheckoutResult(result)
            }
    }

    private fun handleCheckoutResult(result: CheckoutResult) {
        when (result) {
            is CheckoutResult.Success -> handleSuccess(result)
            is CheckoutResult.PaidPendingSync -> handlePaidPendingSync(result)
            is CheckoutResult.PaymentDeclined -> {
                _uiState.update { it.copy(step = PaymentStep.Declined(result.reason)) }
            }
            is CheckoutResult.PaymentCancelled -> {
                _uiState.update { it.copy(step = PaymentStep.Failed("Operación cancelada")) }
            }
            is CheckoutResult.PaymentTerminalError -> {
                _uiState.update { it.copy(step = PaymentStep.Failed(result.message)) }
            }
            is CheckoutResult.QuoteFailed -> {
                _uiState.update { it.copy(step = PaymentStep.Failed(result.message)) }
            }
        }
    }

    private fun handleSuccess(result: CheckoutResult.Success) {
        _uiState.update { it.copy(step = PaymentStep.Processing) }
        val info =
            CompletedOrderInfo(
                orderNumber = result.paymentResponse.orderNumber,
                diningMode = orderGraph.diningMode.value,
                tableTent = orderGraph.tableTent.value,
                total = Money.fromString(result.quote.total),
                paymentResponse = result.paymentResponse,
            )
        onOrderSuccess(info)
    }

    private fun handlePaidPendingSync(result: CheckoutResult.PaidPendingSync) {
        _uiState.update { it.copy(step = PaymentStep.Processing) }
        val info =
            CompletedOrderInfo(
                orderNumber = result.quote.formattedOrderNumber,
                diningMode = orderGraph.diningMode.value,
                tableTent = orderGraph.tableTent.value,
                total = Money.fromString(result.quote.total),
                paymentResponse =
                    KioskPaymentResponse(
                        orderNumber = result.quote.formattedOrderNumber,
                        status = "PAID_PENDING_INVOICE",
                        dispatch = "RETIRO_MOSTRADOR",
                        receipt =
                            KioskReceipt(
                                companyName = "Amaxonia Kiosk",
                                orderNumber = result.quote.formattedOrderNumber,
                                diningMode = orderGraph.diningMode.value,
                                tableTent = orderGraph.tableTent.value,
                                customerName = orderGraph.customerName.value,
                                customerId = orderGraph.customerId.value,
                                date = "",
                                lines = emptyList(),
                                subtotal = result.quote.subtotal,
                                tax = result.quote.tax,
                                total = result.quote.total,
                                paymentBrand = result.payment.brand,
                                paymentLast4 = result.payment.last4,
                                paymentAuthCode = result.payment.authCode,
                                paymentReference = result.payment.reference,
                            ),
                    ),
            )
        onOrderSuccess(info)
    }

    private fun startCountdown() {
        countdownJob =
            viewModelScope.launch {
                for (remaining in (TIMEOUT_SECONDS - 1) downTo 0) {
                    delay(SECOND_MS)
                    _uiState.update {
                        if (it.step is PaymentStep.AwaitingPayment) {
                            it.copy(step = PaymentStep.AwaitingPayment(remaining))
                        } else {
                            it
                        }
                    }
                }
                paymentTerminal.cancelPayment()
                _uiState.update {
                    it.copy(step = PaymentStep.Failed("Tiempo de espera agotado. Por favor reintenta."))
                }
            }
    }

    fun cancelPayment(onCancelled: () -> Unit) {
        countdownJob?.cancel()
        checkoutJob?.cancel()
        viewModelScope.launch {
            paymentTerminal.cancelPayment()
            onCancelled()
        }
    }

    fun retry() {
        startCheckout()
    }
}
