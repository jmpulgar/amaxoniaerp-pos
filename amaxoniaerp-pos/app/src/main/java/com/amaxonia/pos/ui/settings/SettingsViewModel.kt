package com.amaxonia.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.PrinterType
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

    init {
        viewModelScope.launch {
            localStore.selectedPrinterTypeFlow().collect { printerType ->
                _selectedPrinterType.value = printerType
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
}
