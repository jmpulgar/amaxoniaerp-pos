package com.amaxonia.pos.ui.cobranza

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.cobranza.CobranzaPendingInvoiceDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaResponseDto
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.ActiveCajaContext
import com.amaxonia.pos.domain.repository.CobranzaRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaCalculator
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaInvoiceSelection
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaPaymentLine
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaSubmission
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaTotals
import com.amaxonia.pos.domain.usecase.cobranza.SubmitCobranzaUseCase
import com.amaxonia.pos.domain.usecase.cobranza.cobranzaFormasPago
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Cliente al que se le cobra. */
data class CobranzaCliente(
    val id: String,
    val nombre: String,
)

/** Línea de pago editable (el monto se conserva como texto mientras se escribe). */
data class PaymentInput(
    val id: String = UUID.randomUUID().toString(),
    val formaPago: FormaPago,
    val amountInput: String = "",
    val referencia: String = "",
)

data class CobranzaState(
    val cliente: CobranzaCliente? = null,
    val isLoadingInvoices: Boolean = false,
    val invoices: List<CobranzaPendingInvoiceDto> = emptyList(),
    val totalPendiente: Double = 0.0,
    /** idFactura → monto a cobrar (texto). Solo las seleccionadas. */
    val selectedAmounts: Map<String, String> = emptyMap(),
    val formasPago: List<FormaPago> = emptyList(),
    val payments: List<PaymentInput> = emptyList(),
    val observacion: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: CreateCobranzaResponseDto? = null,
) {
    val selections: List<CobranzaInvoiceSelection>
        get() =
            invoices.mapNotNull { invoice ->
                selectedAmounts[invoice.idFactura]?.let { CobranzaInvoiceSelection(invoice, Money.parse(it)) }
            }

    val paymentLines: List<CobranzaPaymentLine>
        get() = payments.map { CobranzaPaymentLine(it.id, it.formaPago, Money.parse(it.amountInput), it.referencia) }

    val totals: CobranzaTotals get() = CobranzaCalculator.totals(selections, paymentLines)
}

/** Acciones sobre las líneas de forma de pago. */
sealed interface CobranzaPaymentAction : CobranzaAction

sealed interface CobranzaAction {
    data class SelectCliente(
        val cliente: CobranzaCliente,
    ) : CobranzaAction

    data object Reload : CobranzaAction

    data class ToggleInvoice(
        val idFactura: String,
    ) : CobranzaAction

    data class SetInvoiceAmount(
        val idFactura: String,
        val amount: String,
    ) : CobranzaAction

    data object ToggleAll : CobranzaAction

    data class AddPayment(
        val formaPago: FormaPago,
    ) : CobranzaPaymentAction

    data class SetPaymentAmount(
        val lineId: String,
        val amount: String,
    ) : CobranzaPaymentAction

    data class SetPaymentReference(
        val lineId: String,
        val referencia: String,
    ) : CobranzaPaymentAction

    data class RemovePayment(
        val lineId: String,
    ) : CobranzaPaymentAction

    data class SetObservacion(
        val text: String,
    ) : CobranzaAction

    data object Submit : CobranzaAction

    data object DismissError : CobranzaAction

    data object DismissResult : CobranzaAction
}

/**
 * Cobro de facturas pendientes de un cliente: selección de facturas (total o abono parcial)
 * y una o varias formas de pago. El excedente en efectivo se registra como vuelto.
 */
