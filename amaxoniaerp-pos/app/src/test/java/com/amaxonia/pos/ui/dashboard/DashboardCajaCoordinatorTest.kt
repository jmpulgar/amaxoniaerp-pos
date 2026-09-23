package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaSessionStatus
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.PendingSalesReader
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.CashCloseTicketPayloadBuilder
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCajaCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildCoordinator(
        fakeRepo: FakeCajaRepository,
    ): DashboardCajaCoordinator {
        val cartRepo = CartRepository()
        val printerProvider = object : PrinterProvider {
            override fun getActivePrinter(): PrinterRepository? = null
            override fun getActiveTicketPrinter(): TicketPrinter? = null
        }
        val contextReader = object : CashCloseContextReader {
            override suspend fun currentCountryCode(): String = "PA"
            override suspend fun selectedPrinterType(): PrinterType = PrinterType.SUNMI_V2
            override suspend fun currentCompany(): CompanyIdentity =
                CompanyIdentity(name = "Test", rif = "J-123456", adminDatabase = "test_db")
        }
        val ticketFormatter = object : CashCloseTicketFormatter {
            override val paymentLabels: List<String> = emptyList()
            override fun format(payload: CashCloseTicketPayload): TicketDocument = TicketDocument(emptyList())
        }
        val printingService = CashClosePrintingService(printerProvider, contextReader, ticketFormatter)
        val productCatalogReader = object : ProductCatalogReader {
            override suspend fun getAllProducts(): Result<List<Product>> = Result.success(emptyList())
            override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> = Result.success(emptyList())
            override suspend fun getProductById(id: String): Result<Product> = Result.failure(AssertionError("unused"))
            override suspend fun getProductStock(id: String): Result<ProductStock> = Result.failure(AssertionError("unused"))
            override suspend fun searchProducts(query: String): Result<List<Product>> = Result.success(emptyList())
        }
        val payloadBuilder =
            CashCloseTicketPayloadBuilder(
                contextReader = contextReader,
                productRepository = productCatalogReader,
                pendingSalesReader = PendingSalesReader { _, _ -> emptyList() },
                ticketFormatter = ticketFormatter,
            )
        val connectivity = object : ConnectivityStatus { override fun isOnline(): Boolean = true }

        return DashboardCajaCoordinator(
            cajaRepository = fakeRepo,
            cartRepository = cartRepo,
            cashClosePrinting = printingService,
            ticketPayloadBuilder = payloadBuilder,
            connectivity = connectivity,
        )
    }

    @Test
    fun `sesion de dia anterior activa aviso y marca isCajaDiaAnterior`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja Principal",
                    descripcion = "Caja 1",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            fakeRepo.setActiveCaja(caja)

            val yesterday = LocalDate.now().minusDays(1).toString()
            val secuenciaAnterior =
                CajaSecuencia(
                    idCajaSecuencia = "seq-old",
                    idCaja = "caja-1",
                    fechaApertura = "$yesterday 09:30:00",
                    montoApertura = 50.0,
                    estatus = 1,
                    usuarioApertura = "cajero",
                    serieSucursal = "SUC-1",
                    idSucursal = 1,
                )

            fakeRepo.activeCajaSecuencia.value = secuenciaAnterior
            advanceUntilIdle()

            assertTrue(state.value.isCajaDiaAnterior)
            assertTrue(state.value.showAvisoCajaAnterior)
            assertTrue(state.value.cajaFechaApertura != null)

            // Descartar aviso
            coordinator.onAction(DashboardCajaUiAction.DismissAvisoCajaAnterior, this, state)
            advanceUntilIdle()

            assertFalse(state.value.showAvisoCajaAnterior)
            assertTrue(state.value.isCajaDiaAnterior) // La caja sigue siendo del día anterior en el banner

            testJob.cancel()
        }

    @Test
    fun `sesion del dia actual no activa aviso`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja Principal",
                    descripcion = "Caja 1",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            fakeRepo.setActiveCaja(caja)

            val today = LocalDate.now().toString()
            val secuenciaHoy =
                CajaSecuencia(
                    idCajaSecuencia = "seq-today",
                    idCaja = "caja-1",
                    fechaApertura = "$today 08:00:00",
                    montoApertura = 50.0,
                    estatus = 1,
                    usuarioApertura = "cajero",
                    serieSucursal = "SUC-1",
                    idSucursal = 1,
                )

            fakeRepo.activeCajaSecuencia.value = secuenciaHoy
            advanceUntilIdle()

            assertFalse(state.value.isCajaDiaAnterior)
            assertFalse(state.value.showAvisoCajaAnterior)
            assertTrue(state.value.cajaFechaApertura != null)

            testJob.cancel()
        }

    @Test
    fun `renovarCajaDiaAnterior abre caja con monto cero y actualiza estado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja Principal",
                    descripcion = "Caja 1",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            fakeRepo.setActiveCaja(caja)

            val yesterday = LocalDate.now().minusDays(1).toString()
            val secuenciaAnterior =
                CajaSecuencia(
                    idCajaSecuencia = "seq-old",
                    idCaja = "caja-1",
                    fechaApertura = "$yesterday 09:30:00",
                    montoApertura = 50.0,
                    estatus = 1,
                    usuarioApertura = "cajero",
                    serieSucursal = "SUC-1",
                    idSucursal = 1,
                )
            fakeRepo.activeCajaSecuencia.value = secuenciaAnterior
            advanceUntilIdle()

            assertTrue(state.value.isCajaDiaAnterior)
            assertTrue(state.value.showAvisoCajaAnterior)

            // Disparamos la acción de renovación automática
            coordinator.onAction(DashboardCajaUiAction.RenovarCajaDiaAnterior, this, state)
            advanceUntilIdle()

            // Verifica que se llamó a openCaja con monto 0.0
            org.junit.Assert.assertEquals("caja-1", fakeRepo.lastOpenRequest?.idCaja)
            org.junit.Assert.assertEquals(0.0, fakeRepo.lastOpenRequest?.montoApertura ?: -1.0, 0.001)

            // El diálogo se ocultó y no queda en estado de renovación
            assertFalse(state.value.showAvisoCajaAnterior)
            assertFalse(state.value.isRenovandoCaja)

            testJob.cancel()
        }

    @Test
    fun `cambiar de caja descarta la sesion de la caja anterior por guarda de correlacion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja1 =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja 1",
                    descripcion = "Caja Uno",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            val caja2 =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja Dos",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C02",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )

            fakeRepo.setActiveCaja(caja1)
            fakeRepo.activeCajaSecuencia.value =
                CajaSecuencia(
                    idCajaSecuencia = "seq-caja-1",
                    idCaja = "caja-1",
                    fechaApertura = "${LocalDate.now()} 08:00:00",
                    montoApertura = 100.0,
                    estatus = 1,
                    usuarioApertura = "cajero",
                    serieSucursal = "SUC-1",
                    idSucursal = 1,
                )
            advanceUntilIdle()

            // Caja 1 está abierta
            org.junit.Assert.assertEquals(CajaSessionStatus.ABIERTA, state.value.cajaSession)

            // Cambiamos a Caja 2
            fakeRepo.setActiveCaja(caja2)
            advanceUntilIdle()

            // La guarda de correlación impide que la secuencia de caja-1 se aplique a caja-2
            org.junit.Assert.assertEquals(CajaSessionStatus.PENDIENTE_APERTURA, state.value.cajaSession)
            org.junit.Assert.assertNull(state.value.cajaFechaApertura)

            testJob.cancel()
        }

    @Test
    fun `accion SelectCaja selecciona caja y solicita apertura si status backend es cerrado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja2 =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja Dos",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C02",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )

            // Seleccionamos caja 2 desde la acción del selector
            coordinator.onAction(DashboardCajaUiAction.SelectCaja(caja2), this, state)
            advanceUntilIdle()

            // Debe haberse establecido como activa en el repositorio y en el estado UI
            org.junit.Assert.assertEquals("caja-2", fakeRepo.activeCaja.value?.idCaja)
            org.junit.Assert.assertEquals("caja-2", state.value.activeCajaId)
            org.junit.Assert.assertEquals("Caja 2", state.value.cajaPrincipalNombre)
            // Dado que FakeCajaRepository devuelve isOpen = false por defecto, debe solicitar apertura
            assertTrue(state.value.showAperturaPrompt)
            org.junit.Assert.assertEquals(caja2, state.value.aperturaCandidate)
            assertFalse(state.value.showCajaSelector)

            testJob.cancel()
        }

    @Test
    fun `selectCaja actualiza inmediatamente nombre de caja y activeCajaId sin lag visual`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(
                DashboardState(
                    activeCajaId = "caja-1",
                    cajaPrincipalNombre = "Caja 1",
                    cajaSession = CajaSessionStatus.ABIERTA,
                )
            )

            val caja2 =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja Dos",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C02",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )

            // Disparamos SelectCaja
            coordinator.onAction(DashboardCajaUiAction.SelectCaja(caja2), this, state)

            // Síncronamente antes de advanceUntilIdle, el estado ya debe reflejar Caja 2 y VERIFICANDO
            org.junit.Assert.assertEquals("caja-2", state.value.activeCajaId)
            org.junit.Assert.assertEquals("Caja 2", state.value.cajaPrincipalNombre)
            org.junit.Assert.assertEquals(CajaSessionStatus.VERIFICANDO, state.value.cajaSession)
            assertFalse(state.value.showCajaSelector)
        }

    @Test
    fun `loadCajas purga caja activa en cache si no esta en las cajas asignadas y autoselecciona la valida`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            val cajaFantasma =
                Caja(
                    idCaja = "caja-fantasma-no-asignada",
                    codCaja = "CF",
                    caja = "Caja Fantasma",
                    descripcion = "No Asignada",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "CF01",
                    defaultSellerId = 99,
                    defaultSellerName = "Otro",
                    availableSellers = emptyList(),
                )
            val cajaAsignada =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja Asignada",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C02",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )

            // Simulamos que en caché o memoria estaba la caja fantasma
            fakeRepo.activeCajaState.value = cajaFantasma
            // El backend solo devuelve las cajas asignadas al usuario actual
            fakeRepo.cajasList = listOf(cajaAsignada)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            // La caja fantasma debe haber sido descartada y la asignada auto-seleccionada
            org.junit.Assert.assertEquals("caja-2", fakeRepo.activeCaja.value?.idCaja)
            org.junit.Assert.assertEquals(1, state.value.availableCajas.size)
            org.junit.Assert.assertEquals("caja-2", state.value.availableCajas.first().idCaja)

            testJob.cancel()
        }

    @Test
    fun `cambiar de caja abierta de dia anterior a caja cerrada no muestra aviso de dia anterior y solicita apertura`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja1 =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja 1",
                    descripcion = "Caja Uno",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            val caja2 =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja Dos",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C02",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )

            // Configuramos caja 1 abierta desde ayer
            fakeRepo.setActiveCaja(caja1)
            val yesterday = LocalDate.now().minusDays(1).toString()
            fakeRepo.activeCajaSecuencia.value =
                CajaSecuencia(
                    idCajaSecuencia = "seq-caja-1-ayer",
                    idCaja = "caja-1",
                    fechaApertura = "$yesterday 09:00:00",
                    montoApertura = 100.0,
                    estatus = 1,
                    usuarioApertura = "cajero",
                    serieSucursal = "SUC-1",
                    idSucursal = 1,
                )
            advanceUntilIdle()

            // Confirmamos que en Caja 1 el aviso de día anterior está activo
            assertTrue(state.value.showAvisoCajaAnterior)
            assertTrue(state.value.isCajaDiaAnterior)

            // Ahora el usuario cambia a Caja 2 (la cual está cerrada en backend)
            coordinator.onAction(DashboardCajaUiAction.SelectCaja(caja2), this, state)
            advanceUntilIdle()

            // Verificamos que NO muestre aviso de día anterior, NO considere día anterior, y solicite aperturar Caja 2
            assertFalse(state.value.showAvisoCajaAnterior)
            assertFalse(state.value.isCajaDiaAnterior)
            assertTrue(state.value.showAperturaPrompt)
            org.junit.Assert.assertEquals("caja-2", state.value.aperturaCandidate?.idCaja)
            org.junit.Assert.assertEquals("caja-2", fakeRepo.activeCaja.value?.idCaja)

            testJob.cancel()
        }

    @Test
    fun `cambio de caja no hereda secuencia de otra caja ni se marca abierta si no coincide idCaja`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val caja1 = Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "Vendedor", availableSellers = emptyList())
            val caja2 = Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "Vendedor", availableSellers = emptyList())

            fakeRepo.setActiveCaja(caja1)
            fakeRepo.activeCajaSecuencia.value = CajaSecuencia(
                idCajaSecuencia = "seq-caja-1",
                idCaja = "caja-1",
                fechaApertura = "${LocalDate.now()} 08:00:00",
                montoApertura = 50.0,
                estatus = 1,
                usuarioApertura = "cajero",
                serieSucursal = "SUC-1",
                idSucursal = 1,
            )
            advanceUntilIdle()

            org.junit.Assert.assertEquals(CajaSessionStatus.ABIERTA, state.value.cajaSession)

            // Switch to Caja 2, simulating that fakeRepo still temporarily holds seq-caja-1
            fakeRepo.activeCajaState.value = caja2
            advanceUntilIdle()

            // Coordinator must detect idCaja mismatch and NOT consider it open
            org.junit.Assert.assertEquals(CajaSessionStatus.PENDIENTE_APERTURA, state.value.cajaSession)
            org.junit.Assert.assertNull(state.value.cajaFechaApertura)

            testJob.cancel()
        }

    @Test
    fun `selector permanece abierto cuando fetch forceShowSelector corre mientras carga en segundo plano`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            fakeRepo.cajasList = listOf(
                Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList()),
                Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList()),
            )

            // Usuario abre selector explícitamente
            coordinator.onAction(DashboardCajaUiAction.Fetch(forceShowSelector = true), this, state)
            advanceUntilIdle()

            // El selector debe permanecer abierto
            assertTrue(state.value.showCajaSelector)
            org.junit.Assert.assertEquals(2, state.value.availableCajas.size)

            testJob.cancel()
        }

    @Test
    fun `caja activa actualiza sucursal y almacen en el estado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            val cajaConAlmacenNombre =
                Caja(
                    idCaja = "caja-1",
                    codCaja = "C1",
                    caja = "Caja Principal",
                    descripcion = "Caja 1",
                    estatus = 1,
                    idSucursal = 1,
                    serieCaja = "C01",
                    sucursalNombre = "Sucursal Norte",
                    almacenNombre = "Almacén Central",
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            fakeRepo.setActiveCaja(cajaConAlmacenNombre)
            advanceUntilIdle()

            org.junit.Assert.assertEquals("Sucursal Norte", state.value.sucursalNombre)
            org.junit.Assert.assertEquals("Almacén Central", state.value.almacenNombre)

            val cajaConIdAlmacen =
                Caja(
                    idCaja = "caja-2",
                    codCaja = "C2",
                    caja = "Caja 2",
                    descripcion = "Caja 2",
                    estatus = 1,
                    idSucursal = 2,
                    serieCaja = "C02",
                    sucursalNombre = "Sucursal Sur",
                    defaultWarehouseId = 3,
                    defaultSellerId = 1,
                    defaultSellerName = "Vendedor",
                    availableSellers = emptyList(),
                )
            fakeRepo.setActiveCaja(cajaConIdAlmacen)
            advanceUntilIdle()

            org.junit.Assert.assertEquals("Sucursal Sur", state.value.sucursalNombre)
            org.junit.Assert.assertEquals("Almacén 3", state.value.almacenNombre)

            testJob.cancel()
        }

    @Test
    fun `loadCajas con multiples cajas y una abierta autoselecciona la abierta sin mostrar selector`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            val caja1 = Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja2 = Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja3 = Caja(idCaja = "caja-3", codCaja = "C3", caja = "Caja 3", descripcion = "Caja 3", estatus = 1, idSucursal = 1, serieCaja = "C03", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())

            fakeRepo.cajasList = listOf(caja1, caja2, caja3)
            fakeRepo.openCajaIds = setOf("caja-2")

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            org.junit.Assert.assertEquals("caja-2", fakeRepo.activeCaja.value?.idCaja)
            assertFalse(state.value.showCajaSelector)
            org.junit.Assert.assertEquals(CajaSessionStatus.ABIERTA, state.value.cajaSession)

            testJob.cancel()
        }

    @Test
    fun `loadCajas con multiples cajas abiertas autoselecciona la primera abierta en orden de lista`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            val caja1 = Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja2 = Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja3 = Caja(idCaja = "caja-3", codCaja = "C3", caja = "Caja 3", descripcion = "Caja 3", estatus = 1, idSucursal = 1, serieCaja = "C03", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())

            fakeRepo.cajasList = listOf(caja1, caja2, caja3)
            // Cajas 2 y 3 abiertas, caja 1 cerrada
            fakeRepo.openCajaIds = setOf("caja-2", "caja-3")

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            // Debe autoseleccionar caja-2 por ser la primera abierta en el orden de la lista
            org.junit.Assert.assertEquals("caja-2", fakeRepo.activeCaja.value?.idCaja)
            assertFalse(state.value.showCajaSelector)
            org.junit.Assert.assertEquals(CajaSessionStatus.ABIERTA, state.value.cajaSession)

            testJob.cancel()
        }

    @Test
    fun `loadCajas con todas las cajas abiertas autoselecciona la primera de la lista`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            val caja1 = Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja2 = Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())

            fakeRepo.cajasList = listOf(caja1, caja2)
            // Todas abiertas
            fakeRepo.openCajaIds = setOf("caja-1", "caja-2")

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            org.junit.Assert.assertEquals("caja-1", fakeRepo.activeCaja.value?.idCaja)
            assertFalse(state.value.showCajaSelector)
            org.junit.Assert.assertEquals(CajaSessionStatus.ABIERTA, state.value.cajaSession)

            testJob.cancel()
        }

    @Test
    fun `loadCajas con multiples cajas y ninguna abierta muestra selector de cajas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakeRepo = FakeCajaRepository()
            val coordinator = buildCoordinator(fakeRepo)
            val state = MutableStateFlow(DashboardState())
            val testJob = kotlinx.coroutines.Job()
            val coordinatorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + testJob)

            val caja1 = Caja(idCaja = "caja-1", codCaja = "C1", caja = "Caja 1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "C01", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())
            val caja2 = Caja(idCaja = "caja-2", codCaja = "C2", caja = "Caja 2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "C02", defaultSellerId = 1, defaultSellerName = "V", availableSellers = emptyList())

            fakeRepo.cajasList = listOf(caja1, caja2)
            fakeRepo.openCajaIds = emptySet()

            coordinator.start(coordinatorScope, state)
            advanceUntilIdle()

            org.junit.Assert.assertNull(fakeRepo.activeCaja.value)
            assertTrue(state.value.showCajaSelector)
            org.junit.Assert.assertEquals(2, state.value.availableCajas.size)

            testJob.cancel()
        }

    private class FakeCajaRepository : CajaRepository {
        val activeCajaState = MutableStateFlow<Caja?>(null)
        override val activeCaja get() = activeCajaState
        override val activeCajaName = MutableStateFlow("Caja Principal")
        override val activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)
        var lastOpenRequest: AperturaRequest? = null
        var cajasList: List<Caja> = emptyList()
        var openCajaIds: Set<String> = emptySet()

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(cajasList)
        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success("001")
        override suspend fun restoreActiveCajaIfValid() = Unit
        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> {
            val isOpen = cajaId in openCajaIds
            val secuencia =
                if (isOpen) {
                    CajaSecuencia(
                        idCajaSecuencia = "seq-$cajaId",
                        idCaja = cajaId,
                        fechaApertura = "${LocalDate.now()} 10:00:00",
                        montoApertura = 100.0,
                        estatus = 1,
                        usuarioApertura = "cajero",
                        serieSucursal = "SUC-1",
                        idSucursal = 1,
                    )
                } else {
                    null
                }
            if (activeCajaState.value?.idCaja == cajaId) {
                activeCajaSecuencia.value = secuencia
            }
            return Result.success(CajaStatusResponse(isOpen = isOpen, cajaSecuencia = secuencia))
        }
        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> {
            lastOpenRequest = request
            return Result.success(CajaStatusResponse(isOpen = true, cajaSecuencia = null))
        }
        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
            Result.success(CierreCajaResponse(success = true, message = "ok"))
        override suspend fun getCierreSummary(): Result<CierreCajaSummary> =
            Result.failure(IllegalStateException("not implemented"))
        override suspend fun setActiveCaja(caja: Caja) {
            if (activeCajaState.value?.idCaja != caja.idCaja) {
                activeCajaSecuencia.value = null
            }
            activeCajaState.value = caja
        }
        override suspend fun clearActiveCaja() {
            activeCajaState.value = null
            activeCajaSecuencia.value = null
        }
        override suspend fun markSequenceClosed() {
            activeCajaSecuencia.value = null
        }
    }
}
