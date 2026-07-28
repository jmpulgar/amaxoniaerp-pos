package com.amaxonia.pos.ui.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import com.amaxonia.pos.domain.usecase.mesas.BuildPedidoMesaItemsInput
import com.amaxonia.pos.domain.usecase.mesas.BuildPedidoMesaItemsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Comanda de una sesión de mesa. Reutiliza el [CartRepository] compartido para agregar productos
 * (los pendientes se muestran igual que en el carrito) y los convierte en pedidos PENDIENTE/ENVIADA
 * contra el backend. NO duplica el carrito.
 *
 * Requerimientos cubiertos:
 * - Separar pendientes de enviados (estado PENDIENTE vs el resto no final).
 * - Recuperar pedidos abiertos de la mesa (vía `listar`).
 * - Agregar productos y enviar comanda (`crear(enviarInmediato=true)` sobre el carrito).
 * - Cambiar estados de comanda (`cambiarEstado`) respetando la transición válida del backend.
 *
 * Reglas de estado:
 * - La transición inválida o de pedidos finales se rechaza y muestra el mensaje del backend.
 * - El envío requiere sesión activa y que haya items pendientes (carrito o backend).
 */
class ComandaViewModel(
    private val areaId: Int,
    private val mesaId: Int,
    private val sesionId: Int,
    private val pedidosMesaRepository: PedidosMesaRepository,
    private val cartRepository: CartRepository,
    private val activeCajaReader: ActiveCajaReader,
    private val connectivity: ConnectivityStatus,
    private val buildPedidoItems: BuildPedidoMesaItemsUseCase = BuildPedidoMesaItemsUseCase(),
) : ViewModel() {
    private val _state = MutableStateFlow(ComandaState())
    val state: StateFlow<ComandaState> = _state.asStateFlow()

    init {
        // Marca el carrito compartido como perteneciente a esta sesión. No borra su contenido:
        // si el usuario ya agregó productos en la pantalla anterior, se muestran como pendientes.
        cartRepository.bindSesionMesa(sesionId)
        cargar(skipSpinner = false)
    }

    /**
     * Recarga la lista de pedidos desde el backend. `skipSpinner=true` evita parpadeos cuando
     * se llama internamente tras enviar/cambiar estado ( Spinner rápido ).
     */
    fun cargar(skipSpinner: Boolean = false) {
        if (!connectivity.isOnline()) {
            _state.update { it.copy(isLoading = false, error = "Sin conexión: no se puede consultar la comanda") }
            return
        }
        val cajaId = activeCajaReader.activeCaja.value?.idCaja
        if (cajaId.isNullOrBlank()) {
            _state.update { it.copy(error = "No hay caja activa") }
            return
        }
        viewModelScope.launch {
            if (!skipSpinner) _state.update { it.copy(isLoading = true, error = null) }
            val result = pedidosMesaRepository.listar(cajaId, areaId, mesaId, sesionId, estado = null)
            result.fold(
                onSuccess = { pedidos -> _state.update { it.copy(isLoading = false, error = null, pendientes = pedidos.pendientes(), enviados = pedidos.enviados()) } },
                onFailure = { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Error al consultar pedidos") } },
            )
        }
    }

    /**
     * Envía la comanda actual. Dos pasos:
     *  1. Si el carrito compartido tiene items nuevos, los persiste con `crear(enviarInmediato=true)`
     *     (alternativa: crear sin enviar y luego enviar, pero combinamos para minimizar viajes).
     *  2. Si ya existían pedidos PENDIENTES en el backend (cargados por otra caja), los mueve a
     *     ENVIADA con `enviarComanda(pedidoIds)`.
     *
     * El carrito compartido se limpia al final para que el próximo inicio en blanco.
     */
    fun enviarComanda() {
        val cajaId = activeCajaReader.activeCaja.value?.idCaja
        if (cajaId.isNullOrBlank()) {
            _state.update { it.copy(error = "No hay caja activa") }
            return
        }
        if (!connectivity.isOnline()) {
            _state.update { it.copy(error = "Sin conexión: no se puede enviar la comanda") }
            return
        }
        val carrito = cartRepository.cartItems.value
        val pendientesBackend = _state.value.pendientes
        if (carrito.isEmpty() && pendientesBackend.isEmpty()) {
            _state.update { it.copy(error = "No hay items para enviar") }
            return
        }
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null) }

        viewModelScope.launch {
            val caja = activeCajaReader.activeCaja.value
            val sellerId =
                cartRepository.currentSeller.value?.id?.takeIf { it > 0 }
                    ?: caja?.defaultSellerId?.takeIf { it > 0 }
                    ?: caja?.availableSellers?.firstOrNull()?.id
                    ?: DEFAULT_SELLER_ID
            val warehouseId = caja?.defaultWarehouseId ?: caja?.codAlmacen?.takeIf { it > 0 } ?: DEFAULT_WAREHOUSE_ID
            val defaultTaxRate = caja?.defaultTaxRate?.takeIf { it > 0.0 } ?: 0.0

            // 1) Persistir items nuevos del carrito como pedidos ENVIADOS en un solo Paso.
            var creadosNuevos: List<PedidoMesa> = emptyList()
            if (carrito.isNotEmpty()) {
                val items =
                    buildPedidoItems(
                        BuildPedidoMesaItemsInput(
                            cartItems = carrito,
                            warehouseId = warehouseId,
                            sellerId = sellerId,
                            defaultTaxRate = defaultTaxRate,
                        ),
                    )
                val crearResult =
                    pedidosMesaRepository.crear(
                        cajaId = cajaId,
                        areaId = areaId,
                        mesaId = mesaId,
                        sesionId = sesionId,
                        request = com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest(items = items, enviarInmediato = true),
                    )
                if (crearResult.isFailure) {
                    _state.update { it.copy(isSending = false, error = crearResult.exceptionOrNull()?.message ?: "No se pudieron crear los items") }
                    return@launch
                }
                creadosNuevos = crearResult.getOrThrow()
            }

            // 2) Mover pedidos que ya estaban PENDIENTES en el backend (ej. agregados por otra caja).
            if (pendientesBackend.isNotEmpty()) {
                val enviarResult =
                    pedidosMesaRepository.enviarComanda(
                        cajaId = cajaId,
                        areaId = areaId,
                        mesaId = mesaId,
                        sesionId = sesionId,
                        request =
                            com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest(
                                pedidoIds = pendientesBackend.map { it.id },
                            ),
                    )
                if (enviarResult.isFailure) {
                    // Los nuevos ya fueron enviados; dejamos el error visible y recargamos.
                    cartRepository.clearItemsOnly()
                    _state.update {
                        it.copy(
                            isSending = false,
                            error = enviarResult.exceptionOrNull()?.message ?: "Falló el envío de pedidos previos",
                            info = "Items nuevos enviados; algunos pedidos previos requieren reintento",
                        )
                    }
                    cargar(skipSpinner = true)
                    return@launch
                }
            }

            cartRepository.clearItemsOnly()
            _state.update {
                it.copy(
                    isSending = false,
                    error = null,
                    info = "Comanda enviada (${creadosNuevos.size + pendientesBackend.size} líneas)",
                )
            }
            cargar(skipSpinner = true)
        }
    }

    /**
     * Cambia el estado de un pedido de comanda. La regla de transición la valida el backend; lo
     * único que el cliente filtra es que no se pueda avanzar un pedido ya final.
     */
    fun cambiarEstado(
        pedidoId: Int,
        nuevoEstado: String,
    ) {
        val cajaId = activeCajaReader.activeCaja.value?.idCaja
        if (cajaId.isNullOrBlank()) {
            _state.update { it.copy(error = "No hay caja activa") }
            return
        }
        val actual = _state.value.enviados.firstOrNull { it.id == pedidoId }?.estado
        if (actual != null && EstadoPedidoMesa.FINALES.contains(actual)) {
            _state.update { it.copy(error = "El pedido ya está finalizado ($actual)") }
            return
        }
        if (!connectivity.isOnline()) {
            _state.update { it.copy(error = "Sin conexión: no se puede cambiar el estado") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val result =
                pedidosMesaRepository.cambiarEstado(
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    sesionId = sesionId,
                    pedidoId = pedidoId,
                    estado = nuevoEstado,
                )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(error = null, info = "Pedido $pedidoId → $nuevoEstado") }
                    cargar(skipSpinner = true)
                },
                onFailure = { e -> _state.update { it.copy(error = e.message ?: "No se pudo cambiar el estado") } },
            )
        }
    }

    /** Limpia el contexto de sesión al salir de la pantalla. */
    fun onSalir() {
        cartRepository.unbindSesionMesa()
    }

    private companion object {
        const val DEFAULT_SELLER_ID = 1
        const val DEFAULT_WAREHOUSE_ID = 1
    }
}

/** Pendientes = estado PENDIENTE (aún no enviados a cocina). */
private fun List<PedidoMesa>.pendientes(): List<PedidoMesa> = filter { it.estado == EstadoPedidoMesa.PENDIENTE }

/**
 * Enviados = todo lo que ya salió de la cocina salvo las canceladas. Mantener un orden estable
 * por `comandaSecuencia` y luego por id ayuda al mozo a ubicar la última comanda primero.
 */
private fun List<PedidoMesa>.enviados(): List<PedidoMesa> =
    filter { it.estado != EstadoPedidoMesa.PENDIENTE && it.estado != EstadoPedidoMesa.CANCELADA }
        .sortedWith(compareByDescending<PedidoMesa> { it.comandaSecuencia }.thenByDescending { it.id })
