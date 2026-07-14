package com.amaxonia.pos.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsInput
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.ExecutePaymentFlowInput
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowEvent
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowExecutor
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowResult
import com.amaxonia.pos.domain.usecase.payment.PaymentMethodsResult
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaymentViewModel(
    private val loadPaymentContext: LoadPaymentContextUseCase,
    private val loadPaymentCountry: LoadPaymentCountryUseCase,
    private val validatePayment: ValidatePaymentUseCase,
    private val buildPaymentDetails: BuildPaymentDetailsUseCase,
    private val executePaymentFlow: PaymentFlowExecutor,
) : ViewModel() {
    private var countryCode: String = DEFAULT_COUNTRY_CODE

    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PaymentUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PaymentUiEffect> = _effects.asSharedFlow()

    init {
        initializePaymentContext()
        viewModelScope.launch { countryCode = loadPaymentCountry() }
    }

    fun onAction(action: PaymentUiAction) {
        when (action) {
            is PaymentUiAction.SetTotalAmount -> {
                val normalized = Money.fromDouble(action.amount).toDouble()
                _state.update { it.copy(totalAmount = normalized) }
            }
            is PaymentUiAction.KeyPadInput -> _state.update { state -> state.withKeyPadInput(action.key) }
            PaymentUiAction.SetExactAmount ->
                _state.update { it.copy(tenderedAmountInput = it.totalAmountText, showInsufficientReminder = false) }
            is PaymentUiAction.SelectMethod ->
                _state.update { it.copy(selectedMethod = action.method, showInsufficientReminder = false) }
            is PaymentUiAction.SetExactNonCashAmount ->
                _state.update { current ->
                    current.copy(
                        nonCashAmountsInput = mapOf(action.paymentMethodId to current.totalAmountText),
                        showInsufficientReminder = false,
                    )
                }
            is PaymentUiAction.SetNonCashAmount -> {
                val normalized = Money.normalizeInput(action.amount)
                _state.update { current ->
                    current.copy(
                        nonCashAmountsInput =
                            current.nonCashAmountsInput.toMutableMap().apply {
                                if (normalized.isBlank()) remove(action.paymentMethodId) else put(action.paymentMethodId, normalized)
                            },
                        showInsufficientReminder = false,
                    )
                }
            }
            PaymentUiAction.ProcessPayment -> processPayment()
            PaymentUiAction.ClearPaymentError -> _state.update { it.copy(paymentError = null) }
            PaymentUiAction.ClearReceiptPrintMessage -> _state.update { it.copy(receiptPrintMessage = null) }
            PaymentUiAction.ClearSuccessPayload -> _state.update { it.copy(successPayload = null) }
        }
    }

    private fun processPayment() {
        val currentState = _state.value
        when {
            currentState.isProcessingPayment -> Unit
            validatePayment.validateAmount(currentState.isPaymentEnough) != null -> showInsufficientPaymentReminder()
            else -> {
                val details =
                    buildPaymentDetails(
                        BuildPaymentDetailsInput(
                            isCash = currentState.selectedMethod == PaymentMethod.CASH,
                            totalAmount = currentState.totalAmountMoney,
                            cashMethods = currentState.formasPagoEfectivo,
                            nonCashMethods = currentState.formasPagoTarjetaOtro,
                            allMethods = currentState.formasPago,
                            nonCashAmountsInput = currentState.nonCashAmountsInput,
                        ),
                    )
                val failure = validatePayment.validatePaymentMethods(details.payload.detalle.size)
                if (failure != null) {
                    _state.update { it.copy(paymentError = failure.message) }
                } else {
                    beginProcessing(details)
                    viewModelScope.launch {
                        val result =
                            executePaymentFlow(
                                input = currentState.toExecutionInput(countryCode, details),
                                onEvent = ::handlePaymentFlowEvent,
                            )
                        applyPaymentResult(result)
                    }
                }
            }
        }
    }

    private fun beginProcessing(details: com.amaxonia.pos.domain.usecase.payment.PaymentDetails) {
        _state.update {
            it.copy(
                lastFormapagoDetalle = details.payload,
                isProcessingPayment = true,
                paymentError = null,
                gatewayStatusMessage = "Validando cobro...",
            )
        }
    }

    private fun applyPaymentResult(result: PaymentFlowResult) {
        when (result) {
            is PaymentFlowResult.Failure ->
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        gatewayStatusMessage = null,
                        paymentError = result.message,
                    )
                }
            is PaymentFlowResult.Success -> {
                SafeLog.d(TAG, "Payment flow completed")
                _state.update {
                    it.copy(
                        isSuccess = true,
                        isProcessingPayment = false,
                        paymentError = null,
                        gatewayStatusMessage = null,
                        receiptPrintMessage = result.receiptPrintMessage,
                        successPayload = result.payload,
                    )
                }
            }
        }
    }

    private fun initializePaymentContext() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingFormasPago = true, formasPagoError = null, paymentError = null) }
            val context = loadPaymentContext()
            when (val methods = context.methods) {
                is PaymentMethodsResult.Loaded ->
                    _state.update {
                        it.copy(
                            formasPago = methods.methods,
                            isLoadingFormasPago = false,
                            formasPagoError = null,
                        )
                    }
                is PaymentMethodsResult.Failed ->
                    _state.update {
                        it.copy(
                            formasPago = emptyList(),
                            isLoadingFormasPago = false,
                            formasPagoError = methods.message,
                        )
                    }
            }
            _state.update {
                it.copy(
                    tasa = context.currency.exchangeRate,
                    abrMonedaSecundaria = context.currency.secondaryCurrency,
                    isMultiCurrency = context.currency.isMultiCurrency,
                )
            }
        }
    }

    private fun showInsufficientPaymentReminder() {
        _state.update { it.copy(showInsufficientReminder = true) }
        viewModelScope.launch {
            delay(INSUFFICIENT_REMINDER_MILLIS)
            _state.update { state ->
                if (state.isPaymentEnough) state else state.copy(showInsufficientReminder = false)
            }
        }
    }

    private suspend fun handlePaymentFlowEvent(event: PaymentFlowEvent) {
        when (event) {
            is PaymentFlowEvent.Progress -> _state.update { it.copy(gatewayStatusMessage = event.message) }
            is PaymentFlowEvent.LaunchGateway -> _effects.emit(PaymentUiEffect.LaunchGateway(event.payload))
            PaymentFlowEvent.FiscalConfirmationFailed -> SafeLog.w(TAG, "Fiscal receipt confirmation failed")
        }
    }

    private fun PaymentState.toExecutionInput(
        countryCode: String,
        details: com.amaxonia.pos.domain.usecase.payment.PaymentDetails,
    ): ExecutePaymentFlowInput =
        ExecutePaymentFlowInput(
            countryCode = countryCode,
            paymentDetails = details,
            totalAmount = totalAmountMoney,
            tenderedAmount = tenderedAmountMoney,
            changeDue = changeDue,
            totalAmountBs = totalAmountBs,
            changeDueBs = changeDueBs,
            exchangeRate = tasa,
            secondaryCurrency = abrMonedaSecundaria,
            isMultiCurrency = isMultiCurrency,
            availableMethods = formasPago,
        )

    private fun PaymentState.withKeyPadInput(key: String): PaymentState =
        when (key) {
            "C" -> copy(tenderedAmountInput = "0", showInsufficientReminder = false)
            "BACK" -> {
                val updated = if (tenderedAmountInput.length > 1) tenderedAmountInput.dropLast(1) else "0"
                copy(
                    tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                    showInsufficientReminder = false,
                )
            }
            "00" -> {
                val updated = if (tenderedAmountInput == "0") "0" else tenderedAmountInput + "00"
                copy(
                    tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                    showInsufficientReminder = false,
                )
            }
            else -> {
                val updated = if (tenderedAmountInput == "0") key else tenderedAmountInput + key
                copy(
                    tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                    showInsufficientReminder = false,
                )
            }
        }

    private companion object {
        const val TAG = "PaymentVM"
        const val DEFAULT_COUNTRY_CODE = "VE"
        const val INSUFFICIENT_REMINDER_MILLIS = 1_400L
    }
}
