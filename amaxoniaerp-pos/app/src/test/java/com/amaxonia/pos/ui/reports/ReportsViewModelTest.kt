package com.amaxonia.pos.ui.reports

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary
import com.amaxonia.pos.domain.repository.ReportRepository
import com.amaxonia.pos.test.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate.of(2026, 9, 25)

    @Test
    fun `initial state loads with TODAY filter and active caja id`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reportRepo = FakeReportRepository()
            val historyRepo = FakeInvoiceHistoryRepository()
            val cajaRepo = FakeCajaRepository()

            val viewModel = ReportsViewModel(
                reportRepository = reportRepo,
                invoiceHistoryRepository = historyRepo,
                cajaRepository = cajaRepo,
                todayProvider = { fixedToday },
            )

            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(ReportPeriod.TODAY, state.selectedPeriod)
            assertTrue(state.onlyActiveCaja)
            assertEquals("caja-1", state.activeCajaId)
            assertEquals("Caja Principal", state.activeCajaName)

            assertNotNull(state.summary)
            assertEquals(150.0, state.summary?.netSales ?: 0.0, 0.01)

            val lastFilter = reportRepo.lastFilter
            assertNotNull(lastFilter)
            assertEquals("2026-09-25", lastFilter?.fechaInicio)
            assertEquals("2026-09-25", lastFilter?.fechaFin)
            assertEquals("caja-1", lastFilter?.cajaId)
        }

    @Test
    fun `selectPeriod YESTERDAY updates filter to yesterday`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reportRepo = FakeReportRepository()
            val viewModel = ReportsViewModel(
                reportRepository = reportRepo,
                invoiceHistoryRepository = FakeInvoiceHistoryRepository(),
                cajaRepository = FakeCajaRepository(),
                todayProvider = { fixedToday },
            )
            advanceUntilIdle()

            viewModel.selectPeriod(ReportPeriod.YESTERDAY)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(ReportPeriod.YESTERDAY, state.selectedPeriod)

            val lastFilter = reportRepo.lastFilter
            assertEquals("2026-09-24", lastFilter?.fechaInicio)
            assertEquals("2026-09-24", lastFilter?.fechaFin)
            assertEquals("caja-1", lastFilter?.cajaId)
        }

    @Test
    fun `selectPeriod THIS_WEEK updates filter to 7 day range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reportRepo = FakeReportRepository()
            val viewModel = ReportsViewModel(
                reportRepository = reportRepo,
                invoiceHistoryRepository = FakeInvoiceHistoryRepository(),
                cajaRepository = FakeCajaRepository(),
                todayProvider = { fixedToday },
            )
            advanceUntilIdle()

            viewModel.selectPeriod(ReportPeriod.THIS_WEEK)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(ReportPeriod.THIS_WEEK, state.selectedPeriod)

            val lastFilter = reportRepo.lastFilter
            assertEquals("2026-09-19", lastFilter?.fechaInicio)
            assertEquals("2026-09-25", lastFilter?.fechaFin)
        }

    @Test
    fun `selectPeriod THIS_MONTH updates filter to first of month until today`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reportRepo = FakeReportRepository()
            val viewModel = ReportsViewModel(
                reportRepository = reportRepo,
                invoiceHistoryRepository = FakeInvoiceHistoryRepository(),
                cajaRepository = FakeCajaRepository(),
                todayProvider = { fixedToday },
            )
            advanceUntilIdle()

            viewModel.selectPeriod(ReportPeriod.THIS_MONTH)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(ReportPeriod.THIS_MONTH, state.selectedPeriod)

            val lastFilter = reportRepo.lastFilter
            assertEquals("2026-09-01", lastFilter?.fechaInicio)
            assertEquals("2026-09-25", lastFilter?.fechaFin)
        }

    @Test
    fun `toggleOnlyActiveCaja sends null cajaId when false`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reportRepo = FakeReportRepository()
            val viewModel = ReportsViewModel(
                reportRepository = reportRepo,
                invoiceHistoryRepository = FakeInvoiceHistoryRepository(),
                cajaRepository = FakeCajaRepository(),
                todayProvider = { fixedToday },
            )
            advanceUntilIdle()

            viewModel.toggleOnlyActiveCaja()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.onlyActiveCaja)
            val lastFilter = reportRepo.lastFilter
            assertNull(lastFilter?.cajaId)
        }

    @Test
    fun `calculatePaymentBreakdown groups by method and calculates percentages correctly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val transactions = listOf(
                Transaction(
                    id = "1",
                    invoiceNumber = "F001",
                    time = "10:00",
                    amount = 60.0,
                    status = TransactionStatus.PAID,
                    formaPago = "EFECTIVO",
                    dateHeader = "Hoy",
                ),
                Transaction(
                    id = "2",
                    invoiceNumber = "F002",
                    time = "11:00",
                    amount = 40.0,
                    status = TransactionStatus.PAID,
                    formaPago = "TARJETA",
                    dateHeader = "Hoy",
                ),
                Transaction(
                    id = "3",
                    invoiceNumber = "F003",
                    time = "12:00",
                    amount = 50.0,
                    status = TransactionStatus.CANCELLED,
                    formaPago = "EFECTIVO",
                    dateHeader = "Hoy",
                ),
            )

            val viewModel = ReportsViewModel(
                reportRepository = FakeReportRepository(),
                todayProvider = { fixedToday },
            )

            val breakdown = viewModel.calculatePaymentBreakdown(transactions)
            assertEquals(2, breakdown.size)

            val efectivo = breakdown.first { it.name == "Efectivo" }
            assertEquals(60.0, efectivo.amount, 0.01)
            assertEquals(1, efectivo.count)
            assertEquals(0.60f, efectivo.percentage, 0.01f)

            val tarjeta = breakdown.first { it.name == "Tarjeta" }
            assertEquals(40.0, tarjeta.amount, 0.01)
            assertEquals(1, tarjeta.count)
            assertEquals(0.40f, tarjeta.percentage, 0.01f)
        }

    @Test
    fun `calculatePaymentBreakdown supports split payment methods`() =
        runTest(mainDispatcherRule.dispatcher) {
            val transactions = listOf(
                Transaction(
                    id = "1",
                    invoiceNumber = "F001",
                    time = "10:00",
                    amount = 100.0,
                    status = TransactionStatus.PAID,
                    formaPago = "MIXTO",
                    paymentMethods = listOf(
                        TransactionPaymentMethod(description = "Efectivo", amount = 70.0),
                        TransactionPaymentMethod(description = "Transferencia", amount = 30.0),
                    ),
                    dateHeader = "Hoy",
                ),
            )

            val viewModel = ReportsViewModel(
                reportRepository = FakeReportRepository(),
                todayProvider = { fixedToday },
            )

            val breakdown = viewModel.calculatePaymentBreakdown(transactions)
            assertEquals(2, breakdown.size)

            val efectivo = breakdown.first { it.name == "Efectivo" }
            assertEquals(70.0, efectivo.amount, 0.01)
            assertEquals(0.70f, efectivo.percentage, 0.01f)

            val transferencia = breakdown.first { it.name == "Transferencia" }
            assertEquals(30.0, transferencia.amount, 0.01)
            assertEquals(0.30f, transferencia.percentage, 0.01f)
        }

    private class FakeReportRepository : ReportRepository {
        var lastFilter: InvoiceHistoryFilter? = null

        override suspend fun getSummaryStats(filter: InvoiceHistoryFilter): Result<SummaryStats> {
            lastFilter = filter
            return Result.success(
                SummaryStats(
                    grossSales = 160.0,
                    netSales = 150.0,
                    discounts = 10.0,
                    cancellations = 25.0,
                    totalTransactions = 10,
                    totalPaid = 9,
                    totalCancelled = 1,
                    ticketPromedio = 16.67,
                    moneda = "$",
                ),
            )
        }

        override suspend fun getBestSellers(): Result<List<BestSellerProduct>> =
            Result.success(
                listOf(
                    BestSellerProduct(
                        id = "prod-1",
                        name = "Cafe Latte",
                        price = 3.50,
                        salesCount = 20,
                        progress = 1.0f,
                        colorHex = 0xFF1565C0,
                    ),
                ),
            )
    }

    private class FakeInvoiceHistoryRepository : InvoiceHistoryRepository {
        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> =
            Result.success(
                InvoiceHistoryPage(
                    transactions = listOf(
                        Transaction(
                            id = "tx-1",
                            invoiceNumber = "F001",
                            time = "10:00",
                            amount = 150.0,
                            status = TransactionStatus.PAID,
                            formaPago = "EFECTIVO",
                            dateHeader = "Hoy",
                        ),
                    ),
                    total = 1,
                ),
            )

        override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> =
            Result.success(InvoiceHistorySummary(ventasNetas = 150.0, totalFacturas = 1))

        override suspend fun getAllTransactions(): Result<List<Transaction>> = Result.success(emptyList())

        override suspend fun getTransactionById(id: String): Result<Transaction> =
            Result.failure(UnsupportedOperationException())

        override suspend fun saveTransaction(transaction: Transaction): Result<Unit> =
            Result.success(Unit)

        override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray> =
            Result.failure(UnsupportedOperationException())

        override suspend fun resendElectronicInvoice(invoiceId: String): Result<ElectronicInvoiceResultDto> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeCajaRepository(
        caja: Caja? =
            Caja(
                idCaja = "caja-1",
                codCaja = "C1",
                descripcion = "Caja Principal",
                estatus = 1,
                idSucursal = 1,
                serieCaja = "S1",
            ),
    ) : CajaRepository {
        override val activeCaja = MutableStateFlow(caja)
        override val activeCajaName = MutableStateFlow(caja?.descripcion.orEmpty())
        override val activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(emptyList())
        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success("000001")
        override suspend fun restoreActiveCajaIfValid() = Unit
        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> = Result.failure(NotImplementedError())
        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> = Result.failure(NotImplementedError())
        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> = Result.failure(NotImplementedError())
        override suspend fun getCierreSummary(): Result<CierreCajaSummary> = Result.failure(NotImplementedError())
        override suspend fun setActiveCaja(caja: Caja) = Unit
        override suspend fun clearActiveCaja() = Unit
        override suspend fun markSequenceClosed() = Unit
    }
}
