package com.amaxonia.kiosk.ui.customer

import androidx.lifecycle.ViewModel
import com.amaxonia.kiosk.domain.cart.OrderGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_DOC_LENGTH = 20
private const val MAX_DV_LENGTH = 4
private const val MAX_NAME_LENGTH = 80

data class CustomerIdUiState(
    val isCustomBilling: Boolean = false,
    val docType: String = "CEDULA",
    val docNumber: String = "",
    val dv: String = "",
    val name: String = "",
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = !isCustomBilling || (docNumber.isNotBlank() && name.isNotBlank())
}

class CustomerIdViewModel(
    private val orderGraph: OrderGraph,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerIdUiState())
    val uiState: StateFlow<CustomerIdUiState> = _uiState.asStateFlow()

    fun toggleCustomBilling(enabled: Boolean) {
        _uiState.update { it.copy(isCustomBilling = enabled, errorMessage = null) }
    }

    fun onDocTypeChanged(type: String) {
        _uiState.update { it.copy(docType = type) }
    }

    fun onDocNumberChanged(number: String) {
        _uiState.update { it.copy(docNumber = number.take(MAX_DOC_LENGTH), errorMessage = null) }
    }

    fun onDvChanged(dv: String) {
        _uiState.update { it.copy(dv = dv.take(MAX_DV_LENGTH)) }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH), errorMessage = null) }
    }

    fun selectConsumidorFinal(onSuccess: () -> Unit) {
        orderGraph.setCustomer(id = "CF", name = "Consumidor Final")
        onSuccess()
    }

    fun submitCustomCustomer(onSuccess: () -> Unit) {
        val current = _uiState.value
        if (current.docNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingrese el número de documento / RUC") }
            return
        }
        if (current.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingrese el nombre o razón social") }
            return
        }

        val formattedId =
            if (current.dv.isNotBlank()) {
                "${current.docNumber.trim()}-${current.dv.trim()}"
            } else {
                current.docNumber.trim()
            }

        orderGraph.setCustomer(
            id = formattedId,
            name = current.name.trim(),
        )
        onSuccess()
    }
}
