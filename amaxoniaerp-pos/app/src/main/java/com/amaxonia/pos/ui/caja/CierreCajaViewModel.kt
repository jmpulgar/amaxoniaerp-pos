package com.amaxonia.pos.ui.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.repository.CajaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CierreCajaViewModel(
    private val cajaRepository: CajaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CierreCajaUiState>(CierreCajaUiState.Loading)
    val uiState: StateFlow<CierreCajaUiState> = _uiState.asStateFlow()

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

    fun confirmClose() {
        val currentState = _uiState.value
        val summary = when (currentState) {
            is CierreCajaUiState.Ready -> currentState.summary
            is CierreCajaUiState.Error -> currentState.summary ?: return
            else -> return
        }

        viewModelScope.launch {
            _uiState.value = CierreCajaUiState.Closing(summary)

            val caja = cajaRepository.activeCaja.value ?: run {
                _uiState.value = CierreCajaUiState.Error(
                    message = "No hay caja activa para cerrar",
                    summary = summary
                )
                return@launch
            }

            val request = CierreCajaRequest(
                idCajaSecuencia = "", // Will be resolved by the backend from active session
                idCaja = caja.idCaja,
                montoCierre = summary.expectedClose,
                totalEfectivo = summary.totalCash,
                totalTarjeta = summary.totalCard,
                totalOtros = summary.totalOther,
                totalVentas = summary.totalSales,
                cantidadTransacciones = summary.transactionCount
            )

            cajaRepository.closeCaja(request).fold(
                onSuccess = { response ->
                    cajaRepository.clearActiveCaja()
                    _uiState.value = CierreCajaUiState.Success(
                        message = response.message ?: "Caja cerrada correctamente"
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
