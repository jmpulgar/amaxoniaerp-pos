package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComandaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pedidosRepository = FakePedidosMesaRepository()
    private val cartRepository = CartRepository()

    @Test
    fun `init vincula el carrito con la sesion y carga pedidos vacios`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            advanceUntilIdle()

            assertFalse(cartRepository.sesionMesaId.value == null)
            assertNotNull(cartRepository.sesionMesaId.value)
            assertFalse(vm.state.value.isLoading)
            assertTrue(pedidosRepository.listarCalls.isNotEmpty())
        }

    @Test
    fun `listar separa pendientes de enviados`() =
        runTest(mainDispatcherRule.dispatcher) {
            val pedidos =
                listOf(
                    pedido(1, EstadoPedidoMesa.PENDIENTE, comanda = null),
                    pedido(2, EstadoPedidoMesa.ENVIADA, comanda = 10),
                    pedido(3, EstadoPedidoMesa.EN_PREPARACION, comanda = 10),
                    pedido(4, EstadoPedidoMesa.LISTA, comanda = 10),
                    pedido(5, EstadoPedidoMesa.ENTREGADA, comanda = 9),
                    pedido(6, EstadoPedidoMesa.CANCELADA, comanda = 9),
                )
            val vm = viewModel(pedidos = pedidos)

            advanceUntilIdle()
            val state = vm.state.value

            assertEquals(listOf(1), state.pendientes.map { it.id })
            // Enviados excluye PENDIENTE y CANCELADA, ordenados por comanda desc.
            assertEquals(listOf(4, 3, 2, 5), state.enviados.map { it.id })
        }

    @Test
    fun `enviar comanda crea items nuevos y marca pendientes como enviados`() =
        runTest(mainDispatcherRule.dispatcher) {
            val pendienteBackend = pedido(11, EstadoPedidoMesa.PENDIENTE, comanda = null)
            val vm = viewModel(pedidos = listOf(pendienteBackend))
            cartRepository.addToCart(Product(id = "101", description = "Producto 101"))
            advanceUntilIdle()

            vm.enviarComanda()
            advanceUntilIdle()

            assertEquals(1, pedidosRepository.crearCalls.size)
            assertEquals(true, pedidosRepository.crearCalls.first().request.enviarInmediato)
            assertEquals(listOf(11), pedidosRepository.enviarCalls.first().request.pedidoIds)
            assertTrue(cartRepository.cartItems.value.isEmpty())
            assertFalse(vm.state.value.isSending)
            assertNotNull(vm.state.value.info)
        }

    @Test
    fun `enviar comanda sin items ni pendientes muestra error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.enviarComanda()
            advanceUntilIdle()

            assertNotNull(vm.state.value.error)
            assertEquals(0, pedidosRepository.crearCalls.size)
            assertEquals(0, pedidosRepository.enviarCalls.size)
        }

    @Test
    fun `error del backend en listar se expone en state error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel(listarError = IllegalStateException("Sesión cerrada"))
            advanceUntilIdle()

            assertEquals("Sesión cerrada", vm.state.value.error)
        }

    @Test
    fun `cambiar estado bloqueado si el pedido ya esta finalizado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val entregado = pedido(50, EstadoPedidoMesa.ENTREGADA, comanda = 9)
            val vm = viewModel(pedidos = listOf(entregado))
            advanceUntilIdle()

            vm.cambiarEstado(50, EstadoPedidoMesa.LISTA)
            advanceUntilIdle()

            assertEquals(0, pedidosRepository.cambiarEstadoCalls.size)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun `cambiar estado valido llama al repositorio y recarga`() =
        runTest(mainDispatcherRule.dispatcher) {
            val lista = pedido(60, EstadoPedidoMesa.LISTA, comanda = 12)
            val vm = viewModel(pedidos = listOf(lista))
            advanceUntilIdle()

            vm.cambiarEstado(60, EstadoPedidoMesa.ENTREGADA)
            advanceUntilIdle()

            assertEquals(1, pedidosRepository.cambiarEstadoCalls.size)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `onSalir desvincula el carrito de la sesion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            assertNotNull(cartRepository.sesionMesaId.value)

            vm.onSalir()

            assertNull(cartRepository.sesionMesaId.value)
        }

    // ---------- helpers ----------

    private fun viewModel(
        pedidos: List<PedidoMesa> = emptyList(),
        listarError: Throwable? = null,
        online: Boolean = true,
    ): ComandaViewModel {
        pedidosRepository.pedidos = pedidos
        pedidosRepository.listarError = listarError
        return ComandaViewModel(
            areaId = AREA_ID,
            mesaId = MESA_ID,
            sesionId = SESION_ID,
            pedidosMesaRepository = pedidosRepository,
            cartRepository = cartRepository,
            activeCajaReader = FakeActiveCajaReader(activeCaja()),
            connectivity = ConnectivityStatus { online },
        )
    }

    private fun pedido(
        id: Int,
        estado: String,
        comanda: Int?,
    ) = PedidoMesa(
        id = id,
        sesionMesaId = SESION_ID,
        mesaId = MESA_ID,
        comandaSecuencia = comanda,
        productoId = 1000 + id,
        itemDescripcion = "Producto $id",
        itemCantidad = 1.0,
        itemPrecioSinIva = 10.0,
        itemPIva = 10.0,
        itemTotalSinIva = 10.0,
        itemTotalConIva = 11.0,
        estado = estado,
    )

    private companion object {
        const val CAJA_ID = "caja-1"
        const val AREA_ID = 100
        const val MESA_ID = 1001
        const val SESION_ID = 99

        fun activeCaja() =
            Caja(
                idCaja = CAJA_ID,
                codCaja = "C1",
                descripcion = "Caja principal",
                estatus = 1,
                idSucursal = 7,
                serieCaja = "1",
                defaultWarehouseId = 5,
            )
    }

    private class FakePedidosMesaRepository : PedidosMesaRepository {
        var pedidos: List<PedidoMesa> = emptyList()
        var listarError: Throwable? = null
        val listarCalls = mutableListOf<ListarCall>()
        val crearCalls = mutableListOf<CrearCall>()
        val enviarCalls = mutableListOf<EnviarCall>()
        val cambiarEstadoCalls = mutableListOf<CambiarEstadoCall>()

        data class ListarCall(
            val cajaId: String,
            val areaId: Int,
            val mesaId: Int,
            val sesionId: Int,
            val estado: String?,
        )

        data class CrearCall(
            val cajaId: String,
            val areaId: Int,
            val mesaId: Int,
            val sesionId: Int,
            val request: CrearPedidoMesaRequest,
        )

        data class EnviarCall(
            val cajaId: String,
            val areaId: Int,
            val mesaId: Int,
            val sesionId: Int,
            val request: EnviarComandaRequest,
        )

        data class CambiarEstadoCall(
            val cajaId: String,
            val areaId: Int,
            val mesaId: Int,
            val sesionId: Int,
            val pedidoId: Int,
            val estado: String,
        )

        override suspend fun listar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            estado: String?,
        ): Result<List<PedidoMesa>> {
            listarCalls += ListarCall(cajaId, areaId, mesaId, sesionId, estado)
            listarError?.let { return Result.failure(it) }
            return Result.success(pedidos)
        }

        override suspend fun crear(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            request: CrearPedidoMesaRequest,
        ): Result<List<PedidoMesa>> {
            crearCalls += CrearCall(cajaId, areaId, mesaId, sesionId, request)
            val nuevos =
                request.items.mapIndexed { idx, item ->
                    PedidoMesa(
                        id = 100 + idx,
                        sesionMesaId = sesionId,
                        mesaId = mesaId,
                        comandaSecuencia = 1,
                        productoId = item.productoId,
                        itemDescripcion = item.itemDescripcion,
                        itemCantidad = item.itemCantidad,
                        estado = if (request.enviarInmediato) EstadoPedidoMesa.ENVIADA else EstadoPedidoMesa.PENDIENTE,
                    )
                }
            return Result.success(nuevos)
        }

        override suspend fun enviarComanda(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            request: EnviarComandaRequest,
        ): Result<List<PedidoMesa>> {
            enviarCalls += EnviarCall(cajaId, areaId, mesaId, sesionId, request)
            val actualizados =
                pedidos.map { p ->
                    if (request.pedidoIds.contains(p.id)) {
                        p.copy(estado = EstadoPedidoMesa.ENVIADA, comandaSecuencia = 1)
                    } else {
                        p
                    }
                }
            return Result.success(actualizados.filter { it.id in request.pedidoIds })
        }

        override suspend fun cambiarEstado(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            pedidoId: Int,
            estado: String,
        ): Result<PedidoMesa> {
            cambiarEstadoCalls += CambiarEstadoCall(cajaId, areaId, mesaId, sesionId, pedidoId, estado)
            return Result.success(
                PedidoMesa(
                    id = pedidoId,
                    sesionMesaId = sesionId,
                    mesaId = mesaId,
                    estado = estado,
                ),
            )
        }
    }

    private class FakeActiveCajaReader(
        caja: Caja?,
    ) : ActiveCajaReader {
        override val activeCaja: StateFlow<Caja?> = MutableStateFlow(caja).asStateFlow()
    }
}
