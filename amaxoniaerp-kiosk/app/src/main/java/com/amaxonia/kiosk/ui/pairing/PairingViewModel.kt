package com.amaxonia.kiosk.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.network.KioskApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAIRING_CODE_LENGTH = 8
private const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8080"

data class PairingUiState(
    val serverUrl: String = DEFAULT_EMULATOR_URL,
    val countryCode: String = "PA",
    val companyDb: String = "",
    val pairingCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface PairingEvent {
    data class Success(val deviceName: String, val prefix: String) : PairingEvent
}

class PairingViewModel(
    private val apiClient: KioskApiClient,
    initialServerUrl: String? = null,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            PairingUiState(
                serverUrl = initialServerUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_EMULATOR_URL,
            ),
        )
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>()
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    fun onServerUrlChanged(value: String) {
        _uiState.update { it.copy(serverUrl = value, errorMessage = null) }
    }

    fun onCountryCodeChanged(value: String) {
        _uiState.update { it.copy(countryCode = value.uppercase(), errorMessage = null) }
    }

    fun onCompanyDbChanged(value: String) {
        _uiState.update { it.copy(companyDb = value, errorMessage = null) }
    }

    fun onPairingCodeChanged(value: String) {
        val filtered = value.filter { it.isLetterOrDigit() }.take(PAIRING_CODE_LENGTH).uppercase()
        _uiState.update { it.copy(pairingCode = filtered, errorMessage = null) }
    }

    fun submitPairing(): Job {
        val current = _uiState.value
        val validationError = validateInput(current)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return Job().apply { complete() }
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        return viewModelScope.launch {
            val result =
                apiClient.pair(
                    serverUrl = current.serverUrl,
                    countryCode = current.countryCode,
                    companyDb = current.companyDb,
                    pairingCode = current.pairingCode,
                )
            result.fold(
                onSuccess = { response ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(PairingEvent.Success(response.deviceName, response.prefix))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Error al emparejar con el servidor",
                        )
                    }
                },
            )
        }
    }

    private fun validateInput(state: PairingUiState): String? {
        return when {
            state.serverUrl.isBlank() -> "La URL del servidor es requerida"
            state.countryCode.isBlank() -> "El código de país es requerido (PA o VE)"
            state.companyDb.isBlank() -> "La base de datos de la empresa es requerida"
            state.pairingCode.length != PAIRING_CODE_LENGTH ->
                "El código de emparejamiento debe tener $PAIRING_CODE_LENGTH caracteres"
            else -> null
        }
    }
}
