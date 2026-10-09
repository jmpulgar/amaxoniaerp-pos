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
import com.amaxonia.erp.domain.model.ItemCarrito
import com.amaxonia.erp.domain.model.Promocion
import com.amaxonia.erp.domain.model.PromocionDetalle
import com.amaxonia.erp.domain.repository.PagedProducts
import com.amaxonia.erp.domain.repository.PromotionRepository
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PosTerminalViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeProductRepository(
        private val productsList: List<Product> = listOf(
            Product(
                id = "1",
                code = "P01",
                description = "Test Product",
                department = "1",
                prices = listOf(PriceLevel(label = "General", price = 10.0, pricePlusTax = 10.0)),
                taxRate = 16.0,
            ),
        ),
        private val departmentsList: List<DepartmentDto> = emptyList(),
    ) : ProductRepository {
        override suspend fun getAllProducts(page: Int, pageSize: Int, departmentId: Int?): Result<List<Product>> =
            Result.success(productsList)

        override suspend fun getPagedProducts(
            page: Int,
            pageSize: Int,
            departmentId: Int?,
            search: String?,
        ): Result<PagedProducts> {
            val offset = (page - 1) * pageSize
            val paged = productsList.drop(offset).take(pageSize)
            return Result.success(
                PagedProducts(
                    items = paged,
                    totalCount = productsList.size,
                    page = page,
                    pageSize = pageSize,
                ),
            )
        }

        override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> =
            Result.success(emptyList())

        override suspend fun createProduct(product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun getDepartments(): Result<List<DepartmentDto>> = Result.success(departmentsList)
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

    private class FakeSalesRepository(
        var formasPagoList: List<FormaPagoDto> = listOf(
            FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
        )
    ) : SalesRepository {
        var lastSaleRequest: ProcessSaleRequestDto? = null

        override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPagoDto>> =
            Result.success(formasPagoList)

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
        override fun allowEditPricesFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)
        override suspend fun readAllowEditPrices(): Boolean = true
        override fun allowDiscountsFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)
        override suspend fun readAllowDiscounts(): Boolean = true
    }

    private class FakePromotionRepository(
        var promotionsResult: Result<List<Promocion>> = Result.success(emptyList()),
    ) : PromotionRepository {
        override suspend fun getPromotions(forceRefresh: Boolean): Result<List<Promocion>> = promotionsResult
        override suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>> =
            promotionsResult.map { list ->
                list.filter { it.activo && (it.idItem == productId || it.detalles.any { d -> d.idItem == productId }) }
            }
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
    fun openPaymentDialog_doesNotBlock_whenCajaIsDiaAnterior() = runTest {
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

        // Verify caja is detected as previous day
        assertTrue(vm.uiState.value.isCajaOpen)
        assertTrue(vm.uiState.value.isCajaDiaAnterior)
        assertTrue(vm.uiState.value.showAvisoCajaAnterior)

        // Dismiss the modal (cashier chooses "Continuar")
        vm.dismissAvisoCajaAnterior()
        assertFalse(vm.uiState.value.showAvisoCajaAnterior)

        // Add product and open payment dialog
        vm.addToCart(
            Product(
                id = "1",
                code = "P01",
                description = "Test Product",
                prices = listOf(PriceLevel(label = "General", price = 10.0, pricePlusTax = 10.0)),
            )
        )
        vm.openPaymentDialog()

        // Must NOT be blocked! Payment dialog opens normally
        assertFalse(vm.uiState.value.showCajaWarningDialog)
        assertTrue(vm.uiState.value.showPaymentDialog)
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

    @Test
    fun quantityPicker_opensAndConfirmsQuantity() = runTest {
        val productRepo = FakeProductRepository()
        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val product = productRepo.getAllProducts(1, 1).getOrThrow().first()
        vm.openQuantityPicker(product)
        assertEquals(product, vm.uiState.value.quantityPickerProduct)

        vm.dismissQuantityPicker()
        assertNull(vm.uiState.value.quantityPickerProduct)

        vm.confirmProductQuantity(product, 4.0)
        advanceUntilIdle()

        val cartItem = vm.uiState.value.cart.firstOrNull()
        assertNotNull(cartItem)
        assertEquals(4.0, cartItem!!.quantity, 0.001)
    }

    @Test
    fun editItemPrice_and_discount_and_priceLevel() = runTest {
        val productRepo = FakeProductRepository()
        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val product = productRepo.getAllProducts(1, 1).getOrThrow().first().copy(
            prices = listOf(
                PriceLevel(label = "A", price = 10.0, pricePlusTax = 11.6),
                PriceLevel(label = "B", price = 8.0, pricePlusTax = 9.28),
            ),
        )
        vm.addToCart(product)

        // Manual price update
        vm.updateItemPrice(product.id, 15.0)
        var item = vm.uiState.value.cart.first()
        assertEquals(15.0, item.unitPriceWithTax, 0.001)
        assertTrue(item.isManualPrice)

        // Discount update
        vm.updateItemDiscount(product.id, 20.0)
        item = vm.uiState.value.cart.first()
        assertEquals(20.0, item.discountPercent, 0.001)

        // Price level update
        vm.updateItemPriceLevel(product.id, "B")
        item = vm.uiState.value.cart.first()
        assertEquals(9.28, item.unitPriceWithTax, 0.001)
        assertEquals("B", item.selectedPriceLabel)
        assertFalse(item.isManualPrice)
    }

    @Test
    fun promotion_triggersModal_andAddsPromotionToCart_andUpdatesQuantity_andRemoves() = runTest {
        val productRepo = FakeProductRepository()
        val promoRepo = FakePromotionRepository()
        val product = productRepo.getAllProducts(1, 1).getOrThrow().first()

        val promo = Promocion(
            id = "PROMO-1",
            codigo = "COMBO-1",
            inicio = null,
            fin = null,
            nombre = "Combo Familiar",
            imagen = "",
            descuentoGlobal = BigDecimal.ZERO,
            idItem = product.id,
            activo = true,
            detalles = listOf(
                PromocionDetalle(
                    id = "DET-1",
                    promocionId = "PROMO-1",
                    idItem = product.id,
                    productName = product.description,
                    productCode = product.code,
                    productReference = "",
                    idTipoPrecio = "1",
                    cantidad = BigDecimal("2"),
                    cantidadTotal = BigDecimal("2"),
                    unidadEmpaque = "UNIDAD",
                    descuento = BigDecimal.ZERO,
                    descuentoMonto = BigDecimal.ZERO,
                    precio = BigDecimal("8.0"),
                    impuesto = BigDecimal.ZERO,
                    iva = BigDecimal("16.0"),
                    totalConIva = BigDecimal("18.56"),
                    totalSinIva = BigDecimal("16.00"),
                    grupo = "1",
                    product = product,
                ),
            ),
        )
        promoRepo.promotionsResult = Result.success(listOf(promo))

        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
            promotionRepository = promoRepo,
        )
        advanceUntilIdle()

        // Adding product with promo triggers modal choice
        vm.addToCart(product)
        assertTrue(vm.uiState.value.showPromotionChoice)
        assertEquals(product, vm.uiState.value.pendingPromotionProduct)
        assertEquals(1, vm.uiState.value.promotionOptions.size)

        // Add promo to cart with 2 combos
        vm.addPromotionToCart(promo, times = 2)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showPromotionChoice)
        assertEquals(1, vm.uiState.value.cart.size)
        val cartItem = vm.uiState.value.cart.first()
        assertEquals(4.0, cartItem.quantity, 0.001) // 2 base * 2 times
        assertEquals("PROMO-1", cartItem.promocionId)
        assertEquals(2, cartItem.promocionVeces)

        // Verify displayItems grouping
        val displayItems = vm.uiState.value.displayItems
        assertEquals(1, displayItems.size)
        assertTrue(displayItems.first() is ItemCarrito.PromocionAgrupada)
        val promoGroup = displayItems.first() as ItemCarrito.PromocionAgrupada
        assertEquals("Combo Familiar", promoGroup.promocionNombre)

        // Update promo quantity to 3
        vm.updatePromotionQuantity("PROMO-1", 3)
        assertEquals(6.0, vm.uiState.value.cart.first().quantity, 0.001)
        assertEquals(3, vm.uiState.value.cart.first().promocionVeces)

        // Remove promotion
        vm.removePromotion("PROMO-1")
        assertTrue(vm.uiState.value.cart.isEmpty())
        assertTrue(vm.uiState.value.displayItems.isEmpty())
    }

    @Test
    fun processSale_includesPromotionDataAndManualPrice() = runTest {
        val productRepo = FakeProductRepository()
        val salesRepo = FakeSalesRepository()
        val product = productRepo.getAllProducts(1, 1).getOrThrow().first()

        val promo = Promocion(
            id = "PROMO-99",
            codigo = "PROMO-CODE",
            inicio = null,
            fin = null,
            nombre = "Promo Especial",
            imagen = "",
            descuentoGlobal = BigDecimal.ZERO,
            idItem = product.id,
            activo = true,
            detalles = listOf(
                PromocionDetalle(
                    id = "DET-99",
                    promocionId = "PROMO-99",
                    idItem = product.id,
                    productName = product.description,
                    productCode = product.code,
                    productReference = "",
                    idTipoPrecio = "1",
                    cantidad = BigDecimal("1"),
                    cantidadTotal = BigDecimal("1"),
                    unidadEmpaque = "UNIDAD",
                    descuento = BigDecimal.ZERO,
                    descuentoMonto = BigDecimal.ZERO,
                    precio = BigDecimal("5.0"),
                    impuesto = BigDecimal.ZERO,
                    iva = BigDecimal("16.0"),
                    totalConIva = BigDecimal("5.80"),
                    totalSinIva = BigDecimal("5.00"),
                    grupo = "1",
                    product = product,
                ),
            ),
        )

        val vm = PosTerminalViewModel(
            productRepository = productRepo,
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = salesRepo,
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        vm.addPromotionToCart(promo, times = 1)
        vm.openPaymentDialog()
        vm.onReceivedAmountChange("10.00")
        vm.processSale()
        advanceUntilIdle()

        val lastRequest = salesRepo.lastSaleRequest
        assertNotNull(lastRequest)
        val saleItem = lastRequest!!.items.first()
        assertEquals("PROMO-99", saleItem.promocionId)
        assertEquals("PROMO-CODE", saleItem.promocionCodigo)
        assertEquals("Promo Especial", saleItem.promocionNombre)
        assertEquals(1.0, saleItem.promocionCantidad, 0.001)
    }

    @Test
    fun posCartSummary_calculatesGrossSubtotalAndDiscountTotalCorrectly() {
        val product = Product(
            id = "1",
            code = "P1",
            description = "Producto Test",
            prices = listOf(PriceLevel(label = "General", price = 100.0, pricePlusTax = 116.0)),
            isExempt = false,
            taxRate = 16.0,
        )

        val cartItem = CartItem(
            product = product,
            quantity = 2.0,
            unitPriceWithTax = 116.0,
            discountPercent = 20.0,
        )

        val state = PosUiState(cart = listOf(cartItem))
        val summary = state.summary

        // grossSubtotal = 100.0 * 2 = 200.0
        assertEquals(200.0, summary.grossSubtotal, 0.01)
        // discountTotal = 200.0 * 0.20 = 40.0
        assertEquals(40.0, summary.discountTotal, 0.01)
        // net subtotal = 160.0
        assertEquals(160.0, summary.subtotal, 0.01)
        // tax = 160.0 * 0.16 = 25.60
        assertEquals(25.60, summary.tax, 0.01)
        // total = 185.60
        assertEquals(185.60, summary.total, 0.01)
    }

    @Test
    fun selectPaymentMethod_nonCashAutoPopulatesExactTotal() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        vm.addToCart(
            Product(
                id = "1",
                code = "P1",
                description = "Item",
                prices = listOf(PriceLevel(label = "General", price = 50.0, pricePlusTax = 58.0)),
                taxRate = 16.0,
            )
        )
        val total = vm.uiState.value.summary.total
        assertTrue(total > 0.0)

        vm.openPaymentDialog()
        vm.onReceivedAmountChange("100.00")
        assertEquals("100.00", vm.uiState.value.receivedAmountText)

        val cardMethod = FormaPagoDto(idFormaPago = 2, siglas = "TARJ", codigo = "TARJETA", descripcion = "Tarjeta")
        vm.selectPaymentMethod(cardMethod)

        assertEquals(String.format(java.util.Locale.US, "%.2f", total), vm.uiState.value.receivedAmountText)
    }

    @Test
    fun setExactAmount_setsReceivedAmountToExactTotal() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        vm.addToCart(
            Product(
                id = "1",
                code = "P1",
                description = "Item",
                prices = listOf(PriceLevel(label = "General", price = 35.0, pricePlusTax = 35.0)),
                isExempt = true,
                taxRate = 0.0,
            )
        )
        val total = vm.uiState.value.summary.total

        vm.openPaymentDialog()
        vm.onReceivedAmountChange("50.00")
        assertEquals("50.00", vm.uiState.value.receivedAmountText)

        vm.setExactAmount()
        assertEquals(String.format(java.util.Locale.US, "%.2f", total), vm.uiState.value.receivedAmountText)
    }

    @Test
    fun paymentHelpers_isCashAndCalculateSuggestedBillsWorkProperly() {
        val cash1 = FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo")
        val cash2 = FormaPagoDto(idFormaPago = 1, siglas = "EFEC", codigo = "CASH", descripcion = "Efectivo en Bs")
        val card = FormaPagoDto(idFormaPago = 2, siglas = "TDD", codigo = "DEBITO", descripcion = "Tarjeta Débito")
        val trans = FormaPagoDto(idFormaPago = 3, siglas = "TRANS", codigo = "TRANSFERENCIA", descripcion = "Transferencia")

        assertTrue(isCashPaymentMethod(cash1))
        assertTrue(isCashPaymentMethod(cash2))
        assertFalse(isCashPaymentMethod(card))
        assertFalse(isCashPaymentMethod(trans))
    }

    @Test
    fun paymentDialog_webAlignedBehavior_prefill_badges_and_keypad() = runTest {
        val today = LocalDate.now().toString()
        val todaySec = CajaSecuencia(
            idCajaSecuencia = "sec-today",
            idCaja = "1",
            fechaApertura = "$today 09:00:00",
            montoApertura = 100.0,
        )
        fakeCajaRepo.statusResponse = CajaStatusResponse(
            isOpen = true,
            cajaSecuencia = todaySec,
        )
        val salesRepo = FakeSalesRepository(
            formasPagoList = listOf(
                FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
                FormaPagoDto(idFormaPago = 2, siglas = "TARJ", codigo = "TARJETA", descripcion = "Tarjeta"),
            )
        )
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = salesRepo,
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        vm.addToCart(
            Product(
                id = "1",
                code = "P1",
                description = "Item",
                prices = listOf(PriceLevel(label = "General", price = 50.0, pricePlusTax = 50.0)),
                isExempt = true,
                taxRate = 0.0,
            )
        )

        // 1. Abrir diálogo de pago: Efectivo debe precargarse al 100% ($50.00)
        vm.openPaymentDialog()
        val state1 = vm.uiState.value
        assertTrue(state1.showPaymentDialog)
        assertEquals(50.0, state1.paymentsMap[1] ?: 0.0, 0.001)
        assertEquals("50.00", state1.paymentInputTexts[1])
        assertEquals(1, state1.activePaymentInputMethodId)

        // 2. Limpiar fila de efectivo
        vm.onClearSinglePaymentMethod(1)
        val state2 = vm.uiState.value
        assertNull(state2.paymentsMap[1])
        assertNull(state2.paymentInputTexts[1])

        // 3. Clic en badge de Tarjeta (id 2): debe absorber la diferencia completa ($50.00)
        val cardMethod = FormaPagoDto(idFormaPago = 2, siglas = "TARJ", codigo = "TARJETA", descripcion = "Tarjeta")
        vm.onPaymentMethodBadgeClick(cardMethod)
        val state3 = vm.uiState.value
        assertEquals(50.0, state3.paymentsMap[2] ?: 0.0, 0.001)
        assertEquals(2, state3.activePaymentInputMethodId)

        // 4. Teclear en keypad: cambiar Tarjeta a $20
        vm.onKeypadInput("C")
        vm.onKeypadInput("2")
        vm.onKeypadInput("0")
        val state4 = vm.uiState.value
        assertEquals(20.0, state4.paymentsMap[2] ?: 0.0, 0.001)
        assertEquals("20", state4.paymentInputTexts[2])

        // 5. Clic en badge de Efectivo (id 1): debe absorber el restante ($30.00)
        val cashMethod = FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo")
        vm.onPaymentMethodBadgeClick(cashMethod)
        val state5 = vm.uiState.value
        assertEquals(30.0, state5.paymentsMap[1] ?: 0.0, 0.001)
        assertEquals("30.00", state5.paymentInputTexts[1])

        // 6. Limpiar todo
        vm.clearPayments()
        val state6 = vm.uiState.value
        assertTrue(state6.paymentsMap.isEmpty())
        assertTrue(state6.paymentInputTexts.isEmpty())

        // 7. Sumar billete de $50 al efectivo
        vm.onAddCashDenomination(50.0)
        val state7 = vm.uiState.value
        assertEquals(50.0, state7.paymentsMap[1] ?: 0.0, 0.001)
        assertEquals("50.00", state7.paymentInputTexts[1])
    }

    @Test
    fun openProductDialog_and_dismissProductDialog_updatesState() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showProductDialog)
        assertEquals("", vm.uiState.value.productDialogQuery)

        vm.openProductDialog("arroz")
        assertTrue(vm.uiState.value.showProductDialog)
        assertEquals("arroz", vm.uiState.value.productDialogQuery)

        vm.dismissProductDialog()
        assertFalse(vm.uiState.value.showProductDialog)
        assertEquals("", vm.uiState.value.productDialogQuery)
    }

    @Test
    fun addProductByCode_blankCode_opensProductDialogWithEmptyQuery() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val handled = vm.addProductByCode("   ")
        assertTrue(handled)
        assertTrue(vm.uiState.value.showProductDialog)
        assertEquals("", vm.uiState.value.productDialogQuery)
    }

    @Test
    fun addProductByCode_exactMatch_addsDirectlyToCartWithoutOpeningDialog() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        val handled = vm.addProductByCode("P01")
        assertTrue(handled)
        assertFalse(vm.uiState.value.showProductDialog)
        assertEquals(1, vm.uiState.value.cart.size)
        assertEquals("P01", vm.uiState.value.cart.first().product.code)
    }

    @Test
    fun addProductByCode_noMatch_opensProductDialogWithQuery() = runTest {
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        vm.addProductByCode("desconocido")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showProductDialog)
        assertEquals("desconocido", vm.uiState.value.productDialogQuery)
    }

    @Test
    fun onDepartmentSelected_filtersProductsCorrectly() = runTest {
        val prod1 = Product(id = "1", code = "P01", description = "Coca Cola", department = "1")
        val prod2 = Product(id = "2", code = "P02", description = "Pepsi", department = "1")
        val prod3 = Product(id = "3", code = "P03", description = "Papas Lays", department = "2")
        val dept1 = DepartmentDto(id = 1, name = "Bebidas")
        val dept2 = DepartmentDto(id = 2, name = "Snacks")

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(
                productsList = listOf(prod1, prod2, prod3),
                departmentsList = listOf(dept1, dept2),
            ),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        // Inicial: 3 productos sin filtro
        assertEquals(3, vm.uiState.value.filteredProducts.size)
        assertNull(vm.uiState.value.selectedDepartmentId)

        // Seleccionar Departamento 1 (Bebidas) -> 2 productos
        vm.onDepartmentSelected(1)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedDepartmentId)
        assertEquals(2, vm.uiState.value.filteredProducts.size)
        assertTrue(vm.uiState.value.filteredProducts.all { it.department == "1" })

        // Seleccionar Departamento 2 (Snacks) -> 1 producto
        vm.onDepartmentSelected(2)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedDepartmentId)
        assertEquals(1, vm.uiState.value.filteredProducts.size)
        assertEquals("P03", vm.uiState.value.filteredProducts.first().code)

        // Volver a pulsar Departamento 2 -> Deselecciona (toggle a null) y muestra todos
        vm.onDepartmentSelected(2)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedDepartmentId)
        assertEquals(3, vm.uiState.value.filteredProducts.size)
    }

    @Test
    fun onDepartmentSelected_withSearchQuery_combinesBothFilters() = runTest {
        val prod1 = Product(id = "1", code = "P01", description = "Coca Cola", department = "1")
        val prod2 = Product(id = "2", code = "P02", description = "Pepsi", department = "1")
        val prod3 = Product(id = "3", code = "P03", description = "Papas Lays", department = "2")

        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(
                productsList = listOf(prod1, prod2, prod3),
                departmentsList = listOf(DepartmentDto(id = 1, name = "Bebidas")),
            ),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        // Filtrar por Departamento 1 (Bebidas)
        vm.onDepartmentSelected(1)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredProducts.size)

        // Filtrar además por búsqueda "Pepsi"
        vm.onSearchQueryChange("Pepsi")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredProducts.size)
        assertEquals("P02", vm.uiState.value.filteredProducts.first().code)

        // Búsqueda que no coincide en departamento 1
        vm.onSearchQueryChange("Papas")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.filteredProducts.size)
    }

    @Test
    fun catalogPagination_navigatesBetweenPages() = runTest {
        val manyProducts = (1..25).map { i ->
            Product(id = "$i", code = "P$i", description = "Product $i", department = "1")
        }
        val vm = PosTerminalViewModel(
            productRepository = FakeProductRepository(productsList = manyProducts),
            clientRepository = FakeClientRepository(),
            cajaRepository = fakeCajaRepo,
            salesRepository = FakeSalesRepository(),
            localStore = fakeLocalStore,
        )
        advanceUntilIdle()

        // Página 1 inicial
        assertEquals(1, vm.uiState.value.catalogCurrentPage)
        assertTrue(vm.uiState.value.catalogTotalPages >= 2)

        // Navegar a siguiente página
        vm.onCatalogNextPage()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.catalogCurrentPage)

        // Navegar a página anterior
        vm.onCatalogPrevPage()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.catalogCurrentPage)

        // Intentar ir a página previa en página 1 no debe cambiar
        vm.onCatalogPrevPage()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.catalogCurrentPage)
    }
}


