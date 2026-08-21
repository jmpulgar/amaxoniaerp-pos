package com.amaxonia.pos.ui.mesas

import app.cash.turbine.test
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaRequest
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CuentaMesaRepository
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuentaMesaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pedidos =
        listOf(
            pedido(id = 1, estado = EstadoPedidoMesa.ENTREGADA, cantidad = 2.0),
            pedido(id = 2, estado = EstadoPedidoMesa.PENDIENTE, cantidad = 3.0),
        )

    @Test
    fun `load sin caja activa expone el error y no consulta repositorios`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val pedidosRepo = FakePedidosMesaRepository()
            val vm =
                cuentaViewModel(
                    cuentas = cuentas,
                    pedidos = pedidosRepo,
                    caja = null,
                )

            vm.load()
            advanceUntilIdle()

            assertEquals("Debes seleccionar una caja", vm.state.value.error)
            assertEquals(0, cuentas.solicitarCalls)
            assertEquals(0, pedidosRepo.listarCalls)
        }

    @Test
    fun `primer load solicita la cuenta, filtra pedidos entregados y carga cuentas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val pedidosRepo = FakePedidosMesaRepository(pedidos = pedidos)
            val vm = cuentaViewModel(cuentas = cuentas, pedidos = pedidosRepo)

            vm.load()
            advanceUntilIdle()

            assertEquals(1, cuentas.solicitarCalls)
            val state = vm.state.value
            assertEquals(listOf(1), state.pedidos.map { it.id })
            assertTrue(state.cuentas.isNotEmpty())
            assertNull(state.error)
            assertTrue(!state.isLoading)
        }

    @Test
    fun `loads siguientes no vuelven a solicitar la cuenta`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val vm = cuentaViewModel(cuentas = cuentas)

            vm.load()
            advanceUntilIdle()
            vm.load()
            advanceUntilIdle()

            assertEquals(1, cuentas.solicitarCalls)
            assertEquals(2, cuentas.listarCalls)
        }

    @Test
    fun `fallo al solicitar la cuenta expone el mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas =
                FakeCuentaMesaRepository(solicitarResult = Result.failure(IllegalStateException()))
            val vm = cuentaViewModel(cuentas = cuentas)

            vm.load()
            advanceUntilIdle()

            assertEquals("No se pudo solicitar la cuenta", vm.state.value.error)
            assertEquals(0, cuentas.listarCalls)
        }

    @Test
    fun `fallo al listar pedidos propaga el mensaje del error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val pedidosRepo =
                FakePedidosMesaRepository(pedidosResult = Result.failure(IllegalStateException("sesión cerrada")))
            val vm = cuentaViewModel(pedidos = pedidosRepo)

            vm.load()
            advanceUntilIdle()

            assertEquals("sesión cerrada", vm.state.value.error)
        }

    @Test
    fun `updateCantidad normaliza comas y descarta caracteres invalidos`() {
        val vm = cuentaViewModel()

        vm.updateCantidad(7, "1,5x")

        assertEquals("1.5", vm.state.value.cantidades[7])
    }

    @Test
    fun `crearDivision sin cantidades validas expone error de seleccion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val pedidosRepo = FakePedidosMesaRepository(pedidos = pedidos)
            val vm = cuentaViewModel(cuentas = cuentas, pedidos = pedidosRepo)
            vm.load()
            advanceUntilIdle()

            vm.updateCantidad(1, "")
            vm.crearDivision()
            advanceUntilIdle()

            assertEquals("Selecciona al menos una cantidad para dividir la cuenta", vm.state.value.error)
            assertEquals(0, cuentas.crearRequests.size)
        }

    @Test
    fun `crearDivision con cantidad mayor al saldo pendiente expone error y no crea`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val pedidosRepo = FakePedidosMesaRepository(pedidos = pedidos)
            val vm = cuentaViewModel(cuentas = cuentas, pedidos = pedidosRepo)
            vm.load()
            advanceUntilIdle()

            vm.updateCantidad(1, "3")
            vm.crearDivision()
            advanceUntilIdle()

            assertEquals(
                "La cantidad de Cafe supera el saldo pendiente",
                vm.state.value.error,
            )
            assertEquals(0, cuentas.crearRequests.size)
        }

    @Test
    fun `crearDivision valida crea la cuenta con los items y limpia cantidades`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val pedidosRepo = FakePedidosMesaRepository(pedidos = pedidos)
            val vm = cuentaViewModel(cuentas = cuentas, pedidos = pedidosRepo)
            vm.load()
            advanceUntilIdle()

            vm.updateCantidad(1, "2")
            vm.crearDivision()
            advanceUntilIdle()

            val request = cuentas.crearRequests.single()
            assertTrue(!request.incluirTodoPendiente)
            assertEquals(1, request.items.size)
            assertEquals(1, request.items.single().pedidoMesaId)
            assertEquals(2.0, request.items.single().cantidad ?: 0.0, 0.0)
            assertEquals("Cuenta creada", vm.state.value.info)
            assertTrue(
                vm.state.value.cantidades
                    .isEmpty(),
            )
        }

    @Test
    fun `crearCuentaCompleta crea con incluirTodoPendiente`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val vm = cuentaViewModel(cuentas = cuentas)
            vm.load()
            advanceUntilIdle()

            vm.crearCuentaCompleta()
            advanceUntilIdle()

            val request = cuentas.crearRequests.single()
            assertTrue(request.incluirTodoPendiente)
            assertTrue(request.items.isEmpty())
            assertEquals("Cuenta creada", vm.state.value.info)
        }

    @Test
    fun `cancelar con exito informa y recarga`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas = FakeCuentaMesaRepository()
            val vm = cuentaViewModel(cuentas = cuentas)
            vm.load()
            advanceUntilIdle()

            vm.cancelar(CuentaMesaResponse(id = 9))
            advanceUntilIdle()

            assertEquals(listOf(9), cuentas.canceladas)
            assertEquals("Cuenta cancelada", vm.state.value.info)
        }

    @Test
    fun `cancelar con fallo expone el mensaje de error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cuentas =
                FakeCuentaMesaRepository(cancelarResult = Result.failure(IllegalStateException("cuenta facturada")))
            val vm = cuentaViewModel(cuentas = cuentas)
            vm.load()
            advanceUntilIdle()

            vm.cancelar(CuentaMesaResponse(id = 9))
            advanceUntilIdle()

            assertEquals("cuenta facturada", vm.state.value.error)
            assertNull(vm.state.value.info)
        }

    @Test
    fun `pagar emite el efecto Pay con la cuenta`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = cuentaViewModel()
            val cuenta = CuentaMesaResponse(id = 5, total = 12.5)

            vm.effects.test {
                vm.pagar(cuenta)
                assertEquals(CuentaMesaEffect.Pay(cuenta), awaitItem())
                expectNoEvents()
            }
        }

    private fun cuentaViewModel(
        cuentas: FakeCuentaMesaRepository = FakeCuentaMesaRepository(),
        pedidos: FakePedidosMesaRepository = FakePedidosMesaRepository(),
        caja: Caja? = activeCaja,
    ): CuentaMesaViewModel =
        CuentaMesaViewModel(
            areaId = 1,
            mesaId = 2,
            sesionId = 3,
            cuentasRepository = cuentas,
            pedidosRepository = pedidos,
            activeCajaReader =
                object : ActiveCajaReader {
                    override val activeCaja: StateFlow<Caja?> = MutableStateFlow(caja)
                },
        )

    private fun pedido(
        id: Int,
        estado: String,
        cantidad: Double,
    ): PedidoMesa =
        PedidoMesa(
            id = id,
            itemDescripcion = "Cafe",
            itemCantidad = cantidad,
            estado = estado,
        )

    private class FakeCuentaMesaRepository(
        val solicitarResult: Result<Boolean> = Result.success(true),
        val cancelarResult: Result<CuentaMesaResponse> = Result.success(CuentaMesaResponse()),
    ) : CuentaMesaRepository {
        var solicitarCalls = 0
        var listarCalls = 0
        val crearRequests = mutableListOf<CrearCuentaRequest>()
        val canceladas = mutableListOf<Int>()

        override suspend fun listar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
        ): Result<List<CuentaMesaResponse>> {
            listarCalls++
            return Result.success(listOf(CuentaMesaResponse(id = 1, estado = "ACTIVA")))
        }

        override suspend fun obtener(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            cuentaId: Int,
        ) = Result.success(CuentaMesaResponse(id = cuentaId))

        override suspend fun crear(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            request: CrearCuentaRequest,
        ): Result<CuentaMesaResponse> {
            crearRequests += request
            return Result.success(CuentaMesaResponse(id = 2))
        }

        override suspend fun cancelar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            cuentaId: Int,
        ): Result<CuentaMesaResponse> {
            canceladas += cuentaId
            return cancelarResult
        }

        override suspend fun marcarFacturada(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            cuentaId: Int,
            request: MarcarCuentaFacturadaRequest,
        ) = Result.success(MarcarCuentaFacturadaResponse())

        override suspend fun solicitarCuenta(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
        ): Result<Boolean> {
            solicitarCalls++
            return solicitarResult
        }

        override suspend fun cancelarSolicitudCuenta(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
        ) = Result.success(true)
    }

    private class FakePedidosMesaRepository(
        val pedidos: List<PedidoMesa> = emptyList(),
        val pedidosResult: Result<List<PedidoMesa>>? = null,
    ) : PedidosMesaRepository {
        var listarCalls = 0

        override suspend fun listar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            estado: String?,
        ): Result<List<PedidoMesa>> {
            listarCalls++
            return pedidosResult ?: Result.success(pedidos)
        }

        override suspend fun crear(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            request: CrearPedidoMesaRequest,
        ) = Result.success(pedidos)

        override suspend fun enviarComanda(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            request: EnviarComandaRequest,
        ) = Result.success(pedidos)

        override suspend fun cambiarEstado(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
            pedidoId: Int,
            estado: String,
        ) = Result.success(PedidoMesa(id = pedidoId, estado = estado))
    }

    private companion object {
        val activeCaja =
            Caja(
                idCaja = "caja-1",
                codCaja = "C1",
                caja = null,
                descripcion = "Caja",
                estatus = 1,
                idSucursal = 1,
                serieCaja = "S1",
            )
    }
}
