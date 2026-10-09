package com.amaxonia.erp.ui.history

import com.amaxonia.erp.data.remote.dto.FacturaDetalleItemDto
import com.amaxonia.erp.data.remote.dto.FacturaDetalleResponseDto
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.ElectronicInvoiceStatus
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import com.amaxonia.erp.domain.model.InvoiceHistoryPage
import com.amaxonia.erp.domain.model.InvoiceHistorySummary
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.model.Transaction
import com.amaxonia.erp.domain.model.TransactionStatus
import com.amaxonia.erp.domain.repository.CajaRepository
import com.amaxonia.erp.domain.repository.InvoiceHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeInvoiceHistoryRepository : InvoiceHistoryRepository {
        val filters = mutableListOf<InvoiceHistoryFilter>()
        var detailResult: Result<FacturaDetalleResponseDto> =
            Result.success(
                FacturaDetalleResponseDto(
                    idFactura = "1",
                    codFactura = "INV-001",
                    items =
                        listOf(
                            FacturaDetalleItemDto(
                                id = "1",
                                codigo = "P1",
                                descripcion = "Producto 1",
                                cantidad = 2.0,
                                precioUnitario = 50.0,
                                totalConIva = 100.0,
                            ),
                        ),
                ),
            )

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            filters.add(filter)
            return Result.success(
                InvoiceHistoryPage(
                    transactions =
                        listOf(
                            Transaction(
                                id = "1",
                                invoiceNumber = "INV-001",
                                time = "10:30",
                                amount = 100.0,
                                currency = "USD",
                                status = TransactionStatus.PAID,
                                dateHeader = "Hoy",
                            ),
                        ),
                    total = 250,
                    isOffline = false,
                ),
            )
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> {
            return Result.success(
                InvoiceHistorySummary(
                    totalFacturas = 250,
                    ventasNetas = 13500.0,
                    moneda = "USD",
                ),
            )
        }

        override suspend fun getTransactionById(id: String): Result<Transaction> {
            return Result.success(
                Transaction(
                    id = id,
                    invoiceNumber = "INV-001",
                    time = "10:30",
                    amount = 100.0,
                    currency = "USD",
                    status = TransactionStatus.PAID,
                    dateHeader = "Hoy",
                ),
            )
        }

        override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> {
            return detailResult
        }

        override suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray> {
            return Result.success(ByteArray(16))
        }

        override suspend fun resendElectronicInvoice(invoiceId: String): Result<com.amaxonia.erp.data.remote.dto.ElectronicInvoiceResultDto> {
            return Result.success(
                com.amaxonia.erp.data.remote.dto.ElectronicInvoiceResultDto(
                    success = true,
                    cufe = "CUFE-123456",
                    numeroDocumentoFiscal = "DF-001",
                ),
            )
        }
    }

    private class FakeCajaRepository : CajaRepository {
        val cajaFlow = MutableStateFlow<Caja?>(Caja(idCaja = "caja-1", caja = "Caja Principal"))
        override val activeCaja: StateFlow<Caja?> = cajaFlow.asStateFlow()
        override val activeCajaName: StateFlow<String> = MutableStateFlow("Caja Principal")
        override val activeCajaSecuencia: StateFlow<CajaSecuencia?> = MutableStateFlow(null)
        override val sessionStatus: StateFlow<CajaSessionStatus> = MutableStateFlow(CajaSessionStatus.SIN_CAJA)

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(emptyList())
        override suspend fun createCaja(request: SaveCajaRequest): Result<Caja> = error("Not implemented")
        override suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja> = error("Not implemented")
        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success("1")
        override suspend fun restoreActiveCajaIfValid() {}
        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> = error("Not implemented")
        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> = error("Not implemented")
        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> = error("Not implemented")
        override suspend fun getCierreSummary(): Result<CierreCajaSummary> = error("Not implemented")
        override suspend fun getCierreSummaryForSequence(caja: Caja, sequenceId: String): Result<CierreCajaSummary> = error("Not implemented")
        override suspend fun setActiveCaja(caja: Caja) {}
        override suspend fun setActiveCaja(id: String, name: String) {}
        override suspend fun clearActiveCaja() {}
        override suspend fun markSequenceClosed() {}
        override suspend fun getActiveCaja(): Pair<String, String>? = null
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateLoadsPageAndBackendSummary() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val fixedToday = LocalDate.of(2026, 9, 3)
        val viewModel = HistoryViewModel(repository, FakeCajaRepository(), todayProvider = { fixedToday })

        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.transactions.size)
        assertEquals(250L, viewModel.state.value.totalTransactions)
        assertEquals(250, viewModel.state.value.summary.totalFacturas)
        assertEquals(1, repository.filters.size)
        assertEquals(
            InvoiceHistoryFilter(
                fechaInicio = "2026-09-03",
                fechaFin = "2026-09-03",
                cajaId = "caja-1",
            ),
            repository.filters.single(),
        )
    }

    @Test
    fun applyAndClearFiltersUseTheCurrentFilter() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val fixedToday = LocalDate.of(2026, 9, 3)
        val viewModel = HistoryViewModel(repository, FakeCajaRepository(), todayProvider = { fixedToday })
        advanceUntilIdle()

        viewModel.onUsuarioChanged("alice")
        viewModel.onFechaInicioChanged("2026-01-01")
        viewModel.onFechaFinChanged("2026-01-31")
        viewModel.applyFilters()
        advanceUntilIdle()

        assertEquals(
            InvoiceHistoryFilter(
                usuario = "alice",
                fechaInicio = "2026-01-01",
                fechaFin = "2026-01-31",
                cajaId = "caja-1",
            ),
            repository.filters.last(),
        )

        viewModel.clearFilters()
        advanceUntilIdle()

        assertEquals(
            InvoiceHistoryFilter(
                fechaInicio = "2026-09-03",
                fechaFin = "2026-09-03",
                cajaId = "caja-1",
            ),
            repository.filters.last(),
        )
    }

    @Test
    fun searchUsesDebounceBeforeReloading() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository, FakeCajaRepository())
        advanceUntilIdle()
        val initialCalls = repository.filters.size

        viewModel.onSearchChanged("INV-001")
        advanceTimeBy(349)
        assertEquals(initialCalls, repository.filters.size)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("INV-001", repository.filters.last().search)
    }

    @Test
    fun onTransactionClickOpensBottomSheetAndLoadsItems() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository, FakeCajaRepository())
        advanceUntilIdle()

        val tx = Transaction(id = "tx-1", invoiceNumber = "INV-001", time = "10:00", amount = 100.0, dateHeader = "Hoy")
        viewModel.onTransactionClick(tx)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showDetalleSheet)
        assertEquals(tx, state.selectedTransaction)
        assertEquals(1, state.detalleItems.size)
        assertEquals("Producto 1", state.detalleItems[0].descripcion)
    }

    @Test
    fun dismissDetalleResetsBottomSheetState() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository, FakeCajaRepository())
        advanceUntilIdle()

        val tx = Transaction(id = "tx-1", invoiceNumber = "INV-001", time = "10:00", amount = 100.0, dateHeader = "Hoy")
        viewModel.onTransactionClick(tx)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showDetalleSheet)

        viewModel.dismissDetalle()
        assertFalse(viewModel.state.value.showDetalleSheet)
        assertNull(viewModel.state.value.selectedTransaction)
        assertTrue(viewModel.state.value.detalleItems.isEmpty())
    }

    @Test
    fun resendElectronicInvoiceUpdatesStatusOnSuccess() = runTest(testDispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository, FakeCajaRepository())
        advanceUntilIdle()

        val tx = Transaction(
            id = "tx-1",
            invoiceNumber = "INV-001",
            time = "10:00",
            amount = 100.0,
            dateHeader = "Hoy",
            electronicStatus = ElectronicInvoiceStatus.FAILED,
        )
        viewModel.onTransactionClick(tx)
        advanceUntilIdle()

        viewModel.resendElectronicInvoice(tx)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isResendingFE)
        assertEquals(ElectronicInvoiceStatus.SUCCESS, state.selectedTransaction?.electronicStatus)
        assertEquals("CUFE-123456", state.selectedTransaction?.codigoFiscal)
        assertEquals("Factura electrónica transmitida exitosamente", state.detalleMessage)
    }
}
