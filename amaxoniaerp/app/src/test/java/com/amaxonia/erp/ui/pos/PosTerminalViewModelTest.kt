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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    }

    private class FakeSalesRepository : SalesRepository {
        override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPagoDto>> =
            Result.success(
                listOf(
                    FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
                )
            )

        override suspend fun processSale(request: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> =
            Result.success(ProcessSaleResponseDto(idFactura = "101", codFactura = "FAC-101"))
    }

    private class FakeLocalStore : LocalStore(android.content.ContextWrapper(null)) {
        override suspend fun readCompanySession(): com.amaxonia.erp.domain.model.CompanySession? = null
        override suspend fun readActiveSucursal(): Pair<String, String>? = Pair("1", "Sucursal 1")
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

        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> = Result.success(statusResponse)

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
}