class CobranzaViewModel(
    private val cobranzaRepository: CobranzaRepository,
    private val formaPagoRepository: FormaPagoRepository,
    private val cajaContext: ActiveCajaContext,
    private val submitCobranza: SubmitCobranzaUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CobranzaState())
    val state: StateFlow<CobranzaState> = mutableState.asStateFlow()

    /** Clave de idempotencia del cobro en curso; se renueva solo tras un cobro exitoso. */
    private var idCobranza = UUID.randomUUID().toString()

    init {
        loadFormasPago()
    }

    fun onAction(action: CobranzaAction) {
        when (action) {
            is CobranzaAction.SelectCliente -> selectCliente(action.cliente)
            CobranzaAction.Reload -> mutableState.value.cliente?.let { loadInvoices(it) }
            is CobranzaAction.ToggleInvoice -> toggleInvoice(action.idFactura)
            is CobranzaAction.SetInvoiceAmount -> updateSelection { it + (action.idFactura to action.amount) }
            CobranzaAction.ToggleAll -> toggleAll()
            is CobranzaAction.SetObservacion -> mutableState.update { it.copy(observacion = action.text) }
            CobranzaAction.Submit -> submit()
            CobranzaAction.DismissError -> mutableState.update { it.copy(error = null) }
            CobranzaAction.DismissResult -> mutableState.update { it.copy(result = null) }
            is CobranzaPaymentAction -> onPaymentAction(action)
        }
    }

    private fun onPaymentAction(action: CobranzaPaymentAction) {
        when (action) {
            is CobranzaAction.AddPayment -> addPayment(action.formaPago)
            is CobranzaAction.SetPaymentAmount -> updatePayment(action.lineId) { it.copy(amountInput = action.amount) }
            is CobranzaAction.SetPaymentReference -> updatePayment(action.lineId) { it.copy(referencia = action.referencia) }
            is CobranzaAction.RemovePayment ->
                mutableState.update { state -> state.copy(payments = state.payments.filterNot { it.id == action.lineId }) }
        }
    }

    private fun loadFormasPago() {
        viewModelScope.launch {
            formaPagoRepository
                .getFormasPago(cajaContext.activeCaja()?.idCaja)
                .onSuccess { formas -> mutableState.update { it.copy(formasPago = cobranzaFormasPago(formas)) } }
                .onFailure { error -> mutableState.update { it.copy(error = error.message ?: "No se pudieron cargar las formas de pago") } }
        }
    }

    private fun selectCliente(cliente: CobranzaCliente) {
        idCobranza = UUID.randomUUID().toString()
        mutableState.update {
            it.copy(cliente = cliente, invoices = emptyList(), selectedAmounts = emptyMap(), payments = emptyList(), result = null)
        }
        loadInvoices(cliente)
    }

    private fun loadInvoices(cliente: CobranzaCliente) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingInvoices = true, error = null) }
            cobranzaRepository
                .pendientes(cliente.id)
                .onSuccess { response ->
                    mutableState.update { state ->
                        val ids = response.data.map { it.idFactura }.toSet()
                        state.copy(
                            isLoadingInvoices = false,
                            invoices = response.data,
                            totalPendiente = response.totalPendiente,
                            selectedAmounts = state.selectedAmounts.filterKeys { it in ids },
                        )
                    }
                }.onFailure { error ->
                    mutableState.update {
                        it.copy(isLoadingInvoices = false, error = error.message ?: "No se pudieron cargar las facturas")
                    }
                }
        }
    }

    private fun toggleInvoice(idFactura: String) {
        val invoice = mutableState.value.invoices.firstOrNull { it.idFactura == idFactura } ?: return
        updateSelection { selected ->
            if (idFactura in selected) selected - idFactura else selected + (idFactura to Money.fromDouble(invoice.saldo).toPlainString())
        }
    }

    private fun toggleAll() {
        val invoices = mutableState.value.invoices
        updateSelection { selected ->
            if (selected.size == invoices.size) {
                emptyMap()
            } else {
                invoices.associate { it.idFactura to (selected[it.idFactura] ?: Money.fromDouble(it.saldo).toPlainString()) }
            }
        }
    }

    private fun updateSelection(transform: (Map<String, String>) -> Map<String, String>) {
        mutableState.update { it.copy(selectedAmounts = transform(it.selectedAmounts)) }
    }

    /** Agrega una forma de pago precargada con lo que falta por cubrir. */
    private fun addPayment(formaPago: FormaPago) {
        mutableState.update { state ->
            val faltante = state.totals.faltante
            val amount = if (faltante > Money.ZERO) faltante.toPlainString() else ""
            state.copy(payments = state.payments + PaymentInput(formaPago = formaPago, amountInput = amount))
        }
    }

    private fun updatePayment(
        lineId: String,
        transform: (PaymentInput) -> PaymentInput,
    ) {
        mutableState.update { state -> state.copy(payments = state.payments.map { if (it.id == lineId) transform(it) else it }) }
    }

    private fun submit() {
        val state = mutableState.value
        val cliente = state.cliente
        val validationError = state.totals.error
        if (cliente == null || state.isSubmitting || validationError != null) {
            validationError?.let { message -> mutableState.update { it.copy(error = message) } }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, error = null) }
            submitCobranza(
                CobranzaSubmission(
                    idCliente = cliente.id,
                    selections = state.selections,
                    payments = state.paymentLines,
                    observacion = state.observacion,
                    idCobranza = idCobranza,
                ),
            ).onSuccess { response ->
                idCobranza = UUID.randomUUID().toString()
                mutableState.update {
                    it.copy(isSubmitting = false, result = response, selectedAmounts = emptyMap(), payments = emptyList(), observacion = "")
                }
                loadInvoices(cliente)
            }.onFailure { error ->
                mutableState.update { it.copy(isSubmitting = false, error = error.message ?: "No se pudo registrar el cobro") }
            }
        }
    }
}
