package com.amaxonia.pos.ui.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.ElectronicInvoiceStatus
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.isOfflinePending
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.usecase.payment.PrintInvoiceUseCase
import com.amaxonia.pos.domain.util.DateRangeValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class HistoryViewModel(
    private val transactionRepository: InvoiceHistoryRepository,
    private val cajaRepository: CajaRepository,
    private val printInvoiceUseCase: PrintInvoiceUseCase? = null,
    private val sessionReader: DashboardSessionReader? = null,
    private val networkMonitor: com.amaxonia.pos.data.remote.NetworkMonitor? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
        const val PAGE_SIZE = 100
        const val DEFAULT_COUNTRY_CODE = "PA"
    }

    private val todayString: String
        get() = todayProvider().toString()

    private fun createDefaultFilter(cajaId: String? = null): InvoiceHistoryFilter =
        InvoiceHistoryFilter(
            fechaInicio = todayString,
            fechaFin = todayString,
            cajaId = cajaId,
        )

    private val _state = MutableStateFlow(HistoryState(filter = createDefaultFilter()))
    val state: StateFlow<HistoryState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            cajaRepository.activeCaja.collect { caja ->
                val activeCajaId = caja?.idCaja
                _state.update {
                    it.copy(filter = it.filter.copy(cajaId = activeCajaId))
                }
                loadTransactions()
            }
        }
    }

    fun retry() {
        loadTransactions()
    }

    fun loadTransactions() {
        searchJob?.cancel()
        val activeCajaId = cajaRepository.activeCaja.value?.idCaja
        val filterToUse = _state.value.filter.copy(cajaId = activeCajaId)
        val validationError = DateRangeValidator.validate(filterToUse.fechaInicio, filterToUse.fechaFin)
        if (validationError != null) {
            _state.update { it.copy(isLoading = false, error = validationError) }
            return
        }
        viewModelScope.launch {
            refresh(filterToUse)
        }
    }

    fun onSearchChanged(value: String) {
        _state.update { it.copy(filter = it.filter.copy(search = value.takeIf(String::isNotBlank))) }
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MILLIS)
                loadTransactions()
            }
    }

    fun onUsuarioChanged(value: String) {
        updateFilter { it.copy(usuario = value.takeIf(String::isNotBlank)) }
    }

    fun onFechaInicioChanged(value: String) {
        updateFilter { it.copy(fechaInicio = value.takeIf(String::isNotBlank)) }
    }

    fun onFechaFinChanged(value: String) {
        updateFilter { it.copy(fechaFin = value.takeIf(String::isNotBlank)) }
    }

    fun applyFilters() {
        val currentFilter = _state.value.filter
        val validationError = DateRangeValidator.validate(currentFilter.fechaInicio, currentFilter.fechaFin)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        _state.update { it.copy(error = null) }
        loadTransactions()
    }

    fun clearFilters() {
        searchJob?.cancel()
        val activeCajaId = cajaRepository.activeCaja.value?.idCaja
        _state.update { it.copy(filter = createDefaultFilter(activeCajaId), error = null) }
        loadTransactions()
    }

    private fun updateFilter(transform: (InvoiceHistoryFilter) -> InvoiceHistoryFilter) {
        _state.update { it.copy(filter = transform(it.filter)) }
    }

    private suspend fun refresh(filter: InvoiceHistoryFilter) {
        _state.update { it.copy(isLoading = true, error = null) }
        val isNetworkOffline = networkMonitor?.isOnline() == false
        transactionRepository.getTransactions(filter = filter, limit = PAGE_SIZE).fold(
            onSuccess = { page ->
                val isOffline = page.isOffline || isNetworkOffline
                transactionRepository.getSummary(filter).fold(
                    onSuccess = { summary ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                transactions = page.transactions,
                                totalTransactions = page.total,
                                summary = summary,
                                error = null,
                                isOffline = isOffline,
                            )
                        }
                    },
                    onFailure = { exception ->
                        val offline = isOffline || isNetworkError(exception)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                transactions = page.transactions,
                                totalTransactions = page.total,
                                error = if (offline) null else (exception.message ?: "Error al cargar resumen de facturas"),
                                isOffline = offline,
                            )
                        }
                    },
                )
            },
            onFailure = { exception ->
                if (isNetworkError(exception) || isNetworkOffline) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            isOffline = true,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Error al cargar transacciones",
                        )
                    }
                }
            },
        )
    }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }

    fun onTransactionClick(transaction: Transaction) {
        _state.update {
            it.copy(
                selectedTransaction = transaction,
                showDetalleSheet = true,
                isLoadingDetalle = true,
                detalleItems = emptyList(),
                detalleError = null,
                detalleMessage = null,
                detalleActionError = null,
            )
        }
        viewModelScope.launch {
            transactionRepository.getInvoiceDetail(transaction.id).fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            isLoadingDetalle = false,
                            detalleItems = response.items,
                            detalleError = null,
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoadingDetalle = false,
                            detalleError = exception.message ?: "Error al cargar detalle",
                        )
                    }
                },
            )
        }
    }

    fun dismissDetalle() {
        _state.update {
            it.copy(
                showDetalleSheet = false,
                selectedTransaction = null,
                detalleItems = emptyList(),
                detalleError = null,
                detalleMessage = null,
                detalleActionError = null,
                isReprinting = false,
                isDownloadingPdf = false,
                isResendingFE = false,
            )
        }
    }

    fun reprintInvoice(transaction: Transaction) {
        if (printInvoiceUseCase == null) {
            _state.update { it.copy(detalleActionError = "Servicio de impresión no disponible") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isReprinting = true, detalleActionError = null, detalleMessage = null) }
            val countryCode = sessionReader?.currentCountry()?.code ?: DEFAULT_COUNTRY_CODE
            val feedback = printInvoiceUseCase(countryCode, transaction, transaction.id)
            if (feedback == null) {
                _state.update {
                    it.copy(
                        isReprinting = false,
                        detalleActionError = "No hay una impresora compatible configurada en Ajustes",
                    )
                }
            } else if (!feedback.isSuccess) {
                _state.update {
                    it.copy(
                        isReprinting = false,
                        detalleActionError = feedback.displayMessage,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isReprinting = false,
                        detalleMessage = feedback.displayMessage,
                    )
                }
            }
        }
    }

    fun downloadAndOpenPdf(
        context: Context,
        transaction: Transaction,
    ) {
        if (transaction.id.isBlank() || transaction.isOfflinePending()) {
            _state.update { it.copy(detalleActionError = "El PDF no está disponible para facturas no sincronizadas") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isDownloadingPdf = true, detalleActionError = null, detalleMessage = null) }
            transactionRepository.getInvoicePdf(transaction.id).fold(
                onSuccess = { bytes -> openPdfFile(context, transaction, bytes) },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isDownloadingPdf = false,
                            detalleActionError = error.message ?: "No se pudo descargar el PDF de la factura",
                        )
                    }
                },
            )
        }
    }

    private fun openPdfFile(
        context: Context,
        transaction: Transaction,
        bytes: ByteArray,
    ) {
        runCatching {
            val cleanNum = transaction.invoiceNumber.replace('/', '_').replace('\\', '_')
            val pdfFile = File(context.cacheDir, "factura_$cleanNum.pdf").apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            _state.update {
                it.copy(
                    isDownloadingPdf = false,
                    detalleMessage = "PDF descargado correctamente",
                )
            }
        }.onFailure { ex ->
            val errorMsg =
                if (ex is android.content.ActivityNotFoundException) {
                    "No hay una aplicación instalada para abrir archivos PDF"
                } else {
                    "No se pudo abrir el PDF: ${ex.message}"
                }
            _state.update {
                it.copy(
                    isDownloadingPdf = false,
                    detalleActionError = errorMsg,
                )
            }
        }
    }

    fun showOfflineSyncInitiated() {
        _state.update {
            it.copy(
                detalleActionError = null,
                detalleMessage = "Sincronización iniciada con el servidor",
            )
        }
    }

    fun resendElectronicInvoice(transaction: Transaction) {
        if (transaction.id.isBlank() || transaction.isOfflinePending()) {
            _state.update { it.copy(detalleActionError = "No se puede reenviar una factura no sincronizada") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isResendingFE = true, detalleActionError = null, detalleMessage = null) }
            transactionRepository.resendElectronicInvoice(transaction.id).fold(
                onSuccess = { result ->
                    val msg =
                        when {
                            result.alreadyIssued -> "La factura ya fue emitida electrónicamente previamente"
                            result.success && !result.cufe.isNullOrBlank() -> {
                                "Factura electrónica transmitida exitosamente"
                            }
                            else -> result.message ?: "Factura transmitida"
                        }
                    // Q3: EXITOSA exige CUFE; sin CUFE queda pendiente de reenvío.
                    val nuevoEstado =
                        if (!result.cufe.isNullOrBlank()) {
                            ElectronicInvoiceStatus.SUCCESS
                        } else {
                            ElectronicInvoiceStatus.PENDING
                        }
                    _state.update {
                        it.copy(
                            isResendingFE = false,
                            detalleMessage = msg,
                            selectedTransaction =
                                it.selectedTransaction?.copy(
                                    electronicStatus = nuevoEstado,
                                    codigoFiscal = result.cufe ?: it.selectedTransaction.codigoFiscal,
                                    numeroDocumentoFiscal =
                                        result.numeroDocumentoFiscal
                                            ?: it.selectedTransaction.numeroDocumentoFiscal,
                                ),
                        )
                    }
                    loadTransactions()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isResendingFE = false,
                            detalleActionError = error.message ?: "Error al reenviar factura electrónica",
                            selectedTransaction =
                                it.selectedTransaction?.copy(
                                    electronicStatus = ElectronicInvoiceStatus.FAILED,
                                ),
                        )
                    }
                },
            )
        }
    }
}

private val NETWORK_ERROR_KEYWORDS =
    listOf(
        "failed to connect",
        "unable to resolve host",
        "network is unreachable",
        "connection refused",
        "timeout",
    )

private fun isNetworkError(throwable: Throwable): Boolean {
    var cause: Throwable? = throwable
    while (cause != null) {
        val msg = cause.message?.lowercase().orEmpty()
        if (cause is java.io.IOException || NETWORK_ERROR_KEYWORDS.any { msg.contains(it) }) {
            return true
        }
        cause = cause.cause
    }
    return false
}
