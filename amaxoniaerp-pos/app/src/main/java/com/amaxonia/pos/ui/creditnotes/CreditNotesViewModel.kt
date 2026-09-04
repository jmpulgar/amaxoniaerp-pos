package com.amaxonia.pos.ui.creditnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.CreditNoteRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.usecase.creditnote.ProcessCreditNoteFiscalUseCase
import com.amaxonia.pos.domain.util.DateRangeValidator
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CreditNotesViewModel(
    private val creditNoteRepository: CreditNoteRepository,
    private val cajaRepository: CajaRepository,
    private val formaPagoRepository: FormaPagoRepository,
    private val processCreditNoteFiscal: ProcessCreditNoteFiscalUseCase,
    private val contextReader: CreditNoteContextReader? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(CreditNotesState())
    val state: StateFlow<CreditNotesState> = _state.asStateFlow()

    /** Edición de los campos del formulario de nota de crédito. */
    val formController = CreditNoteFormController(_state)

    init {
        viewModelScope.launch {
            cajaRepository.activeCaja.collect { caja ->
                val symbol = caja?.currency?.abrMonedaBase?.let { formatCurrencyLabel(it) } ?: "$"
                _state.update { it.copy(currencySymbol = symbol) }
            }
        }
        viewModelScope.launch {
            val countryCode = contextReader?.currentCountryCode()
            val isPanama = countryCode.equals(PANAMA_CODE, ignoreCase = true)
            _state.update { it.copy(isPanama = isPanama) }
        }
        refreshAll()
        loadRefundMethods()
    }

    fun refreshAll() {
        loadCreditNotes()
        if (_state.value.mode != CreditNotesMode.LIST) {
            loadSourceInvoices()
        }
    }

    fun openInvoicePicker() {
        _state.update { it.copy(mode = CreditNotesMode.INVOICE_PICKER, error = null, successMessage = null) }
        loadSourceInvoices()
    }

    fun backFromFlow() {
        when (_state.value.mode) {
            CreditNotesMode.LIST -> Unit
            CreditNotesMode.INVOICE_PICKER -> _state.update { it.copy(mode = CreditNotesMode.LIST, selectedInvoice = null) }
            CreditNotesMode.CREATE ->
                _state.update {
                    it.copy(mode = CreditNotesMode.INVOICE_PICKER, selectedInvoice = null, form = CreditNoteFormState())
                }
        }
    }

    fun onSearchQueryChange(value: String) {
        _state.update { it.copy(searchQuery = value) }
    }

    fun onInvoiceSearchQueryChange(value: String) {
        _state.update { it.copy(invoiceSearchQuery = value) }
    }

    fun setInvoiceDateFilterType(type: InvoiceDateFilterType) {
        _state.update {
            it.copy(
                invoiceDateFilter = it.invoiceDateFilter.copy(type = type),
                isDateFilterCustomExpanded = type == InvoiceDateFilterType.PERSONALIZADO,
            )
        }
        if (type != InvoiceDateFilterType.PERSONALIZADO ||
            (
                _state.value.invoiceDateFilter.customFechaInicio
                    .isNotBlank() ||
                    _state.value.invoiceDateFilter.customFechaFin
                        .isNotBlank()
            )
        ) {
            loadSourceInvoices()
        }
    }

    fun onCustomInvoiceFechaInicioChange(value: String) {
        _state.update {
            it.copy(
                invoiceDateFilter = it.invoiceDateFilter.copy(customFechaInicio = value),
            )
        }
    }

    fun onCustomInvoiceFechaFinChange(value: String) {
        _state.update {
            it.copy(
                invoiceDateFilter = it.invoiceDateFilter.copy(customFechaFin = value),
            )
        }
    }

    fun toggleDateFilterCustomExpanded() {
        _state.update { it.copy(isDateFilterCustomExpanded = !it.isDateFilterCustomExpanded) }
    }

    fun applyCustomInvoiceDateFilter() {
        val customStart = _state.value.invoiceDateFilter.customFechaInicio
        val customEnd = _state.value.invoiceDateFilter.customFechaFin
        val validationError = DateRangeValidator.validate(customStart, customEnd)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        _state.update {
            it.copy(
                invoiceDateFilter = it.invoiceDateFilter.copy(type = InvoiceDateFilterType.PERSONALIZADO),
                error = null,
            )
        }
        loadSourceInvoices()
    }

    fun resetInvoiceDateFilterToToday() {
        _state.update {
            it.copy(
                invoiceDateFilter = InvoiceDateFilter(type = InvoiceDateFilterType.HOY),
                isDateFilterCustomExpanded = false,
                error = null,
            )
        }
        loadSourceInvoices()
    }

    fun resetInvoiceDateFilterToCurrentMonth() {
        _state.update {
            it.copy(
                invoiceDateFilter = InvoiceDateFilter(type = InvoiceDateFilterType.MES_ACTUAL),
                isDateFilterCustomExpanded = false,
                error = null,
            )
        }
        loadSourceInvoices()
    }

    fun searchCreditNotes() = loadCreditNotes()

    fun searchSourceInvoices() = loadSourceInvoices()

    fun retry() = refreshAll()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    fun selectInvoice(invoiceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            creditNoteRepository.getSourceInvoiceDetail(invoiceId).fold(
                onSuccess = { invoice ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            mode = CreditNotesMode.CREATE,
                            selectedInvoice = invoice,
                            form =
                                CreditNoteFormState(
                                    fecha = LocalDate.now().toString(),
                                    periodo = YearMonth.now().toString(),
                                ),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "No se pudo cargar la factura")
                    }
                },
            )
        }
    }

    fun openCreditNoteDetail(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            creditNoteRepository.getCreditNoteDetail(id).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedCreditNote = detail,
                            showCreditNoteDetail = true,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "No se pudo cargar el detalle") }
                },
            )
        }
    }

    fun dismissCreditNoteDetail() {
        _state.update { it.copy(showCreditNoteDetail = false, selectedCreditNote = null) }
    }

    fun submitCreditNote() {
        val currentState = _state.value
        val invoice =
            currentState.selectedInvoice ?: run {
                _state.update { it.copy(error = "Selecciona una factura para continuar") }
                return
            }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, successMessage = null) }
            val activeCaja =
                cajaRepository.activeCaja.value ?: run {
                    _state.update { it.copy(isSubmitting = false, error = "Debes tener una caja activa") }
                    return@launch
                }
            val cajaStatus =
                cajaRepository.checkCajaStatus(activeCaja.idCaja).getOrElse { error ->
                    _state.update { it.copy(isSubmitting = false, error = error.message ?: "No se pudo validar la caja") }
                    return@launch
                }
            val idCajaSecuencia = cajaStatus.cajaSecuencia?.idCajaSecuencia.orEmpty()
            if (idCajaSecuencia.isBlank()) {
                _state.update { it.copy(isSubmitting = false, error = "La caja activa no tiene secuencia abierta") }
                return@launch
            }

            val payload =
                buildCreateCreditNoteRequest(
                    invoice = invoice,
                    form = currentState.form,
                    idCajaSecuencia = idCajaSecuencia,
                )

            creditNoteRepository.createCreditNote(payload).fold(
                onSuccess = { response ->
                    val processedDetail = processFiscalIfNeeded(response.detail)
                    loadCreditNotes()
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            mode = CreditNotesMode.LIST,
                            selectedInvoice = null,
                            form = CreditNoteFormState(),
                            selectedCreditNote = processedDetail,
                            showCreditNoteDetail = true,
                            successMessage = "Nota de crédito ${response.codigo} generada correctamente",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, error = error.message ?: "No se pudo crear la nota de crédito") }
                },
            )
        }
    }

    fun processSelectedCreditNoteFiscal() {
        val detail = _state.value.selectedCreditNote ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val processed = processFiscalIfNeeded(detail, force = true)
            _state.update {
                it.copy(
                    isSubmitting = false,
                    selectedCreditNote = processed,
                    successMessage =
                        if (processed.fiscalStatus == CreditNoteFiscalStatusDto.CONFIRMADA) {
                            if (it.isPanama) {
                                "Nota de crédito electrónica (NCE) confirmada"
                            } else {
                                "Nota de crédito fiscal confirmada"
                            }
                        } else {
                            it.successMessage
                        },
                )
            }
            loadCreditNotes()
        }
    }

    private fun loadCreditNotes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            creditNoteRepository
                .getCreditNotes(
                    _state.value.searchQuery
                        .trim()
                        .ifBlank { null },
                ).fold(
                    onSuccess = { response ->
                        _state.update { it.copy(isLoading = false, creditNotes = response.data) }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(isLoading = false, error = error.message ?: "No se pudieron cargar las notas de crédito") }
                    },
                )
        }
    }

    private fun loadSourceInvoices() {
        val (fechaInicio, fechaFin) = _state.value.invoiceDateFilter.resolveDateRange()
        val validationError = DateRangeValidator.validate(fechaInicio, fechaFin)
        if (validationError != null) {
            _state.update { it.copy(isLoading = false, error = validationError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            creditNoteRepository
                .getSourceInvoices(
                    search =
                        _state.value.invoiceSearchQuery
                            .trim()
                            .ifBlank { null },
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                ).fold(
                    onSuccess = { response ->
                        _state.update { it.copy(isLoading = false, sourceInvoices = response.data) }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(isLoading = false, error = error.message ?: "No se pudieron cargar las facturas") }
                    },
                )
        }
    }

    private fun loadRefundMethods() {
        viewModelScope.launch {
            val activeCajaId = cajaRepository.activeCaja.first()?.idCaja
            formaPagoRepository.getFormasPago(activeCajaId).fold(
                onSuccess = { formas ->
                    _state.update {
                        it.copy(
                            availableRefundMethods =
                                formas.filter { forma ->
                                    !forma.descripcion.orEmpty().equals("PUNTO DE VENTA", ignoreCase = true)
                                },
                        )
                    }
                },
                onFailure = {
                    _state.update { state -> state.copy(availableRefundMethods = emptyList()) }
                },
            )
        }
    }

    private suspend fun processFiscalIfNeeded(
        detail: CreditNoteDetailDto,
        force: Boolean = false,
    ): CreditNoteDetailDto {
        val result = processCreditNoteFiscal(detail, force)
        result.errorMessage?.let { message -> _state.update { it.copy(error = message) } }
        return result.detail
    }

    private companion object {
        const val PANAMA_CODE = "PA"
    }
}
