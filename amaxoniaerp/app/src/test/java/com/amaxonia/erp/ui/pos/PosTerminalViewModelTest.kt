package com.amaxonia.erp.ui.pos

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleResponseDto
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.SellerSummary
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.repository.CajaRepository
import com.amaxonia.erp.domain.repository.ClientRepository
import com.amaxonia.erp.domain.repository.ProductRepository
import com.amaxonia.erp.domain.repository.SalesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.amaxonia.erp.domain.util.CajaDateParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PosTerminalViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeProductRepository : ProductRepository {
        override suspend fun getAllProducts(page: Int, pageSize: Int, departmentId: Int?): Result<List<Product>> =
            Result.success(
                listOf(
                    Product(
                        id = "1",
                        code = "P01",
                        description = "Test Product",
                        prices = listOf(PriceLevel(label = "General", price = 10.0, pricePlusTax = 10.0)),
                        taxRate = 16.0,
                    ),
                )
            )

        override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> =
            Result.success(emptyList())

        override suspend fun createProduct(product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun getDepartments(): Result<List<DepartmentDto>> = Result.success(emptyList())
    }

    private class FakeClientRepository : ClientRepository {
        override suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>> =
            Result.success(listOf(Client(id = "1", code = "CF", name = "Consumidor Final")))

        override suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> =
            Result.success(emptyList())

        override suspend fun createClient(client: Client): Result<Client> =
            Result.success(client)

        override suspend fun updateClient(id: String, client: Client): Result<Client> =
            Result.success(client)

        var clientBranchesResult: Result<List<ClientBranch>> = Result.success(emptyList())

        override suspend fun getClientSucursales(clientId: String): Result<List<ClientBranch>> =
            clientBranchesResult
    }

    private class FakeSalesRepository : SalesRepository {
        var lastSaleRequest: ProcessSaleRequestDto? = null

        override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPagoDto>> =
            Result.success(
                listOf(
                    FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
                )
            )

        override suspend fun processSale(request: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> {
            lastSaleRequest = request
            return Result.success(ProcessSaleResponseDto(idFactura = "101", codFactura = "FAC-101"))
        }

        override suspend fun getPrintPayload(facturaId: String): Result<com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto> =
            Result.failure(UnsupportedOperationException("Not implemented in test"))
    }

    private class FakeLocalStore : LocalStore(android.content.ContextWrapper(null)) {
        override suspend fun readCompanySession(): com.amaxonia.erp.domain.model.CompanySession? = null
        override suspend fun readActiveSucursal(): Pair<String, String>? = Pair("1", "Sucursal 1")
        override suspend fun readAutoPrintReceipt(): Boolean = false
        override fun customerDisplayEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    }

    private class FakeCajaRepository : CajaRepository {
        val _activeCaja = MutableStateFlow<Caja?>(Caja(idCaja = "1", caja = "Caja Principal"))
        override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()

        val _activeCajaName = MutableStateFlow("Caja Principal")
        override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()

        val _activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)
        override val activeCajaSecuencia: StateFlow<CajaSecuencia?> = _activeCajaSecuencia.asStateFlow()

        val _sessionStatus = MutableStateFlow(CajaSessionStatus.ABIERTA)
        override val sessionStatus: StateFlow<CajaSessionStatus> = _sessionStatus.asStateFlow()

        var statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = null)

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(listOf(_activeCaja.value!!))

        override suspend fun createCaja(request: SaveCajaRequest): Result<Caja> = Result.success(_activeCaja.value!!)

        override suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja> = Result.success(_activeCaja.value!!)

        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success("SEC-002")

        override suspend fun restoreActiveCajaIfValid() {}

        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> {
            if (_activeCaja.value?.idCaja == cajaId) {
                if (statusResponse.isOpen && statusResponse.cajaSecuencia != null) {
                    _activeCajaSecuencia.value = statusResponse.cajaSecuencia
                    _sessionStatus.value = CajaSessionStatus.ABIERTA
                } else {
                    _activeCajaSecuencia.value = null
                    _sessionStatus.value = CajaSessionStatus.PENDIENTE_APERTURA
                }
            }
            return Result.success(statusResponse)
        }

        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> {
            val newSec = CajaSecuencia(
                idCajaSecuencia = "sec-today",
                idCaja = request.idCaja,
                fechaApertura = "${LocalDate.now()} 09:00:00",
                montoApertura = request.montoApertura,
            )
            _activeCajaSecuencia.value = newSec
            statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = newSec)
            return Result.success(statusResponse)
        }

        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
            Result.success(CierreCajaResponse(success = true))

        override suspend fun getCierreSummary(): Result<CierreCajaSummary> =
            Result.success(CierreCajaSummary())

        override suspend fun getCierreSummaryForSequence(caja: Caja, sequenceId: String): Result<CierreCajaSummary> =
            Result.success(CierreCajaSummary())

        override suspend fun setActiveCaja(caja: Caja) {
            _activeCaja.value = caja
        }

        override suspend fun setActiveCaja(id: String, name: String) {}

        override suspend fun clearActiveCaja() {}

        override suspend fun markSequenceClosed() {}

        override suspend fun getActiveCaja(): Pair<String, String>? = Pair("1", "Caja Principal")
    }

    private lateinit var fakeCajaRepo: FakeCajaRepository
    private lateinit var fakeLocalStore: FakeLocalStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeCajaRepo = FakeCajaRepository()
        fakeLocalStore = FakeLocalStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInitialData_detectsPreviousDayCaja() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        fakeCajaRepo.statusResponse = CajaStatusResponse(
            isOpen = true,
            cajaSecuencia = CajaSecuencia(
                idCajaSecuencia = "sec-old",
                idCaja = "1",
                fechaApertura = "$yesterday 08:30:00",
                montoApertura = 50.0,
            ),
        )

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isCajaOpen)
        assertTrue(state.isCajaDiaAnterior)
        assertTrue(state.showAvisoCajaAnterior)
        assertNotNull(state.cajaFechaApertura)
    }

    @Test
    fun openPaymentDialog_blocksWhenCajaClosed() = runTest {
        fakeCajaRepo.statusResponse = CajaStatusResponse(isOpen = false, cajaSecuencia = null)

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        // Add product to cart
        vm.addToCart(
            Product(
                id = "1",
                code = "P01",
                description = "Test",
                prices = listOf(PriceLevel(label = "General", price = 10.0, pricePlusTax = 10.0)),
            )
        )
        vm.openPaymentDialog()

        assertTrue(vm.uiState.value.showCajaWarningDialog)
        assertFalse(vm.uiState.value.showPaymentDialog)
    }

    @Test
    fun renovarCajaDiaAnterior_renewsAndClearsWarning() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        fakeCajaRepo.statusResponse = CajaStatusResponse(
            isOpen = true,
            cajaSecuencia = CajaSecuencia(
                idCajaSecuencia = "sec-old",
                idCaja = "1",
                fechaApertura = "$yesterday 08:30:00",
                montoApertura = 50.0,
            ),
        )

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isCajaDiaAnterior)

        vm.renovarCajaDiaAnterior()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isCajaDiaAnterior)
        assertFalse(vm.uiState.value.showAvisoCajaAnterior)
        assertTrue(vm.uiState.value.isCajaOpen)
    }

    @Test
    fun loadInitialData_populatesSessionMetadata_andRespondsToRepoChanges() = runTest {
        val testCaja = Caja(
            idCaja = "1",
            caja = "Caja Principal",
            sucursalNombre = "Sucursal Norte",
            almacenNombre = "Almacén Central",
        )
        val todaySec = CajaSecuencia(
            idCajaSecuencia = "sec-today",
            idCaja = "1",
            fechaApertura = "${LocalDate.now()} 08:00:00",
            usuarioApertura = "cajero_pedro",
        )
        fakeCajaRepo._activeCaja.value = testCaja
        fakeCajaRepo._activeCajaSecuencia.value = todaySec
        fakeCajaRepo.statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = todaySec)

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Sucursal Norte", state.sucursalNombre)
        assertEquals("Almacén Central", state.almacenNombre)
        assertEquals("cajero_pedro", state.usuarioApertura)
        assertEquals(CajaDateParser.formatDisplayDate(todaySec.fechaApertura), state.cajaFechaApertura)

        // Simular cambio reactivo de caja en repo
        val nuevaCaja = Caja(
            idCaja = "2",
            caja = "Caja Secundaria",
            sucursalNombre = "Sucursal Sur",
            almacenNombre = "Almacén 2",
        )
        val nuevaSec = CajaSecuencia(
            idCajaSecuencia = "sec-2",
            idCaja = "2",
            fechaApertura = "${LocalDate.now()} 10:00:00",
            usuarioApertura = "cajera_maria",
        )
        fakeCajaRepo._activeCaja.value = nuevaCaja
        fakeCajaRepo._activeCajaSecuencia.value = nuevaSec
        advanceUntilIdle()

        val updatedState = vm.uiState.value
        assertEquals("2", updatedState.activeCajaId)
        assertEquals("Caja Secundaria", updatedState.activeCajaName)
        assertEquals("Sucursal Sur", updatedState.sucursalNombre)
        assertEquals("Almacén 2", updatedState.almacenNombre)
        assertEquals("cajera_maria", updatedState.usuarioApertura)
    }

    @Test
    fun sellerSelection_initializesFromCajaAndAllowsManualSelection() = runTest {
        val sellers = listOf(
            SellerSummary(id = 10, nombre = "Vendedor A"),
            SellerSummary(id = 20, nombre = "Vendedor B"),
        )
        fakeCajaRepo._activeCaja.value = Caja(
            idCaja = "1",
            caja = "Caja Principal",
            availableSellers = sellers,
            defaultSellerId = 20,
        )

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        assertEquals(sellers, vm.uiState.value.availableSellers)
        assertEquals(20, vm.uiState.value.selectedSeller?.id)

        vm.selectSeller(sellers[0])
        assertEquals(10, vm.uiState.value.selectedSeller?.id)
        assertFalse(vm.uiState.value.showSellerSheet)
    }

    @Test
    fun clientBranches_singleBranch_autoSelectsBranch() = runTest {
        val clientRepo = FakeClientRepository()
        val branch = ClientBranch(
            sucursalId = 101,
            clienteCodigo = "CLI-1",
            nombreSucursal = "Casa Matriz",
            direccion = "Calle 50, Edif 12",
            telefonoContacto = "507-6000-1111",
        )
        clientRepo.clientBranchesResult = Result.success(listOf(branch))

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = clientRepo,
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val testClient = Client(id = "1", code = "CLI-1", name = "Empresa ABC")
        vm.selectClient(testClient)
        advanceUntilIdle()

        assertEquals(testClient, vm.uiState.value.selectedClient)
        assertEquals(1, vm.uiState.value.clientBranches.size)
        assertEquals(branch, vm.uiState.value.selectedClientBranch)
        assertFalse(vm.uiState.value.branchSelectionRequiredError)
    }

    @Test
    fun clientBranches_multipleBranches_requiresSelectionBeforePayment() = runTest {
        val todaySec = CajaSecuencia(
            idCajaSecuencia = "sec-today",
            idCaja = "1",
            fechaApertura = "${LocalDate.now()} 08:00:00",
            usuarioApertura = "cajero_test",
        )
        fakeCajaRepo._activeCajaSecuencia.value = todaySec
        fakeCajaRepo.statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = todaySec)

        val clientRepo = FakeClientRepository()
        val branch1 = ClientBranch(sucursalId = 1, clienteCodigo = "CLI-2", nombreSucursal = "Sucursal Albrook")
        val branch2 = ClientBranch(sucursalId = 2, clienteCodigo = "CLI-2", nombreSucursal = "Sucursal David")
        clientRepo.clientBranchesResult = Result.success(listOf(branch1, branch2))

        val productRepo = FakeProductRepository()
        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = clientRepo,
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val product = productRepo.getAllProducts(1, 1).getOrThrow().first()
        vm.addToCart(product)

        val testClient = Client(id = "2", code = "CLI-2", name = "Corporacion XYZ")
        vm.selectClient(testClient)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.clientBranches.size)
        assertNull(vm.uiState.value.selectedClientBranch)

        vm.openPaymentDialog()
        assertTrue(vm.uiState.value.branchSelectionRequiredError)
        assertFalse(vm.uiState.value.showPaymentDialog)

        vm.selectClientBranch(branch2)
        assertFalse(vm.uiState.value.branchSelectionRequiredError)
        assertEquals(branch2, vm.uiState.value.selectedClientBranch)

        vm.openPaymentDialog()
        assertTrue(vm.uiState.value.showPaymentDialog)
    }

    @Test
    fun processSale_includesSellerAndSelectedClientBranch() = runTest {
        val todaySec = CajaSecuencia(
            idCajaSecuencia = "sec-today",
            idCaja = "1",
            fechaApertura = "${LocalDate.now()} 08:00:00",
            usuarioApertura = "cajero_test",
        )
        fakeCajaRepo._activeCajaSecuencia.value = todaySec
        fakeCajaRepo.statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = todaySec)

        val salesRepo = FakeSalesRepository()
        val clientRepo = FakeClientRepository()
        val branch = ClientBranch(
            sucursalId = 55,
            clienteCodigo = "CLI-3",
            nombreSucursal = "Sucursal San Francisco",
            direccion = "Via Porras",
            telefonoContacto = "6234-5678",
        )
        clientRepo.clientBranchesResult = Result.success(listOf(branch))

        val seller = SellerSummary(id = 88, nombre = "Vendedor Estrella")
        fakeCajaRepo._activeCaja.value = Caja(
            idCaja = "1",
            caja = "Caja Principal",
            availableSellers = listOf(seller),
            defaultSellerId = 88,
        )

        val productRepo = FakeProductRepository()
        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = clientRepo,
            cajaRepository = fakeCajaRepo,
            salesRepository = salesRepo,
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val product = productRepo.getAllProducts(1, 1).getOrThrow().first()
        vm.addToCart(product)

        val testClient = Client(id = "3", code = "CLI-3", name = "Distribuidora Beta")
        vm.selectClient(testClient)
        advanceUntilIdle()

        vm.openPaymentDialog()
        vm.onReceivedAmountChange("10.00")
        vm.processSale()
        advanceUntilIdle()

        val lastRequest = salesRepo.lastSaleRequest
        assertNotNull(lastRequest)
        assertEquals(88, lastRequest!!.factura.codVendedor)
        assertEquals(55, lastRequest.factura.clienteSucursalId)
        assertEquals("Via Porras", lastRequest.factura.facturarADireccion)
        assertEquals("6234-5678", lastRequest.factura.facturarATelefono)
        assertEquals(88, lastRequest.items.first().codVendedor)
    }
}
