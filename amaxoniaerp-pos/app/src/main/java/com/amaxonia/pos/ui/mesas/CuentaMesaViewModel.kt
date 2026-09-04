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
import java.util.Locale

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
                if (solicitud.isSuccess) {
                    cuentaSolicitada = true
                }
            }
            coroutineScope {
                val pedidos = async { pedidosRepository.listar(cajaId, areaId, mesaId, sesionId) }
                val cuentas = async { cuentasRepository.listar(cajaId, areaId, mesaId, sesionId) }
                val pedidosResult = pedidos.await()
                val cuentasResult = cuentas.await()
                val error = pedidosResult.exceptionOrNull() ?: cuentasResult.exceptionOrNull()
                val todosPedidos = pedidosResult.getOrDefault(emptyList())
                mutableState.update {
                    it.copy(
                        pedidos = todosPedidos.filter { line -> line.estado == EstadoPedidoMesa.ENTREGADA },
                        pedidosNoEntregados =
                            todosPedidos.filter { line ->
                                line.estado != EstadoPedidoMesa.ENTREGADA && line.estado != EstadoPedidoMesa.CANCELADA
                            },
                        cuentas = cuentasResult.getOrDefault(emptyList()),
                        isLoading = false,
                        error = error?.message,
                    )
                }
            }
        }
    }

    fun setModo(modo: CuentaModo) {
        mutableState.update { it.copy(modoSeleccionado = modo, error = null) }
    }

    fun setShowHistoricoSheet(show: Boolean) {
        mutableState.update { it.copy(showHistoricoSheet = show) }
    }

    fun setShowCuentasActivasSheet(show: Boolean) {
        mutableState.update { it.copy(showCuentasActivasSheet = show) }
    }

    fun clearMessages() {
        mutableState.update { it.copy(error = null, info = null) }
    }

    fun marcarTodosEntregados() {
        if (mutableState.value.isDeliveringAll || mutableState.value.isSaving) return
        val noEntregados = mutableState.value.pedidosNoEntregados
        if (noEntregados.isEmpty()) return

        viewModelScope.launch {
            val cajaId = activeCajaReader.activeCaja.value?.idCaja
            if (cajaId == null) {
                mutableState.update { it.copy(error = "Debes seleccionar una caja") }
                return@launch
            }
            mutableState.update { it.copy(isDeliveringAll = true, error = null, info = null) }
            var hayError = false
            var ultimoError: String? = null
            for (pedido in noEntregados) {
                val res =
                    pedidosRepository.cambiarEstado(
                        cajaId = cajaId,
                        areaId = areaId,
                        mesaId = mesaId,
                        sesionId = sesionId,
                        pedidoId = pedido.id,
                        estado = EstadoPedidoMesa.ENTREGADA,
                    )
                if (res.isFailure) {
                    hayError = true
                    ultimoError = res.exceptionOrNull()?.message
                }
            }
            mutableState.update {
                it.copy(
                    isDeliveringAll = false,
                    error = if (hayError) (ultimoError ?: "No se pudieron entregar todos los pedidos") else null,
                    info = if (!hayError) "Todos los productos fueron marcados como entregados" else null,
                )
            }
            load()
        }
    }

    fun updateCantidad(
        pedidoId: Int,
        value: String,
    ) {
        val normalized = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        mutableState.update { state -> state.copy(cantidades = state.cantidades + (pedidoId to normalized), error = null) }
    }

    fun toggleSeleccion(pedidoId: Int) {
        val current = mutableState.value
        val pedido = current.pedidos.find { it.id == pedidoId } ?: return
        val disponible = current.disponible(pedido)
        if (current.estaSeleccionado(pedidoId)) {
            mutableState.update { it.copy(cantidades = it.cantidades - pedidoId, error = null) }
        } else if (disponible > 0.0) {
            mutableState.update { it.copy(cantidades = it.cantidades + (pedidoId to formatQuantity(disponible)), error = null) }
        }
    }

    fun incrementarCantidad(
        pedidoId: Int,
        step: Double = 1.0,
    ) {
        val current = mutableState.value
        val pedido = current.pedidos.find { it.id == pedidoId } ?: return
        val disponible = current.disponible(pedido)
        val actual = current.cantidadSeleccionada(pedidoId)
        val siguiente = (if (actual <= 0.0) 1.0.coerceAtMost(disponible) else actual + step).coerceAtMost(disponible)
        mutableState.update { it.copy(cantidades = it.cantidades + (pedidoId to formatQuantity(siguiente)), error = null) }
    }

    fun decrementarCantidad(
        pedidoId: Int,
        step: Double = 1.0,
    ) {
        val current = mutableState.value
        val actual = current.cantidadSeleccionada(pedidoId)
        val siguiente = actual - step
        if (siguiente <= 0.0) {
            mutableState.update { it.copy(cantidades = it.cantidades - pedidoId, error = null) }
        } else {
            mutableState.update { it.copy(cantidades = it.cantidades + (pedidoId to formatQuantity(siguiente)), error = null) }
        }
    }

    fun seleccionarTodoParaDividir() {
        val current = mutableState.value
        val nuevas = current.pedidosDisponibles.associate { it.id to formatQuantity(current.disponible(it)) }
        mutableState.update { it.copy(cantidades = nuevas, error = null) }
    }

    fun deseleccionarTodoParaDividir() {
        mutableState.update { it.copy(cantidades = emptyMap(), error = null) }
    }

    fun crearCuentaCompleta() {
        crear(CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true))
    }

    fun crearYCobrarCuentaCompleta() {
        if (mutableState.value.isSaving) return
        val activas = mutableState.value.cuentasActivas
        if (activas.isNotEmpty()) {
            pagar(activas.first())
            return
        }

        viewModelScope.launch {
            val cajaId = activeCajaReader.activeCaja.value?.idCaja
            if (cajaId == null) {
                mutableState.update { it.copy(error = "Debes seleccionar una caja") }
                return@launch
            }
            mutableState.update { it.copy(isSaving = true, error = null, info = null) }
            val request = CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true)
            cuentasRepository.crear(cajaId, areaId, mesaId, sesionId, request).fold(
                onSuccess = { cuentaCreada ->
                    mutableState.update { state ->
                        state.copy(
                            isSaving = false,
                            cantidades = emptyMap(),
                            info = "Cuenta creada",
                        )
                    }
                    pagar(cuentaCreada)
                    load()
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "No se pudo crear la cuenta",
                        )
                    }
                },
            )
        }
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
                        state.copy(
                            isSaving = false,
                            cantidades = emptyMap(),
                            info = successMessage,
                            // Si se dividió o se creó una cuenta, abrir sheet de cuentas activas
                            showCuentasActivasSheet = true,
                        )
                    }
                    load()
                },
                onFailure = { error ->
                    mutableState.update { it.copy(isSaving = false, error = error.message ?: "No se pudo modificar la cuenta") }
                },
            )
        }
    }

    companion object {
        fun formatQuantity(value: Double): String =
            if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
            }
    }
}
