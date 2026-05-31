package com.amaxonia.pos.ui.creditnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.printer.PrinterFactory
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteLineInputDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSettlementTypeDto
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CreditNoteRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
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
    private val printerFactory: PrinterFactory,
    private val localStore: LocalStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CreditNotesState())
    val state: StateFlow<CreditNotesState> = _state.asStateFlow()

    init {
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
            CreditNotesMode.CREATE -> _state.update { it.copy(mode = CreditNotesMode.INVOICE_PICKER, selectedInvoice = null, form = CreditNoteFormState()) }
        }
    }

    fun onSearchQueryChange(value: String) {
        _state.update { it.copy(searchQuery = value) }
    }

    fun onInvoiceSearchQueryChange(value: String) {
        _state.update { it.copy(invoiceSearchQuery = value) }
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
                            form = CreditNoteFormState(
                                fecha = LocalDate.now().toString(),
                                periodo = YearMonth.now().toString(),
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "No se pudo cargar la factura")
                    }
                }
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
                }
            )
        }
    }

    fun dismissCreditNoteDetail() {
        _state.update { it.copy(showCreditNoteDetail = false, selectedCreditNote = null) }
    }

    fun onFechaChange(value: String) {
        _state.update { it.copy(form = it.form.copy(fecha = value)) }
    }

    fun onPeriodoChange(value: String) {
        _state.update { it.copy(form = it.form.copy(periodo = value)) }
    }

    fun onObservacionChange(value: String) {
        _state.update { it.copy(form = it.form.copy(observacion = value)) }
    }

    fun onDevolverStockChange(enabled: Boolean) {
        _state.update { it.copy(form = it.form.copy(devolverStock = enabled)) }
    }

    fun onGenerarAbonoChange(generar: Boolean) {
        _state.update {
            it.copy(
                form = it.form.copy(
                    generarAbono = generar,
                    idFormaPagoReintegro = if (generar) null else it.form.idFormaPagoReintegro
                )
            )
        }
    }

    fun onRefundMethodChange(idFormaPago: Int?) {
        _state.update { it.copy(form = it.form.copy(idFormaPagoReintegro = idFormaPago)) }
    }

    fun submitCreditNote() {
        val currentState = _state.value
        val invoice = currentState.selectedInvoice ?: run {
            _state.update { it.copy(error = "Selecciona una factura para continuar") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, successMessage = null) }
            val activeCaja = cajaRepository.activeCaja.value ?: run {
                _state.update { it.copy(isSubmitting = false, error = "Debes tener una caja activa") }
                return@launch
            }
            val cajaStatus = cajaRepository.checkCajaStatus(activeCaja.idCaja).getOrElse { error ->
                _state.update { it.copy(isSubmitting = false, error = error.message ?: "No se pudo validar la caja") }
                return@launch
            }
            val idCajaSecuencia = cajaStatus.cajaSecuencia?.idCajaSecuencia.orEmpty()
            if (idCajaSecuencia.isBlank()) {
                _state.update { it.copy(isSubmitting = false, error = "La caja activa no tiene secuencia abierta") }
                return@launch
            }

            val settlementType = if (currentState.form.generarAbono) {
                CreditNoteSettlementTypeDto.ABONO
            } else if (currentState.form.idFormaPagoReintegro != null) {
                CreditNoteSettlementTypeDto.REINTEGRO
            } else {
                CreditNoteSettlementTypeDto.NINGUNO
            }

            val payload = CreateCreditNoteRequestDto(
                idFactura = invoice.id,
                fecha = currentState.form.fecha,
                periodo = currentState.form.periodo,
                observacion = currentState.form.observacion,
                detalle = emptyList(), // Devolución total
                anular = true, // Siempre se anula
                devolverStock = currentState.form.devolverStock,
                idCajaSecuencia = idCajaSecuencia,
                settlementType = settlementType,
                idFormaPagoReintegro = currentState.form.idFormaPagoReintegro,
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
                }
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
                    successMessage = if (processed.fiscalStatus == CreditNoteFiscalStatusDto.CONFIRMADA) {
                        "Nota de crédito fiscal confirmada"
                    } else {
                        it.successMessage
                    }
                )
            }
            loadCreditNotes()
        }
    }

    private fun loadCreditNotes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            creditNoteRepository.getCreditNotes(_state.value.searchQuery.trim().ifBlank { null }).fold(
                onSuccess = { response ->
                    _state.update { it.copy(isLoading = false, creditNotes = response.data) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "No se pudieron cargar las notas de crédito") }
                }
            )
        }
    }

    private fun loadSourceInvoices() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            creditNoteRepository.getSourceInvoices(_state.value.invoiceSearchQuery.trim().ifBlank { null }).fold(
                onSuccess = { response ->
                    _state.update { it.copy(isLoading = false, sourceInvoices = response.data) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "No se pudieron cargar las facturas") }
                }
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
                            availableRefundMethods = formas.filter { forma ->
                                !forma.descripcion.orEmpty().equals("PUNTO DE VENTA", ignoreCase = true)
                            }
                        )
                    }
                },
                onFailure = {
                    _state.update { state -> state.copy(availableRefundMethods = emptyList()) }
                }
            )
        }
    }

    private suspend fun processFiscalIfNeeded(detail: CreditNoteDetailDto, force: Boolean = false): CreditNoteDetailDto {
        if (detail.fiscalStatus == CreditNoteFiscalStatusDto.CONFIRMADA && !force) {
            return detail
        }
        if (!shouldProcessFiscal()) {
            return detail
        }
        val document = detail.fiscalDocument ?: return detail
        val activePrinter = printerFactory.getActivePrinter() ?: return detail

        return activePrinter.printCreditNote(document).fold(
            onSuccess = { result ->
                creditNoteRepository.confirmFiscal(
                    id = detail.id,
                    payload = ConfirmCreditNoteFiscalRequestDto(
                        codDevolucionFiscal = result.fiscalNumber,
                        numeroDocumentoFiscal = result.fiscalNumber,
                        printerSerial = result.printerSerial,
                    )
                ).fold(
                    onSuccess = {
                        detail.copy(
                            fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
                            fiscalNumber = it.codDevolucionFiscal,
                            printerSerial = it.printerSerial,
                        )
                    },
                    onFailure = { confirmError ->
                        _state.update {
                            it.copy(error = confirmError.message ?: "La impresión fiscal salió bien, pero no se pudo confirmar en el backend")
                        }
                        detail
                    }
                )
            },
            onFailure = { printError ->
                _state.update {
                    it.copy(error = printError.message ?: "No se pudo procesar la nota de crédito fiscal")
                }
                detail
            }
        )
    }

    private suspend fun shouldProcessFiscal(): Boolean {
        if (localStore.readSelectedCountry()?.code != "VE") return false
        val printerType = localStore.readSelectedPrinterType()
        return printerType == PrinterType.THE_FACTORY_HKA
    }

    private fun formatQuantity(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.3f", value)
    }
}
