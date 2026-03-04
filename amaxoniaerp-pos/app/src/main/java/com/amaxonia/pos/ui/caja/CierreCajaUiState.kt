package com.amaxonia.pos.ui.caja

import com.amaxonia.pos.domain.model.caja.CierreCajaSummary

sealed interface CierreCajaUiState {
    data object Loading : CierreCajaUiState
    data class Ready(val summary: CierreCajaSummary) : CierreCajaUiState
    data class Closing(val summary: CierreCajaSummary) : CierreCajaUiState
    data class Success(val message: String) : CierreCajaUiState
    data class Error(val message: String, val summary: CierreCajaSummary? = null) : CierreCajaUiState
}
