package com.amaxonia.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val localStore: LocalStore
) : ViewModel() {

    private val _selectedPrinterType = MutableStateFlow(PrinterType.NONE)
    val selectedPrinterType = _selectedPrinterType.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _theFactorySettings = MutableStateFlow(TheFactorySettings())
    val theFactorySettings = _theFactorySettings.asStateFlow()

    init {
        viewModelScope.launch {
            localStore.selectedPrinterTypeFlow().collect { printerType ->
                _selectedPrinterType.value = printerType
            }
        }
        viewModelScope.launch {
            localStore.theFactorySettingsFlow().collect { settings ->
                _theFactorySettings.value = settings
            }
        }
    }

    fun onPrinterTypeSelected(printerType: PrinterType) {
        if (_selectedPrinterType.value == printerType) return
        viewModelScope.launch {
            runCatching {
                localStore.saveSelectedPrinterType(printerType)
            }.onFailure { throwable ->
                _errorMessage.update {
                    throwable.message ?: "No se pudo guardar la configuracion de impresora"
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun onTheFactoryIpChanged(value: String) {
        _theFactorySettings.update { current ->
            current.copy(ipAddress = value.trim())
        }
    }

    fun onTheFactoryPortChanged(value: String) {
        _theFactorySettings.update { current ->
            current.copy(port = value.filter(Char::isDigit))
        }
    }

    suspend fun persistTheFactorySettings(showSuccessMessage: Boolean = false): Result<Unit> {
        val settings = _theFactorySettings.value.copy(
            ipAddress = _theFactorySettings.value.ipAddress.trim(),
            port = _theFactorySettings.value.port.trim()
        )

        return runCatching {
            require(settings.ipAddress.isNotBlank()) { "Ingresa la IP de The Factory HKA" }
            require(settings.port.isNotBlank()) { "Ingresa el puerto de The Factory HKA" }
            require(settings.port.toIntOrNull() != null) { "El puerto de The Factory HKA no es valido" }
            localStore.saveTheFactorySettings(settings)
            _theFactorySettings.value = settings
            if (showSuccessMessage) {
                _statusMessage.value = "Configuracion de The Factory HKA guardada"
            }
        }.onFailure { throwable ->
            _errorMessage.value = throwable.message ?: "No se pudo guardar la configuracion de The Factory HKA"
        }
    }
}
