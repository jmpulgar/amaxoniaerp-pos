package com.amaxonia.pos.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.repository.TableAccountPaymentReader
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsInput
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentExecutionContext
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowEvent
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowResult
import com.amaxonia.pos.domain.usecase.payment.PaymentIntent
import com.amaxonia.pos.domain.usecase.payment.PaymentMethodsResult
import com.amaxonia.pos.domain.usecase.payment.PaymentOperation
import com.amaxonia.pos.domain.usecase.payment.PaymentOperationRequest
import com.amaxonia.pos.domain.usecase.payment.PaymentSource
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class PaymentViewModel(
    private val loadPaymentContext: LoadPaymentContextUseCase,
    private val loadPaymentCountry: LoadPaymentCountryUseCase,
    private val validatePayment: ValidatePaymentUseCase,
    private val buildPaymentDetails: BuildPaymentDetailsUseCase,
    private val paymentOperation: PaymentOperation,
    private val selectedClient: StateFlow<Client?> = MutableStateFlow(null),
    private val tableAccountPaymentReader: TableAccountPaymentReader? = null,
    private val cartFinancialSnapshot: StateFlow<SaleFinancialSnapshot?> = MutableStateFlow(null),
) : ViewModel() {
    private var countryCode: String = DEFAULT_COUNTRY_CODE

    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PaymentUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PaymentUiEffect> = _effects.asSharedFlow()

    init {
        initializePaymentContext()
        viewModelScope.launch {
            val country = loadPaymentCountry()
            countryCode = country
            _state.update { it.copy(taxLabel = taxLabelFor(country)) }
        }
        // Cart sales: subscribe to the canonical financial snapshot so the UI shows
        // exactly what the backend will invoice. Table-account sales override this
        // via the tableAccountPaymentReader below.
        viewModelScope.launch {
            cartFinancialSnapshot.collect { snapshot -> _state.update { it.copy(financialSnapshot = snapshot) } }
        }
        viewModelScope.launch {
            tableAccountPaymentReader?.current?.collect { payment ->
                val snapshot = payment?.financialSnapshot
                if (snapshot != null) {
                    _state.update { it.copy(financialSnapshot = snapshot) }
                }
            }
        }
        viewModelScope.launch {
            selectedClient.collect { client ->
                val canUseCredit = client?.permiteCredito == true
                _state.update { current ->
                    if (!canUseCredit && current.cxcAssignedMoney > Money.ZERO) {
                        // Client no longer allows credit → drop any CXC amounts so the
                        // derived condition reverts to CONTADO automatically.
                        current.copy(
                            canUseCredit = canUseCredit,
                            nonCashAmountsInput = current.withoutCxcAmounts(),
                        )
                    } else {
                        current.copy(canUseCredit = canUseCredit)
                    }
                }
            }
        }
    }

    fun onAction(action: PaymentUiAction) {
        when (action) {
            is PaymentUiAction.SetTotalAmount -> {
                val normalized = Money.fromDouble(action.amount).toDouble()
                _state.update { it.copy(totalAmount = normalized) }
            }
            is PaymentUiAction.KeyPadInput -> _state.update { state -> state.withKeyPadInput(action.key) }
            PaymentUiAction.SetExactAmount ->
                _state.update {
                    val remaining = (it.totalAmountMoney - it.nonCashAssignedMoney).coerceAtLeastZero()
                    it.copy(tenderedAmountInput = Money.format(remaining), showInsufficientReminder = false)
                }
            is PaymentUiAction.SelectMethod ->
                _state.update { it.copy(selectedMethod = action.method, showInsufficientReminder = false) }
            is PaymentUiAction.SetExactNonCashAmount -> setExactNonCashAmount(action.paymentMethodId)
            is PaymentUiAction.SetNonCashAmount -> setNonCashAmount(action.paymentMethodId, action.amount)
            PaymentUiAction.ProcessPayment -> processPayment()
            PaymentUiAction.ClearPaymentError -> _state.update { it.copy(paymentError = null) }
            PaymentUiAction.ClearReceiptPrintMessage -> _state.update { it.copy(receiptPrintMessage = null) }
            PaymentUiAction.ClearSuccessPayload -> _state.update { it.copy(successPayload = null) }
            PaymentUiAction.DismissDuplicateInvoice -> _state.update { it.copy(duplicateInvoice = null) }
        }
    }

    /**
     * Assigns the remaining balance to a non-cash method. CXC is silently rejected
     * when the selected client does not allow credit, so the derived condition stays CONTADO.
     */
    private fun setExactNonCashAmount(paymentMethodId: Int) {
        _state.update { current ->
            if (!current.canUseCredit && current.isCxcPaymentMethod(paymentMethodId)) {
                return@update current.copy(nonCashAmountsInput = current.withoutCxcAmounts())
            }
            val assignedToOtherMethods =
                current.nonCashAmountsInput
                    .filterKeys { it != paymentMethodId }
                    .values
                    .fold(Money.ZERO) { accumulated, amount -> accumulated + Money.parse(amount) }
            val remaining =
                (current.totalAmountMoney - current.tenderedAmountMoney - assignedToOtherMethods)
                    .coerceAtLeastZero()
            current.copy(
                nonCashAmountsInput =
                    current.nonCashAmountsInput.toMutableMap().apply {
                        if (remaining > Money.ZERO) {
                            put(paymentMethodId, Money.format(remaining))
                        } else {
                            remove(paymentMethodId)
                        }
                    },
                showInsufficientReminder = false,
            )
        }
    }

    private fun setNonCashAmount(
        paymentMethodId: Int,
        amount: String,
    ) {
        val normalized = Money.normalizeInput(amount)
        _state.update { current ->
            if (!current.canUseCredit && current.isCxcPaymentMethod(paymentMethodId)) {
                return@update current.copy(nonCashAmountsInput = current.withoutCxcAmounts())
            }
            current.copy(
                nonCashAmountsInput =
                    current.nonCashAmountsInput.toMutableMap().apply {
                        if (normalized.isBlank()) remove(paymentMethodId) else put(paymentMethodId, normalized)
                    },
                showInsufficientReminder = false,
            )
        }
    }

    private fun processPayment() {
        val currentState = _state.value
        when {
            currentState.isProcessingPayment -> Unit
            // The condition is auto-derived; the validator only enforces the credit
            // permission so a stale CXC entry cannot slip through if the client lost credit.
            validatePayment.validatePaymentCondition(currentState.paymentCondition, currentState.canUseCredit) != null ->
                _state.update {
                    it.copy(
                        nonCashAmountsInput = it.withoutCxcAmounts(),
                        paymentError = "El cliente seleccionado no permite ventas a crédito",
                    )
                }
            validatePayment.validateAmount(currentState.isPaymentEnough) != null -> showInsufficientPaymentReminder()
            else -> {
                val details =
                    buildPaymentDetails(
                        BuildPaymentDetailsInput(
                            isCash = currentState.selectedMethod == PaymentMethod.CASH,
                            totalAmount = currentState.totalAmountMoney,
                            cashTenderedAmount = currentState.tenderedAmountMoney,
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
                        val request = currentState.toPaymentOperationRequest(details)
                        val result = paymentOperation.execute(request, ::handlePaymentFlowEvent)
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
            is PaymentFlowResult.DuplicateInvoice ->
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        gatewayStatusMessage = null,
                        duplicateInvoice =
                            DuplicateInvoicePrompt(
                                clientCorrelationId = result.clientCorrelationId,
                                reason = result.reason,
                            ),
                    )
                }
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

    private fun PaymentState.toPaymentOperationRequest(
        details: com.amaxonia.pos.domain.usecase.payment.PaymentDetails,
    ): PaymentOperationRequest {
        val tablePayment = tableAccountPaymentReader?.current?.value
        val source =
            if (tablePayment == null) {
                PaymentSource.CurrentCart(financialSnapshot)
            } else {
                PaymentSource.TableAccount(tablePayment)
            }
        return PaymentOperationRequest(
            payment =
                PaymentIntent(
                    details = details,
                    totalAmount = totalAmountMoney,
                    tenderedAmount = tenderedAmountMoney,
                    changeDue = changeDueMoney,
                    condition = paymentCondition,
                ),
            source = source,
            context =
                PaymentExecutionContext(
                    countryCode = countryCode,
                    availableMethods = formasPago,
                    exchangeRate = tasa,
                    secondaryCurrency = abrMonedaSecundaria,
                    isMultiCurrency = isMultiCurrency,
                ),
        )
    }

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

    private fun PaymentState.withoutCxcAmounts(): Map<Int, String> =
        nonCashAmountsInput.filterKeys { paymentMethodId -> !isCxcPaymentMethod(paymentMethodId) }

    private fun PaymentState.isCxcPaymentMethod(paymentMethodId: Int): Boolean =
        formasPago
            .firstOrNull { it.idFormaPago == paymentMethodId }
            ?.siglas
            ?.trim()
            ?.equals("CXC", ignoreCase = true) == true

    /**
     * Country-aware tax label. The POS surfaces this in the financial breakdown so
     * Venezuelians see "IVA" and Panamanians see "ITBMS" while the multi-country
     * fallback remains "Impuesto". Does not break compatibility: the underlying
     * financial values are unchanged.
     */
    private fun taxLabelFor(country: String): String =
        when (country.uppercase()) {
            "VE" -> "IVA"
            "PA" -> "ITBMS"
            else -> "Impuesto"
        }

    private companion object {
        const val TAG = "PaymentVM"
        const val DEFAULT_COUNTRY_CODE = "VE"
        const val INSUFFICIENT_REMINDER_MILLIS = 1_400L
    }
}
