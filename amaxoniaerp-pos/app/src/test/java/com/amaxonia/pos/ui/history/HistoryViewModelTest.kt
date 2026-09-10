package com.amaxonia.pos.ui.history

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary
import com.amaxonia.pos.domain.usecase.payment.InvoicePrintFeedback
import com.amaxonia.pos.domain.usecase.payment.PrintInvoiceUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateLoadsPageAndBackendSummary() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun applyAndClearFiltersUseTheCurrentFilter() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun searchUsesDebounceBeforeReloading() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun reprintInvoiceShowsErrorWhenPrintServiceUnavailable() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val viewModel = HistoryViewModel(repository, FakeCajaRepository(), printInvoiceUseCase = null)
            advanceUntilIdle()

            val transaction = Transaction(id = "tx-1", invoiceNumber = "INV-1", time = "12:00", amount = 10.0, dateHeader = "Hoy")
            viewModel.reprintInvoice(transaction)
            advanceUntilIdle()

            assertEquals("Servicio de impresión no disponible", viewModel.state.value.detalleActionError)
            assertEquals(false, viewModel.state.value.isReprinting)
        }

    @Test
    fun reprintInvoiceShowsErrorWhenNoPrinterConfigured() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val printUseCase = PrintInvoiceUseCase { _, _, _ -> null }
            val viewModel = HistoryViewModel(repository, FakeCajaRepository(), printInvoiceUseCase = printUseCase)
            advanceUntilIdle()

            val transaction = Transaction(id = "tx-1", invoiceNumber = "INV-1", time = "12:00", amount = 10.0, dateHeader = "Hoy")
            viewModel.reprintInvoice(transaction)
            advanceUntilIdle()

            assertEquals("No hay una impresora compatible configurada en Ajustes", viewModel.state.value.detalleActionError)
            assertEquals(false, viewModel.state.value.isReprinting)
        }

    @Test
    fun reprintInvoiceShowsSuccessMessageOnSuccessfulPrint() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val printUseCase =
                PrintInvoiceUseCase { _, _, _ ->
                    InvoicePrintFeedback(
                        displayMessage = "Ticket SUNMI enviado correctamente",
                        fiscalNumber = "CUFE-123",
                        printerSerial = "SUNMI",
                        isSuccess = true,
                    )
                }
            val viewModel = HistoryViewModel(repository, FakeCajaRepository(), printInvoiceUseCase = printUseCase)
            advanceUntilIdle()

            val transaction = Transaction(id = "tx-1", invoiceNumber = "INV-1", time = "12:00", amount = 10.0, dateHeader = "Hoy")
            viewModel.reprintInvoice(transaction)
            advanceUntilIdle()

            assertEquals("Ticket SUNMI enviado correctamente", viewModel.state.value.detalleMessage)
            assertNull(viewModel.state.value.detalleActionError)
            assertEquals(false, viewModel.state.value.isReprinting)
        }

    @Test
    fun reprintInvoiceShowsErrorMessageOnFailedPrint() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val printUseCase =
                PrintInvoiceUseCase { _, _, _ ->
                    InvoicePrintFeedback(
                        displayMessage = "Papel agotado en impresora SUNMI",
                        fiscalNumber = "",
                        printerSerial = "SUNMI",
                        isSuccess = false,
                    )
                }
            val viewModel = HistoryViewModel(repository, FakeCajaRepository(), printInvoiceUseCase = printUseCase)
            advanceUntilIdle()

            val transaction = Transaction(id = "tx-1", invoiceNumber = "INV-1", time = "12:00", amount = 10.0, dateHeader = "Hoy")
            viewModel.reprintInvoice(transaction)
            advanceUntilIdle()

            assertEquals("Papel agotado en impresora SUNMI", viewModel.state.value.detalleActionError)
            assertNull(viewModel.state.value.detalleMessage)
            assertEquals(false, viewModel.state.value.isReprinting)
        }

    @Test
    fun resendElectronicInvoiceBlocksOfflinePendingInvoiceWithUuid() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val viewModel = HistoryViewModel(repository, FakeCajaRepository())
            advanceUntilIdle()

            val offlineTransaction =
                Transaction(
                    id = "c7b91d4e-8f2a-4c12-9b34-123456789abc",
                    invoiceNumber = "OFF-1725983412345",
                    time = "--:--",
                    amount = 50.0,
                    status = TransactionStatus.PENDING,
                    dateHeader = "Hoy",
                )
            viewModel.resendElectronicInvoice(offlineTransaction)
            advanceUntilIdle()

            assertEquals("No se puede reenviar una factura no sincronizada", viewModel.state.value.detalleActionError)
            assertEquals(0, repository.resendCalls)
        }

    @Test
    fun resendElectronicInvoiceSucceedsForSyncedInvoice() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val viewModel = HistoryViewModel(repository, FakeCajaRepository())
            advanceUntilIdle()

            val syncedTransaction =
                Transaction(
                    id = "inv-123",
                    invoiceNumber = "FAC-0001",
                    time = "10:00",
                    amount = 50.0,
                    status = TransactionStatus.PAID,
                    dateHeader = "Hoy",
                )
            viewModel.resendElectronicInvoice(syncedTransaction)
            advanceUntilIdle()

            assertEquals(1, repository.resendCalls)
            assertNull(viewModel.state.value.detalleActionError)
            assertEquals("Factura electrónica transmitida exitosamente", viewModel.state.value.detalleMessage)
        }

    @Test
    fun showOfflineSyncInitiatedSetsMessage() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeInvoiceHistoryRepository()
            val viewModel = HistoryViewModel(repository, FakeCajaRepository())
            advanceUntilIdle()

            viewModel.showOfflineSyncInitiated()

            assertNull(viewModel.state.value.detalleActionError)
            assertEquals("Sincronización iniciada con el servidor", viewModel.state.value.detalleMessage)
        }

    private class FakeCajaRepository(
        caja: Caja? =
            Caja(
                idCaja = "caja-1",
                codCaja = "C1",
                descripcion = "Caja 1",
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

    private class FakeInvoiceHistoryRepository : InvoiceHistoryRepository {
        val filters = mutableListOf<InvoiceHistoryFilter>()
        private val transaction =
            Transaction(
                id = "invoice-1",
                invoiceNumber = "INV-001",
                time = "10:00",
                amount = 12.0,
                dateHeader = "01/01/2026",
            )

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            filters += filter
            return Result.success(InvoiceHistoryPage(listOf(transaction), total = 250))
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> =
            Result.success(InvoiceHistorySummary(ventasNetas = 999.0, totalFacturas = 250))

        override suspend fun getAllTransactions(): Result<List<Transaction>> = Result.success(listOf(transaction))

        override suspend fun getTransactionById(id: String): Result<Transaction> = Result.success(transaction)

        override suspend fun saveTransaction(transaction: Transaction): Result<Unit> = Result.success(Unit)

        override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> =
            Result.success(FacturaDetalleResponseDto(invoiceId, transaction.invoiceNumber, emptyList()))

        override suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray> = Result.success(ByteArray(0))

        var resendCalls = 0

        override suspend fun resendElectronicInvoice(
            invoiceId: String,
        ): Result<com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto> {
            resendCalls++
            return Result.success(
                com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto(
                    success = true,
                    cufe = "CUFE-1",
                    qr = "QR-1",
                ),
            )
        }
    }
}
