package com.amaxonia.pos.ui.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.printer.PrinterFactory
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.repository.CajaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CierreCajaViewModel(
    private val cajaRepository: CajaRepository,
    private val printerFactory: PrinterFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow<CierreCajaUiState>(CierreCajaUiState.Loading)
    val uiState: StateFlow<CierreCajaUiState> = _uiState.asStateFlow()

    private val _isPrintingReportX = MutableStateFlow(false)
    val isPrintingReportX: StateFlow<Boolean> = _isPrintingReportX.asStateFlow()

    private val _isPrintingReportZ = MutableStateFlow(false)
    val isPrintingReportZ: StateFlow<Boolean> = _isPrintingReportZ.asStateFlow()

    private val _reportMessage = MutableStateFlow<String?>(null)
    val reportMessage: StateFlow<String?> = _reportMessage.asStateFlow()

    val hasActivePrinter: Boolean
        get() = printerFactory.getActivePrinter() != null

    init {
        loadSummary()
    }

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = CierreCajaUiState.Loading
            cajaRepository.getCierreSummary().fold(
                onSuccess = { summary ->
                    _uiState.value = CierreCajaUiState.Ready(summary)
                },
                onFailure = { error ->
                    _uiState.value = CierreCajaUiState.Error(
                        message = error.message ?: "No se pudo cargar el resumen de caja"
                    )
                }
            )
        }
    }

    fun printReportX() {
        val printer = printerFactory.getActivePrinter() ?: return
        viewModelScope.launch {
            _isPrintingReportX.value = true
            _reportMessage.value = null
            printer.printReportX().fold(
                onSuccess = {
                    _reportMessage.value = "Reporte X impreso correctamente"
                },
                onFailure = { error ->
                    _reportMessage.value = "Error Reporte X: ${error.message}"
                }
            )
            _isPrintingReportX.value = false
        }
    }

    fun printReportZ() {
        val printer = printerFactory.getActivePrinter() ?: return
        viewModelScope.launch {
            _isPrintingReportZ.value = true
            _reportMessage.value = null
            printer.printReportZ().fold(
                onSuccess = {
                    _reportMessage.value = "Reporte Z impreso correctamente"
                },
                onFailure = { error ->
                    _reportMessage.value = "Error Reporte Z: ${error.message}"
                }
            )
            _isPrintingReportZ.value = false
        }
    }

    fun clearReportMessage() {
        _reportMessage.value = null
    }

    fun confirmClose() {
        val currentState = _uiState.value
        val summary = when (currentState) {
            is CierreCajaUiState.Ready -> currentState.summary
            is CierreCajaUiState.Error -> currentState.summary ?: return
            else -> return
        }

        viewModelScope.launch {
            _uiState.value = CierreCajaUiState.Closing(summary)

            cajaRepository.activeCaja.value ?: run {
                _uiState.value = CierreCajaUiState.Error(
                    message = "No hay caja activa para cerrar",
                    summary = summary
                )
                return@launch
            }

            val request = CierreCajaRequest(
                id = summary.idCajaSecuencia,
                monto_efectivo_ventas = summary.montoEfectivoVentas,
                monto_efectivo_entrada = summary.montoEfectivoEntrada,
                monto_efectivo_salida = summary.montoEfectivoSalida,
                monto_efectivo_total = summary.montoEfectivoTotal,
                monto_efectivo_cierre = summary.montoEfectivoCierre,
                monto_efectivo_diferencia = summary.montoEfectivoDiferencia,
                monto_otros_total = summary.montoOtrosTotal,
                monto_otros_cierre = summary.montoOtrosCierre,
                monto_otros_diferencia = summary.montoOtrosDiferencia,
                monto_total = summary.montoTotal,
                monto_cierre = summary.montoCierre,
                monto_diferencia = summary.montoDiferencia,
                detalle = summary.detalle,
                detalle_formapago = summary.detalleFormaPago,
                observacion_cierre = "",
                numero_cierre_fiscal = "",
            )

            cajaRepository.closeCaja(request).fold(
                onSuccess = { response ->
                    cajaRepository.clearActiveCaja()
                    _uiState.value = CierreCajaUiState.Success(
                        message = response.message
                    )
                },
                onFailure = { error ->
                    _uiState.value = CierreCajaUiState.Error(
                        message = error.message ?: "Error al cerrar la caja",
                        summary = summary
                    )
                }
            )
        }
    }
}
