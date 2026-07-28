package com.amaxonia.pos.ui.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.mesas.CrearCuentaItemRequest
import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CuentaMesaRepository
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CuentaMesaViewModel(
    private val areaId: Int,
    private val mesaId: Int,
    private val sesionId: Int,
    private val cuentasRepository: CuentaMesaRepository,
    private val pedidosRepository: PedidosMesaRepository,
    private val activeCajaReader: ActiveCajaReader,
) : ViewModel() {
    private var cuentaSolicitada = false

    private val mutableState = MutableStateFlow(CuentaMesaState())
    val state = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<CuentaMesaEffect>(replay = 0, extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    fun load() {
        if (mutableState.value.isLoading) return
        viewModelScope.launch {
            val cajaId = activeCajaReader.activeCaja.value?.idCaja
            if (cajaId == null) {
                mutableState.update { it.copy(error = "Debes seleccionar una caja", isLoading = false) }
                return@launch
            }
            mutableState.update { it.copy(isLoading = true, error = null) }
            if (!cuentaSolicitada) {
                val solicitud = cuentasRepository.solicitarCuenta(cajaId, areaId, mesaId, sesionId)
                if (solicitud.isFailure) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            error = solicitud.exceptionOrNull()?.message ?: "No se pudo solicitar la cuenta",
                        )
                    }
                    return@launch
                }
                cuentaSolicitada = true
            }
            coroutineScope {
                val pedidos = async { pedidosRepository.listar(cajaId, areaId, mesaId, sesionId) }
                val cuentas = async { cuentasRepository.listar(cajaId, areaId, mesaId, sesionId) }
                val pedidosResult = pedidos.await()
                val cuentasResult = cuentas.await()
                val error = pedidosResult.exceptionOrNull() ?: cuentasResult.exceptionOrNull()
                mutableState.update {
                    it.copy(
                        pedidos =
                            pedidosResult
                                .getOrDefault(emptyList())
                                .filter { line -> line.estado == EstadoPedidoMesa.ENTREGADA },
                        cuentas = cuentasResult.getOrDefault(emptyList()),
                        isLoading = false,
                        error = error?.message,
                    )
                }
            }
        }
    }

    fun updateCantidad(
        pedidoId: Int,
        value: String,
    ) {
        val normalized = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        mutableState.update { state -> state.copy(cantidades = state.cantidades + (pedidoId to normalized), error = null) }
    }

    fun crearCuentaCompleta() {
        crear(CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true))
    }

    fun crearDivision() {
        val current = mutableState.value
        val items =
            current.pedidos.mapNotNull { pedido ->
                val quantity = current.cantidades[pedido.id]?.toDoubleOrNull() ?: return@mapNotNull null
                when {
                    quantity <= 0.0 -> null
                    quantity > current.disponible(pedido) -> {
                        mutableState.update { it.copy(error = "La cantidad de ${pedido.itemDescripcion} supera el saldo pendiente") }
                        return
                    }
                    else -> CrearCuentaItemRequest(pedidoMesaId = pedido.id, cantidad = quantity)
                }
            }
        if (items.isEmpty()) {
            mutableState.update { it.copy(error = "Selecciona al menos una cantidad para dividir la cuenta") }
            return
        }
        crear(CrearCuentaRequest(items = items, incluirTodoPendiente = false))
    }

    fun cancelar(cuenta: CuentaMesaResponse) {
        mutate("Cuenta cancelada") { cajaId ->
            cuentasRepository.cancelar(cajaId, areaId, mesaId, sesionId, cuenta.id)
        }
    }

    fun pagar(cuenta: CuentaMesaResponse) {
        if (!mutableEffects.tryEmit(CuentaMesaEffect.Pay(cuenta))) {
            viewModelScope.launch { mutableEffects.emit(CuentaMesaEffect.Pay(cuenta)) }
        }
    }

    private fun crear(request: CrearCuentaRequest) {
        mutate("Cuenta creada") { cajaId ->
            cuentasRepository.crear(cajaId, areaId, mesaId, sesionId, request)
        }
    }

    private fun mutate(
        successMessage: String,
        operation: suspend (String) -> Result<CuentaMesaResponse>,
    ) {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            val cajaId = activeCajaReader.activeCaja.value?.idCaja
            if (cajaId == null) {
                mutableState.update { it.copy(error = "Debes seleccionar una caja") }
                return@launch
            }
            mutableState.update { it.copy(isSaving = true, error = null, info = null) }
            operation(cajaId).fold(
                onSuccess = {
                    mutableState.update { state ->
                        state.copy(isSaving = false, cantidades = emptyMap(), info = successMessage)
                    }
                    load()
                },
                onFailure = { error ->
                    mutableState.update { it.copy(isSaving = false, error = error.message ?: "No se pudo modificar la cuenta") }
                },
            )
        }
    }
}
