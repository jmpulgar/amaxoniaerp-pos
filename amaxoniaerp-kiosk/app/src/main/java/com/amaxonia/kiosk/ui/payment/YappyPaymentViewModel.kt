package com.amaxonia.kiosk.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.checkout.YappyCharge
import com.amaxonia.kiosk.domain.checkout.YappyChargeResult
import com.amaxonia.kiosk.domain.checkout.YappyCheckoutUseCase
import com.amaxonia.kiosk.domain.checkout.YappyFailure
import com.amaxonia.kiosk.domain.checkout.YappyOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SECOND_MS = 1000L

enum class YappyErrorKind {
    DECLINED,
    EXPIRED,
    ORDER_EXPIRED,
    NOT_CONFIGURED,
    UPSTREAM,
    NETWORK,
}

sealed interface YappyStep {
    data object Creating : YappyStep

    data class AwaitingScan(
        val qrHash: String,
        val secondsRemaining: Int,
        val totalSeconds: Int,
    ) : YappyStep

    /** Yappy confirmed the payment; it is being registered with the backend. */
    data object Confirming : YappyStep

    data object Cancelling : YappyStep

    data class Success(val info: CompletedOrderInfo) : YappyStep

    data class Error(
        val kind: YappyErrorKind,
        val detail: String = "",
    ) : YappyStep
}

data class YappyUiState(
    val step: YappyStep = YappyStep.Creating,
    val totalAmount: Money? = null,
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    val canChangeMethod: Boolean = true,
) {
    val isPaymentInFlight: Boolean
        get() = step !is YappyStep.Error
}

/**
 * Yappy QR payment: creates the charge for the session quote, shows the QR with a countdown, polls
 * until Yappy confirms/rejects/expires, and registers the payment. Leaving the screen (cancel,
 * change method, idle reset) always cancels the pending charge — or settles it if the customer
 * already paid.
 */
class YappyPaymentViewModel(
    private val yappyCheckout: YappyCheckoutUseCase,
    private val quoteOrder: QuoteOrderUseCase,
    private val checkoutSession: CheckoutSession,
    private val orderGraph: OrderGraph,
    private val dispatch: String,
    canChangeMethod: Boolean,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            YappyUiState(currency = orderGraph.currencyConfig.value, canChangeMethod = canChangeMethod),
        )
    val uiState: StateFlow<YappyUiState> = _uiState.asStateFlow()

    private var flowJob: Job? = null
    private var countdownJob: Job? = null
    private var activeCharge: Pair<KioskQuoteResponse, YappyCharge>? = null

    init {
        generateCode()
    }

    /** Creates a fresh QR (cancelling the previous charge first). */
    fun generateCode() {
        if (isLocked()) return
        countdownJob?.cancel()
        flowJob?.cancel()
        setStep(YappyStep.Creating)
        flowJob =
            viewModelScope.launch {
                val settled = releaseActiveCharge()
                if (settled != null) {
                    onPaid(settled)
                } else {
                    runChargeFlow()
                }
            }
    }

    /** Cancel / change method: cancels the charge, then calls [onLeft] (unless the customer had already paid). */
    fun leave(onLeft: () -> Unit) {
        if (isLocked()) return
        countdownJob?.cancel()
        flowJob?.cancel()
        setStep(YappyStep.Cancelling)
        flowJob =
            viewModelScope.launch {
                val settled = releaseActiveCharge()
                if (settled != null) onPaid(settled) else onLeft()
            }
    }

    private suspend fun runChargeFlow() {
        val quote =
            quoteOrder(orderGraph).getOrElse { error ->
                setStep(YappyStep.Error(YappyErrorKind.NETWORK, error.message.orEmpty()))
                return
            }
        _uiState.update { it.copy(totalAmount = Money.fromString(quote.total)) }
        when (val created = yappyCheckout.createCharge(quote)) {
            is YappyChargeResult.Failed -> {
                if (created.reason == YappyFailure.ORDER_EXPIRED) checkoutSession.invalidateQuote()
                setStep(YappyStep.Error(created.reason.toErrorKind(), created.message))
            }
            is YappyChargeResult.Created -> awaitCharge(quote, created.charge)
        }
    }

    private suspend fun awaitCharge(
        quote: KioskQuoteResponse,
        charge: YappyCharge,
    ) {
        activeCharge = quote to charge
        checkoutSession.setActiveYappyCharge(quote, charge)
        setStep(YappyStep.AwaitingScan(charge.qrHash, charge.expiresInSec, charge.expiresInSec))
        startCountdown(charge.expiresInSec)

        val outcome =
            yappyCheckout.awaitPayment(quote, charge) {
                countdownJob?.cancel()
                forgetActiveCharge()
                setStep(YappyStep.Confirming)
            }
        countdownJob?.cancel()
        forgetActiveCharge()
        when (outcome) {
            is YappyOutcome.Paid -> onPaid(outcome.result)
            is YappyOutcome.Rejected -> setStep(YappyStep.Error(YappyErrorKind.DECLINED, outcome.status.name))
            YappyOutcome.Expired -> setStep(YappyStep.Error(YappyErrorKind.EXPIRED))
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        countdownJob =
            viewModelScope.launch {
                for (remaining in (totalSeconds - 1) downTo 0) {
                    delay(SECOND_MS)
                    _uiState.update {
                        val step = it.step
                        if (step is YappyStep.AwaitingScan) it.copy(step = step.copy(secondsRemaining = remaining)) else it
                    }
                }
            }
    }

    private fun onPaid(result: CheckoutResult) {
        val info = result.toCompletedOrderInfo(orderGraph, dispatch)
        setStep(if (info != null) YappyStep.Success(info) else YappyStep.Error(YappyErrorKind.UPSTREAM))
    }

    /** Cancels the pending charge; returns the registered payment if Yappy had already confirmed it. */
    private suspend fun releaseActiveCharge(): CheckoutResult? {
        val (quote, charge) = activeCharge ?: return null
        forgetActiveCharge()
        return yappyCheckout.cancelOrSettle(quote, charge)
    }

    private fun forgetActiveCharge() {
        activeCharge = null
        checkoutSession.clearActiveYappyCharge()
    }

    private fun isLocked(): Boolean {
        val step = _uiState.value.step
        return step is YappyStep.Confirming || step is YappyStep.Success
    }

    private fun setStep(step: YappyStep) {
        _uiState.update { it.copy(step = step) }
    }

    override fun onCleared() {
        // Left without cancel (idle reset / back stack cleared): the session cancels the charge on the app scope.
        checkoutSession.abortYappyCharge()
        super.onCleared()
    }

    private fun YappyFailure.toErrorKind(): YappyErrorKind =
        when (this) {
            YappyFailure.ORDER_EXPIRED -> YappyErrorKind.ORDER_EXPIRED
            YappyFailure.NOT_CONFIGURED -> YappyErrorKind.NOT_CONFIGURED
            YappyFailure.UPSTREAM -> YappyErrorKind.UPSTREAM
            YappyFailure.NETWORK -> YappyErrorKind.NETWORK
        }
}
