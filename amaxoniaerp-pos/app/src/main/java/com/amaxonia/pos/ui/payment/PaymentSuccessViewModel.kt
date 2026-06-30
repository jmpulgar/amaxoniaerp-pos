package com.amaxonia.pos.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.repository.SalesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentSuccessUiState(
    val isLoading: Boolean = true,
    val isSendingReceiptEmail: Boolean = false,
    val payload: PaymentSuccessPayload? = null,
    val errorMessage: String? = null
)

class PaymentSuccessViewModel(
    private val localStore: LocalStore,
    private val salesRepository: SalesRepository,
    transactionId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentSuccessUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = runCatching { localStore.readLastPaymentSuccess(transactionId) }
            result
                .onSuccess { payload ->
                    if (payload == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                payload = null,
                                errorMessage = "No se pudo cargar la transacción del recibo"
                            )
                        }
                    } else {
                        _state.update { it.copy(isLoading = false, payload = payload, errorMessage = null) }
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            payload = null,
                            errorMessage = throwable.message ?: "Error al cargar la transacción del recibo"
                        )
                    }
                }
        }
    }

    fun sendReceiptEmail() {
        val facturaId = _state.value.payload?.transactionId
            ?.takeIf { it.isNotBlank() }
            ?: return _state.update { it.copy(errorMessage = "No hay factura para enviar") }

        viewModelScope.launch {
            _state.update { it.copy(isSendingReceiptEmail = true, errorMessage = null) }
            salesRepository.sendReceiptEmail(facturaId).fold(
                onSuccess = { response ->
                    val message = response.mensaje
                        ?: response.resultado
                        ?: "Recibo enviado por correo"
                    _state.update { it.copy(isSendingReceiptEmail = false, errorMessage = message) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            isSendingReceiptEmail = false,
                            errorMessage = throwable.message ?: "No se pudo enviar el recibo por correo"
                        )
                    }
                }
            )
        }
    }
}

