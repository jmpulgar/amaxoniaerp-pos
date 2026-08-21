package com.amaxonia.pos.ui.caja

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CierreCajaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val summary =
        CierreCajaSummary(
            idCajaSecuencia = "seq-1",
            idCaja = "caja-1",
            cajaName = "Caja 1",
            vendedorName = "Vendedor",
            totalSales = 100.0,
            totalCash = 60.0,
            totalCard = 40.0,
            montoEfectivoCierre = 60.0,
            montoTotal = 100.0,
            montoCierre = 100.0,
        )

    @Test
    fun `al iniciar carga el resumen y queda Ready`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Ready)
            assertEquals(summary, (state as CierreCajaUiState.Ready).summary)
        }

    @Test
    fun `fallo del resumen expone Error con mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel(cajaRepository = failingSummaryRepository(message = null))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Error)
            assertEquals("No se pudo cargar el resumen de caja", (state as CierreCajaUiState.Error).message)
        }

    @Test
    fun `cerrar sin caja activa falla sin llamar al repositorio y conserva el resumen`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = succeedingRepository(closeResponse = null)
            repo.activeCajaState.value = null
            val vm = viewModel(cajaRepository = repo)
            advanceUntilIdle()

            vm.requestClose()
            assertTrue(vm.showCloseTicketPrompt.value)
            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Error)
            assertEquals("No hay caja activa para cerrar", (state as CierreCajaUiState.Error).message)
            assertEquals(summary, state.summary)
            assertNull(repo.closeRequests.firstOrNull())
            assertEquals(0, repo.markSequenceClosedCalls)
            assertFalse(vm.showCloseTicketPrompt.value)
        }

    @Test
    fun `cerrar con exito envia el request con los montos del resumen y marca secuencia cerrada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                succeedingRepository(
                    closeResponse = CierreCajaResponse(success = true, message = "Cierre correcto"),
                )
            val vm = viewModel(cajaRepository = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val request = repo.closeRequests.single()
            assertEquals("seq-1", request.id)
            assertEquals(100.0, request.monto_total, 0.0)
            assertEquals(100.0, request.monto_cierre, 0.0)
            assertEquals("", request.observacion_cierre)
            assertEquals("", request.numero_cierre_fiscal)
            assertEquals(1, repo.markSequenceClosedCalls)

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Success)
            assertEquals("Cierre correcto", (state as CierreCajaUiState.Success).message)
        }

    @Test
    fun `cerrar con fallo expone Error conservando el resumen para reintentar`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                succeedingRepository(
                    closeResponse = null,
                    closeError = IllegalStateException("sin conexión"),
                )
            val vm = viewModel(cajaRepository = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Error)
            assertEquals("sin conexión", (state as CierreCajaUiState.Error).message)
            assertEquals(summary, state.summary)
            assertEquals(0, repo.markSequenceClosedCalls)
        }

    @Test
    fun `cerrar imprimiendo ticket imprime el payload del resumen con la caja activa`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ticketPrinter = RecordingTicketPrinter()
            val vm = viewModel(ticketPrinter = ticketPrinter)
            advanceUntilIdle()

            vm.confirmClose(printTicket = true)
            advanceUntilIdle()

            assertEquals(1, ticketPrinter.tickets.size)
            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Success)
        }

    @Test
    fun `printReportX sin impresora fiscal no cambia mensajes ni flags`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.printReportX()
            advanceUntilIdle()

            assertFalse(vm.isPrintingReportX.value)
            assertNull(vm.reportMessage.value)
        }

    @Test
    fun `printReportX con impresora expone mensaje de exito`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = RecordingFiscalPrinter(reportXResult = Result.success(Unit))
            val vm = viewModel(fiscalPrinter = fiscalPrinter)
            advanceUntilIdle()

            vm.printReportX()
            advanceUntilIdle()

            assertEquals(1, fiscalPrinter.reportXCalls)
            assertEquals("Reporte X impreso correctamente", vm.reportMessage.value)
            assertFalse(vm.isPrintingReportX.value)
        }

    @Test
    fun `printReportZ con error de impresora expone el mensaje de error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter =
                RecordingFiscalPrinter(reportZResult = Result.failure(IllegalStateException("sin papel")))
            val vm = viewModel(fiscalPrinter = fiscalPrinter)
            advanceUntilIdle()

            vm.printReportZ()
            advanceUntilIdle()

            assertEquals("Error Reporte Z: sin papel", vm.reportMessage.value)
            assertFalse(vm.isPrintingReportZ.value)
        }

    @Test
    fun `clearReportMessage limpia el mensaje`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = RecordingFiscalPrinter(reportXResult = Result.success(Unit))
            val vm = viewModel(fiscalPrinter = fiscalPrinter)
            advanceUntilIdle()
            vm.printReportX()
            advanceUntilIdle()
            assertNotNull(vm.reportMessage.value)

            vm.clearReportMessage()

            assertNull(vm.reportMessage.value)
        }

    private fun viewModel(
        cajaRepository: FakeCajaRepository = succeedingRepository(),
        fiscalPrinter: PrinterRepository? = null,
        ticketPrinter: TicketPrinter? = null,
    ): CierreCajaViewModel {
        val contextReader =
            FakeCashCloseContextReader(
                company = CompanyIdentity(name = "Empresa", rif = "R-1", adminDatabase = "db"),
            )
        val formatter: CashCloseTicketFormatter =
            com.amaxonia.pos.data.printer.panama
                .PanamaCashCloseTicketFormatter()
        val printing =
            CashClosePrintingService(
                printerProvider = FakePrinterProvider(fiscal = fiscalPrinter, ticket = ticketPrinter),
                contextReader = contextReader,
                ticketFormatter = formatter,
            )
        val payloadBuilder =
            CashCloseTicketPayloadBuilder(
                contextReader = contextReader,
                productRepository = EmptyProductCatalogReader,
                pendingSalesReader = PendingSalesReader { _, _ -> emptyList() },
                ticketFormatter = formatter,
            )
        return CierreCajaViewModel(
            cajaRepository = cajaRepository,
            cashClosePrinting = printing,
            ticketPayloadBuilder = payloadBuilder,
        )
    }

    private fun succeedingRepository(
        closeResponse: CierreCajaResponse? = CierreCajaResponse(success = true, message = "ok"),
        closeError: IllegalStateException? = null,
    ): FakeCajaRepository =
        FakeCajaRepository(
            summaryResult = Result.success(summary),
            closeResult =
                when {
                    closeError != null -> Result.failure(closeError)
                    closeResponse != null -> Result.success(closeResponse)
                    else -> Result.success(CierreCajaResponse(success = true, message = "ok"))
                },
        )

    private fun failingSummaryRepository(message: String?): FakeCajaRepository =
        FakeCajaRepository(
            summaryResult = Result.failure(IllegalStateException(message)),
        )

    private class FakeCajaRepository(
        val summaryResult: Result<CierreCajaSummary>,
        val closeResult: Result<CierreCajaResponse> =
            Result.success(CierreCajaResponse(success = true, message = "ok")),
    ) : CajaRepository {
        val activeCajaState = MutableStateFlow<Caja?>(activeCajaFixture)
        val closeRequests = mutableListOf<CierreCajaRequest>()
        var markSequenceClosedCalls = 0

        override val activeCaja get() = activeCajaState
        override val activeCajaName = MutableStateFlow("Caja 1")
        override val activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)

        override suspend fun getCajas() = Result.success(listOf(activeCajaFixture))

        override suspend fun getNextSecuenciaCodigo(idCaja: String) = Result.success("SEQ")

        override suspend fun restoreActiveCajaIfValid() = Unit

        override suspend fun checkCajaStatus(cajaId: String) = Result.success(CajaStatusResponse())

        override suspend fun openCaja(request: AperturaRequest) = Result.success(CajaStatusResponse())

        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> {
            closeRequests += request
            return closeResult
        }

        override suspend fun getCierreSummary() = summaryResult

        override suspend fun setActiveCaja(caja: Caja) {
            activeCajaState.value = caja
        }

        override suspend fun clearActiveCaja() {
            activeCajaState.value = null
        }

        override suspend fun markSequenceClosed() {
            markSequenceClosedCalls++
        }
    }

    private class FakePrinterProvider(
        private val fiscal: PrinterRepository?,
        private val ticket: TicketPrinter?,
    ) : PrinterProvider {
        override fun getActivePrinter(): PrinterRepository? = fiscal

        override fun getActiveTicketPrinter(): TicketPrinter? = ticket
    }

    private class FakeCashCloseContextReader(
        private val company: CompanyIdentity?,
    ) : CashCloseContextReader {
        override suspend fun currentCountryCode() = "PA"

        override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2

        override suspend fun currentCompany() = company
    }

    private class RecordingFiscalPrinter(
        val reportXResult: Result<Unit> = Result.success(Unit),
        val reportZResult: Result<Unit> = Result.success(Unit),
    ) : PrinterRepository {
        var reportXCalls = 0
        var reportZCalls = 0

        override suspend fun printReceipt(transaction: com.amaxonia.pos.domain.model.Transaction) =
            Result.failure<com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult>(
                AssertionError("must not be called"),
            )

        override suspend fun printCreditNote(document: com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto) =
            Result.failure<com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult>(
                AssertionError("must not be called"),
            )

        override suspend fun printReportX(): Result<Unit> {
            reportXCalls++
            return reportXResult
        }

        override suspend fun printReportZ(): Result<Unit> {
            reportZCalls++
            return reportZResult
        }
    }

    private class RecordingTicketPrinter : TicketPrinter {
        val tickets = mutableListOf<TicketDocument>()

        override suspend fun connect() = PrintResult.Success

        override suspend fun disconnect() = Unit

        override suspend fun isAvailable() = true

        override suspend fun printText(text: String) = PrintResult.Success

        override suspend fun printTicket(ticket: TicketDocument): PrintResult {
            tickets += ticket
            return PrintResult.Success
        }
    }

    private object EmptyProductCatalogReader : ProductCatalogReader {
        override suspend fun getAllProducts(): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getProductById(id: String): Result<Product> = Result.failure(AssertionError("must not be called"))

        override suspend fun getProductStock(id: String): Result<ProductStock> = Result.failure(AssertionError("must not be called"))

        override suspend fun searchProducts(query: String): Result<List<Product>> = Result.success(emptyList())
    }

    private companion object {
        val activeCajaFixture =
            Caja(
                idCaja = "caja-1",
                codCaja = "C1",
                caja = null,
                descripcion = "Caja principal",
                estatus = 1,
                idSucursal = 1,
                serieCaja = "S1",
            )
    }
}
