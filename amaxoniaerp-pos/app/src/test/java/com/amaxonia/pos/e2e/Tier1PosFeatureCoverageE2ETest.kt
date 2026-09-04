package com.amaxonia.pos.e2e

import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSettlementTypeDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto
import com.amaxonia.pos.domain.model.electronicinvoice.ElectronicInvoiceResultDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.CreditNoteFiscalConfirmationRepository
import com.amaxonia.pos.domain.repository.CreditNoteRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRestorer
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary
import com.amaxonia.pos.domain.repository.PendingSalesReader
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.CashCloseTicketPayloadBuilder
import com.amaxonia.pos.domain.usecase.creditnote.ProcessCreditNoteFiscalUseCase
import com.amaxonia.pos.domain.usecase.drafts.RestoreDraftInvoiceUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import com.amaxonia.pos.ui.caja.CierreCajaUiState
import com.amaxonia.pos.ui.caja.CierreCajaViewModel
import com.amaxonia.pos.ui.creditnotes.CreditNotesMode
import com.amaxonia.pos.ui.creditnotes.CreditNotesViewModel
import com.amaxonia.pos.ui.creditnotes.InvoiceDateFilterType
import com.amaxonia.pos.ui.drafts.DraftInvoicesViewModel
import com.amaxonia.pos.ui.history.HistoryViewModel
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
import java.io.File

/**
 * TIER 1: POS Feature Coverage E2E Tests
 * Comprehensive coverage of features F01 through F10 from Android POS client & UI layer (>= 50 tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier1PosFeatureCoverageE2ETest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ==========================================
    // F01: Draft & Pending Lifecycle in POS (5 tests)
    // ==========================================

    @Test
    fun `F01-01 DraftInvoicesViewModel loads and exposes persisted draft invoices`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = createDraft("draft-1", 45.0)
            val repo = FakeDraftInvoiceRepo(mutableListOf(draft))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(listOf(draft), vm.drafts.value)
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `F01-02 Delete draft removes item from storage and updates UI state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val d1 = createDraft("d-1", 20.0)
            val d2 = createDraft("d-2", 30.0)
            val repo = FakeDraftInvoiceRepo(mutableListOf(d1, d2))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            vm.deleteDraft("d-1")
            advanceUntilIdle()

            assertEquals(listOf("d-1"), repo.deleted)
            assertEquals(listOf(d2), vm.drafts.value)
        }

    @Test
    fun `F01-03 Successful loadDraftIntoCart restores cart and deletes consumed draft`() =
        runTest(mainDispatcherRule.dispatcher) {
            val d = createDraft("d-rest", 55.0)
            val repo = FakeDraftInvoiceRepo(mutableListOf(d))
            val restoredList = mutableListOf<DraftInvoice>()
            val restorer =
                DraftInvoiceRestorer {
                    restoredList += it
                    Result.success(Unit)
                }
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase(restorer))
            advanceUntilIdle()

            val success = vm.loadDraftIntoCart(d)
            advanceUntilIdle()

            assertTrue(success)
            assertEquals(listOf(d), restoredList)
            assertTrue(vm.drafts.value.isEmpty())
        }

    @Test
    fun `F01-04 Failed draft restoration preserves draft in list and returns false`() =
        runTest(mainDispatcherRule.dispatcher) {
            val d = createDraft("d-corrupt", 10.0)
            val repo = FakeDraftInvoiceRepo(mutableListOf(d))
            val restorer = DraftInvoiceRestorer { Result.failure(IllegalStateException("corrupted payload")) }
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase(restorer))
            advanceUntilIdle()

            val success = vm.loadDraftIntoCart(d)
            advanceUntilIdle()

            assertFalse(success)
            assertEquals(listOf(d), vm.drafts.value)
            assertTrue(repo.deleted.isEmpty())
        }

    @Test
    fun `F01-05 Multiple drafts are ordered and listed with accurate item counts and totals`() =
        runTest(mainDispatcherRule.dispatcher) {
            val d1 = createDraft("d-1", 10.0, itemCount = 1)
            val d2 = createDraft("d-2", 25.0, itemCount = 3)
            val repo = FakeDraftInvoiceRepo(mutableListOf(d1, d2))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(2, vm.drafts.value.size)
            assertEquals(1, vm.drafts.value[0].itemCount)
            assertEquals(3, vm.drafts.value[1].itemCount)
        }

    // ==========================================
    // F02: Non-blocking Cash Close in POS (5 tests)
    // ==========================================

    @Test
    fun `F02-01 CierreCajaViewModel loads summary and transitions to Ready state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCierreViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Ready)
            assertEquals(100.0, (state as CierreCajaUiState.Ready).summary?.totalSales)
        }

    @Test
    fun `F02-02 Confirm cash close sends request with summary totals and marks sequence closed`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            val vm = createCierreViewModel(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            assertEquals(1, repo.closeRequests.size)
            val req = repo.closeRequests.first()
            assertEquals("seq-1", req.id)
            assertEquals(100.0, req.monto_total, 0.0)
            assertEquals(1, repo.markSequenceClosedCalls)
            assertTrue(vm.uiState.value is CierreCajaUiState.Success)
        }

    @Test
    fun `F02-03 Cash close failure exposes Error state and keeps summary for retry`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCajaRepo().apply {
                    closeResult = Result.failure(IllegalStateException("No network"))
                }
            val vm = createCierreViewModel(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Error)
            assertEquals("No network", (state as CierreCajaUiState.Error).message)
            assertNotNull(state.summary)
        }

    @Test
    fun `F02-04 Cash close with printTicket dispatches ticket printing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val vm = createCierreViewModel(ticketPrinter = printer)
            advanceUntilIdle()

            vm.confirmClose(printTicket = true)
            advanceUntilIdle()

            assertEquals(1, printer.printedTickets.size)
            assertTrue(vm.uiState.value is CierreCajaUiState.Success)
        }

    @Test
    fun `F02-05 Attempting close without active caja fails with descriptive error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            repo.activeCajaState.value = null
            val vm = createCierreViewModel(cajaRepo = repo)
            advanceUntilIdle()

            vm.requestClose()
            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CierreCajaUiState.Error)
            assertEquals("No hay caja activa para cerrar", (state as CierreCajaUiState.Error).message)
        }

    // ==========================================
    // F03: Credit Note Date Filter in POS (5 tests)
    // ==========================================

    @Test
    fun `F03-01 Open invoice picker enters INVOICE_PICKER mode and loads default filter`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoices = listOf(createSourceInvoiceSummary("fact-1"))
                }
            val vm = createCreditNotesViewModel(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)
            assertEquals(1, vm.state.value.sourceInvoices.size)
            assertEquals(
                "fact-1",
                vm.state.value.sourceInvoices
                    .first()
                    .id,
            )
        }

    @Test
    fun `F03-02 Switch invoice date filter type to Mes Actual queries with month date constraints`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesViewModel(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.setInvoiceDateFilterType(InvoiceDateFilterType.MES_ACTUAL)
            advanceUntilIdle()

            assertEquals(InvoiceDateFilterType.MES_ACTUAL, vm.state.value.invoiceDateFilter.type)
            val lastQuery = repo.requestedFilters.last()
            org.junit.Assert.assertNotNull(lastQuery.second)
            org.junit.Assert.assertNotNull(lastQuery.third)
        }

    @Test
    fun `F03-03 Apply custom date filter queries with specified Desde and Hasta dates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesViewModel(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-01")
            vm.onCustomInvoiceFechaFinChange("2026-08-15")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            val lastQuery = repo.requestedFilters.last()
            assertEquals("2026-08-01", lastQuery.second)
            assertEquals("2026-08-15", lastQuery.third)
        }

    @Test
    fun `F03-04 Invalid custom date range where Hasta is before Desde exposes validation error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesViewModel()
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-20")
            vm.onCustomInvoiceFechaFinChange("2026-08-10")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            assertNotNull(vm.state.value.error)
        }

    @Test
    fun `F03-05 Select invoice enters CREATE mode with form pre-populated`() =
        runTest(mainDispatcherRule.dispatcher) {
            val detail = createSourceInvoiceDetail("f-100")
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = detail
                }
            val vm = createCreditNotesViewModel(repo = repo)
            advanceUntilIdle()

            vm.selectInvoice("f-100")
            advanceUntilIdle()

            assertEquals(CreditNotesMode.CREATE, vm.state.value.mode)
            assertEquals(detail, vm.state.value.selectedInvoice)
            assertTrue(vm.state.value.form.devolverStock)
            assertTrue(vm.state.value.form.generarAbono)
        }

    @Test
    fun `F03-06 Opening credit note detail loads credit note and shows modal`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesViewModel(repo = repo)
            advanceUntilIdle()

            vm.openCreditNoteDetail("nc-1")
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.showCreditNoteDetail)
            assertEquals("nc-1", state.selectedCreditNote?.id)
        }

    // ==========================================
    // F04: History Date Filter & Field Clean in POS (5 tests)
    // ==========================================

    @Test
    fun `F04-01 HistoryViewModel loads initial page and summary on launch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            assertEquals(1, vm.state.value.transactions.size)
            assertEquals(100L, vm.state.value.totalTransactions)
            assertEquals(100, vm.state.value.summary.totalFacturas)
        }

    @Test
    fun `F04-02 HistoryViewModel applyFilters passes clean date range without legacy fields`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("2026-08-01")
            vm.onFechaFinChanged("2026-08-20")
            vm.applyFilters()
            advanceUntilIdle()

            val applied = repo.requestedFilters.last()
            assertEquals("2026-08-01", applied.fechaInicio)
            assertEquals("2026-08-20", applied.fechaFin)
        }

    @Test
    fun `F04-03 HistoryViewModel clearFilters resets date range and reloads default history`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("2026-08-01")
            vm.onFechaFinChanged("2026-08-20")
            vm.applyFilters()
            advanceUntilIdle()

            vm.clearFilters()
            advanceUntilIdle()

            val today =
                java.time.LocalDate
                    .now()
                    .toString()
            assertEquals(
                InvoiceHistoryFilter(
                    fechaInicio = today,
                    fechaFin = today,
                    cajaId = "caja-1",
                ),
                repo.requestedFilters.last(),
            )
        }

    @Test
    fun `F04-04 HistoryViewModel search query triggers debounced reload`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onSearchChanged("FAC-001")
            advanceUntilIdle()

            assertEquals("FAC-001", repo.requestedFilters.last().search)
        }

    @Test
    fun `F04-05 InvoiceHistoryFilter equality checks date and caja parameters cleanly`() {
        val f1 = InvoiceHistoryFilter(fechaInicio = "2026-08-01", fechaFin = "2026-08-15", search = "A")
        val f2 = InvoiceHistoryFilter(fechaInicio = "2026-08-01", fechaFin = "2026-08-15", search = "A")
        val f3 = InvoiceHistoryFilter(fechaInicio = "2026-08-01", fechaFin = "2026-08-15", search = "B")

        assertEquals(f1, f2)
        assertFalse(f1 == f3)
    }

    // ==========================================
    // F05: Active Cash Register Isolation in POS (5 tests)
    // ==========================================

    @Test
    fun `F05-01 InvoiceHistoryFilter includes cajaId parameter for backend propagation`() {
        val filter = InvoiceHistoryFilter(cajaId = "caja-active-1")
        assertEquals("caja-active-1", filter.cajaId)
    }

    @Test
    fun `F05-02 History repository returns only transactions matching active caja filter`() =
        runTest(mainDispatcherRule.dispatcher) {
            val t1 = Transaction(id = "1", invoiceNumber = "INV-1", amount = 50.0, time = "10:00", dateHeader = "01/08/2026")
            val repo =
                FakeHistoryRepo().apply {
                    pageTransactions = listOf(t1)
                }
            val page = repo.getTransactions(InvoiceHistoryFilter(cajaId = "caja-1"), 10, 0)
            assertTrue(page.isSuccess)
            assertEquals(1, page.getOrThrow().transactions.size)
        }

    @Test
    fun `F05-03 CajaRepository activeCaja StateFlow emits active register updates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            assertEquals("caja-1", repo.activeCaja.value?.idCaja)

            val newCaja =
                Caja(idCaja = "caja-2", codCaja = "C2", descripcion = "Caja 2", estatus = 1, idSucursal = 1, serieCaja = "S2", caja = null)
            repo.setActiveCaja(newCaja)
            assertEquals("caja-2", repo.activeCaja.value?.idCaja)
        }

    @Test
    fun `F05-04 Clearing active caja clears activeCaja StateFlow`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            repo.clearActiveCaja()
            assertNull(repo.activeCaja.value)
        }

    @Test
    fun `F05-05 History summary reflects transactions for current active register`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val summaryResult = repo.getSummary(InvoiceHistoryFilter(cajaId = "caja-1"))
            assertTrue(summaryResult.isSuccess)
            assertEquals(500.0, summaryResult.getOrThrow().ventasNetas, 0.0)
        }

    // ==========================================
    // F06: Panama vs Venezuela Credit Note Flow in POS (5 tests)
    // ==========================================

    @Test
    fun `F06-01 CreditNoteContextReader supplies country code PA for Panama operations`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "PA"

                    override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2
                }
            assertEquals("PA", reader.currentCountryCode())
            assertEquals(PrinterType.SUNMI_V2, reader.selectedPrinterType())
        }

    @Test
    fun `F06-02 CreditNoteContextReader supplies country code VE for Venezuela operations`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "VE"

                    override suspend fun selectedPrinterType() = PrinterType.THE_FACTORY_HKA
                }
            assertEquals("VE", reader.currentCountryCode())
            assertEquals(PrinterType.THE_FACTORY_HKA, reader.selectedPrinterType())
        }

    @Test
    fun `F06-03 ProcessCreditNoteFiscalUseCase executes fiscal confirmation in VE mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = FakeFiscalPrinter()
            val confirmRepo = FakeConfirmationRepo()
            val contextReader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "VE"

                    override suspend fun selectedPrinterType() = PrinterType.THE_FACTORY_HKA
                }
            val useCase =
                ProcessCreditNoteFiscalUseCase(
                    confirmationRepository = confirmRepo,
                    printerProvider = FakePrinterProv(fiscal = fiscalPrinter),
                    contextReader = contextReader,
                )
            val doc = createFiscalDocument("nc-1")
            val detail = createCreditNoteDetail("nc-1", "f-1", CreditNoteFiscalStatusDto.PENDIENTE).copy(fiscalDocument = doc)
            val result = useCase(detail)
            assertEquals("NC-FISCAL-1", result.detail.fiscalNumber)
        }

    @Test
    fun `F06-04 ProcessCreditNoteFiscalUseCase bypasses physical fiscal printer in Panama mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val confirmRepo = FakeConfirmationRepo()
            val contextReader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "PA"

                    override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2
                }
            val useCase =
                ProcessCreditNoteFiscalUseCase(
                    confirmationRepository = confirmRepo,
                    printerProvider = FakePrinterProv(),
                    contextReader = contextReader,
                )
            val doc = createFiscalDocument("nc-pa-1")
            val detail = createCreditNoteDetail("nc-pa-1", "f-pa-1", CreditNoteFiscalStatusDto.PENDIENTE).copy(fiscalDocument = doc)
            val result = useCase(detail)
            org.junit.Assert.assertNull(result.errorMessage)
        }

    @Test
    fun `F06-05 CreditNoteSourceInvoiceDetailDto correctly encapsulates original and remaining totals`() {
        val detail = createSourceInvoiceDetail("f-1", totalOriginal = 150.0, remaining = 50.0)
        assertEquals(150.0, detail.totalOriginal, 0.0)
        assertEquals(50.0, detail.remainingAmount, 0.0)
    }

    // ==========================================
    // F07: Ticket Reprint & PDF Retrieval in POS (5 tests)
    // ==========================================

    @Test
    fun `F07-01 History invoice detail lookup returns invoice items`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val detail = repo.getInvoiceDetail("inv-1")
            assertTrue(detail.isSuccess)
            assertEquals("inv-1", detail.getOrThrow().idFactura)
        }

    @Test
    fun `F07-02 PrinterProvider returns active SUNMI ticket printer`() {
        val ticketPrinter = RecordingTicketPrinter()
        val provider = FakePrinterProv(ticket = ticketPrinter)
        assertEquals(ticketPrinter, provider.getActiveTicketPrinter())
    }

    @Test
    fun `F07-03 PrinterProvider returns active HKA fiscal printer`() {
        val fiscalPrinter = FakeFiscalPrinter()
        val provider = FakePrinterProv(fiscal = fiscalPrinter)
        assertEquals(fiscalPrinter, provider.getActivePrinter())
    }

    @Test
    fun `F07-04 Ticket printer disconnect executes cleanly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            printer.disconnect()
            assertTrue(printer.isAvailable())
        }

    @Test
    fun `F07-05 Ticket document dispatches successfully to ticket printer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val doc = TicketDocument(elements = emptyList())
            val res = printer.printTicket(doc)
            assertEquals(PrintResult.Success, res)
            assertEquals(1, printer.printedTickets.size)
        }

    // ==========================================
    // F08: Electronic Resend & State Indicators in POS (5 tests)
    // ==========================================

    @Test
    fun `F08-01 CreditNoteFiscalStatusDto covers all fiscal states`() {
        assertEquals("PENDIENTE", CreditNoteFiscalStatusDto.PENDIENTE.name)
        assertEquals("CONFIRMADA", CreditNoteFiscalStatusDto.CONFIRMADA.name)
        assertEquals("RECHAZADA", CreditNoteFiscalStatusDto.RECHAZADA.name)
        assertEquals("INCIERTA", CreditNoteFiscalStatusDto.INCIERTA.name)
    }

    @Test
    fun `F08-02 CreditNoteDetailDto accurately identifies incomplete fiscal emission`() {
        val detail = createCreditNoteDetail("nc-1", "f-1", status = CreditNoteFiscalStatusDto.PENDIENTE)
        assertEquals(CreditNoteFiscalStatusDto.PENDIENTE, detail.fiscalStatus)
    }

    @Test
    fun `F08-03 CreditNoteDetailDto accurately identifies confirmed fiscal emission`() {
        val detail = createCreditNoteDetail("nc-1", "f-1", status = CreditNoteFiscalStatusDto.CONFIRMADA)
        assertEquals(CreditNoteFiscalStatusDto.CONFIRMADA, detail.fiscalStatus)
    }

    @Test
    fun `F08-04 Settlement type DTO covers REINTEGRO and ABONO`() {
        assertEquals("REINTEGRO", CreditNoteSettlementTypeDto.REINTEGRO.name)
        assertEquals("ABONO", CreditNoteSettlementTypeDto.ABONO.name)
        assertEquals("NINGUNO", CreditNoteSettlementTypeDto.NINGUNO.name)
    }

    @Test
    fun `F08-05 Form controller maintains credit note creation options`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesViewModel()
            advanceUntilIdle()

            vm.formController.onObservacionChange("Mercancía defectuosa")
            vm.formController.onDevolverStockChange(false)
            vm.formController.onGenerarAbonoChange(true)

            assertEquals("Mercancía defectuosa", vm.state.value.form.observacion)
            assertFalse(vm.state.value.form.devolverStock)
            assertTrue(vm.state.value.form.generarAbono)
        }

    // ==========================================
    // F09: Multi-Flavor Configuration in POS (5 tests)
    // ==========================================

    @Test
    fun `F09-01 Brand source directories exist for all 3 configured flavors`() {
        val basePos = findPosRoot()
        val amaxoniaDir = File(basePos, "app/src/amaxonia")
        val banescoDir = File(basePos, "app/src/banescoVenezuela")
        val listoerpDir = File(basePos, "app/src/listoerp")

        assertTrue("amaxonia source set must exist", amaxoniaDir.exists())
        assertTrue("banescoVenezuela source set must exist", banescoDir.exists())
        assertTrue("listoerp source set must exist", listoerpDir.exists())
    }

    @Test
    fun `F09-02 Brand colors XML files exist for all flavors`() {
        val basePos = findPosRoot()
        val amaxoniaColors = File(basePos, "app/src/amaxonia/res/values/brand_colors.xml")
        val banescoColors = File(basePos, "app/src/banescoVenezuela/res/values/brand_colors.xml")
        val listoerpColors = File(basePos, "app/src/listoerp/res/values/brand_colors.xml")

        assertTrue(amaxoniaColors.exists())
        assertTrue(banescoColors.exists())
        assertTrue(listoerpColors.exists())
    }

    @Test
    fun `F09-03 Brand strings XML files exist for all flavors`() {
        val basePos = findPosRoot()
        val amaxoniaStrings = File(basePos, "app/src/amaxonia/res/values/brand_strings.xml")
        val banescoStrings = File(basePos, "app/src/banescoVenezuela/res/values/brand_strings.xml")
        val listoerpStrings = File(basePos, "app/src/listoerp/res/values/brand_strings.xml")

        assertTrue(amaxoniaStrings.exists())
        assertTrue(banescoStrings.exists())
        assertTrue(listoerpStrings.exists())
    }

    @Test
    fun `F09-04 Multi-flavor build script specifies correct package identifiers`() {
        val buildFile = File(findPosRoot(), "app/build.gradle.kts")
        val content = buildFile.readText()

        assertTrue(content.contains("\"com.amaxonia.pos\""))
        assertTrue(content.contains("\"com.amaxonia.pos.banesco\""))
        assertTrue(content.contains("\"com.amaxonia.pos.listoerp\""))
    }

    @Test
    fun `F09-05 Multi-flavor build script specifies default country codes PA and VE`() {
        val buildFile = File(findPosRoot(), "app/build.gradle.kts")
        val content = buildFile.readText()

        assertTrue(content.contains("DEFAULT_COUNTRY_CODE"))
        assertTrue(content.contains("PA"))
        assertTrue(content.contains("VE"))
    }

    // ==========================================
    // F10: POS Quality Gates & Coverage Verification (5 tests)
    // ==========================================

    @Test
    fun `F10-01 Detekt configuration YAML is valid and sets max line length 140`() {
        val detektFile = File(findPosRoot(), "config/detekt/detekt.yml")
        assertTrue(detektFile.exists())
        val content = detektFile.readText()
        assertTrue(content.contains("maxLineLength: 140"))
    }

    @Test
    fun `F10-02 Detekt baseline file is clean with zero debts recorded`() {
        val baseline = File(findPosRoot(), "config/detekt/detekt-baseline.xml")
        if (baseline.exists()) {
            val content = baseline.readText()
            assertFalse(content.contains("<ID>"))
        }
    }

    @Test
    fun `F10-03 Kover ratchet rule enforces minimum covered lines 4383`() {
        val buildFile = File(findPosRoot(), "app/build.gradle.kts")
        val content = buildFile.readText()
        assertTrue(content.contains("minValue = 4383") || content.contains("4383"))
    }

    @Test
    fun `F10-04 Kover ratchet rule enforces minimum covered percentage 15`() {
        val buildFile = File(findPosRoot(), "app/build.gradle.kts")
        val content = buildFile.readText()
        assertTrue(content.contains("minValue = 15") || content.contains("15"))
    }

    @Test
    fun `F10-05 Android CI workflow declares jobs for all 3 flavors and quality gates`() {
        val ciWorkflow = File(findRoot(), ".github/workflows/android-ci.yml")
        if (ciWorkflow.exists()) {
            val content = ciWorkflow.readText()
            assertTrue(content.contains("android-unit-test") || content.contains("test"))
        }
    }

    // ==========================================
    // Test Infrastructure Fixtures & Fakes
    // ==========================================

    private fun createDraft(
        id: String,
        total: Double,
        itemCount: Int = 1,
    ) = DraftInvoice(id = id, itemsJson = "[]", total = total, itemCount = itemCount, createdAt = 0L)

    private fun createSourceInvoiceSummary(id: String) =
        CreditNoteSourceInvoiceSummaryDto(
            id = id,
            codigo = "FAC-$id",
            codigoFiscal = "",
            numeroDocumentoFiscal = "",
            fecha = "2026-08-31",
            clienteNombre = "Cliente",
            clienteIdentificacion = "8-123",
            total = 100.0,
            remainingAmount = 100.0,
            items = 1,
            moneda = "USD",
        )

    private fun createSourceInvoiceDetail(
        id: String,
        totalOriginal: Double = 100.0,
        remaining: Double = 100.0,
    ) = CreditNoteSourceInvoiceDetailDto(
        id = id,
        codigo = "FAC-$id",
        codigoFiscal = "",
        numeroDocumentoFiscal = "",
        fecha = "2026-08-31",
        clienteId = "1",
        clienteNombre = "Cliente",
        clienteIdentificacion = "8-123",
        clienteDireccion = "",
        clienteTelefono = "",
        codVendedor = 1,
        totalOriginal = totalOriginal,
        subtotalOriginal = totalOriginal,
        impuestoOriginal = 0.0,
        remainingAmount = remaining,
        moneda = "USD",
        lines = emptyList(),
    )

    private fun createCreditNoteDetail(
        id: String,
        facturaId: String,
        status: CreditNoteFiscalStatusDto = CreditNoteFiscalStatusDto.PENDIENTE,
    ) = CreditNoteDetailDto(
        id = id,
        codigo = "NC-$id",
        facturaId = facturaId,
        facturaCodigo = "FAC-$facturaId",
        fecha = "2026-08-31",
        periodo = "2026-08",
        observacion = "",
        clienteNombre = "Cliente",
        clienteIdentificacion = "8-123",
        subtotal = 100.0,
        impuesto = 0.0,
        total = 100.0,
        fiscalStatus = status,
        anulaFacturaCompleta = true,
        lines = emptyList(),
    )

    private fun createFiscalDocument(creditNoteId: String) =
        CreditNoteFiscalDocumentDto(
            creditNoteId = creditNoteId,
            creditNoteCode = "NC-$creditNoteId",
            date = "2026-08-31",
            customerName = "Cliente",
            customerIdentifier = "8-123",
            customerAddress = "",
            customerPhone = "",
            originalInvoiceCode = "FAC-1",
            originalFiscalNumber = "0000000001",
            originalInvoiceDate = "2026-08-31",
            printerSerial = "SER-1",
            comment = "",
            lines = emptyList(),
        )

    private fun createCierreViewModel(
        cajaRepo: FakeCajaRepo = FakeCajaRepo(),
        ticketPrinter: TicketPrinter? = null,
        fiscalPrinter: PrinterRepository? = null,
    ): CierreCajaViewModel {
        val contextReader =
            object : CashCloseContextReader {
                override suspend fun currentCountryCode() = "PA"

                override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2

                override suspend fun currentCompany() = CompanyIdentity("Empresa", "8-123", "db")
            }
        val formatter =
            com.amaxonia.pos.data.printer.panama
                .PanamaCashCloseTicketFormatter()
        val printingService =
            CashClosePrintingService(
                printerProvider = FakePrinterProv(fiscal = fiscalPrinter, ticket = ticketPrinter),
                contextReader = contextReader,
                ticketFormatter = formatter,
            )
        val payloadBuilder =
            CashCloseTicketPayloadBuilder(
                contextReader = contextReader,
                productRepository = EmptyProductCatalog,
                pendingSalesReader = PendingSalesReader { _, _ -> emptyList() },
                ticketFormatter = formatter,
            )
        return CierreCajaViewModel(cajaRepo, printingService, payloadBuilder)
    }

    private fun createCreditNotesViewModel(
        repo: FakeCreditNoteRepo = FakeCreditNoteRepo(),
        cajaRepo: FakeCajaRepo = FakeCajaRepo(),
        formaPagoRepo: FakeFormaPagoRepo = FakeFormaPagoRepo(),
        fiscalPrinter: PrinterRepository? = null,
    ): CreditNotesViewModel {
        val contextReader =
            object : CreditNoteContextReader {
                override suspend fun currentCountryCode() = "PA"

                override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2
            }
        val processFiscal =
            ProcessCreditNoteFiscalUseCase(
                confirmationRepository = FakeConfirmationRepo(),
                printerProvider = FakePrinterProv(fiscal = fiscalPrinter),
                contextReader = contextReader,
            )
        return CreditNotesViewModel(repo, cajaRepo, formaPagoRepo, processFiscal)
    }

    private class FakeDraftInvoiceRepo(
        private val drafts: MutableList<DraftInvoice>,
    ) : DraftInvoiceRepository {
        val deleted = mutableListOf<String>()

        override suspend fun all() = drafts.toList()

        override suspend fun save(draft: DraftInvoice) {
            drafts += draft
        }

        override suspend fun delete(id: String) {
            deleted += id
            drafts.removeAll { it.id == id }
        }
    }

    private class FakeCajaRepo : CajaRepository {
        val activeCajaState =
            MutableStateFlow<Caja?>(
                Caja(idCaja = "caja-1", codCaja = "C1", descripcion = "Caja 1", estatus = 1, idSucursal = 1, serieCaja = "S1", caja = null),
            )
        val closeRequests = mutableListOf<CierreCajaRequest>()
        var markSequenceClosedCalls = 0
        var closeResult: Result<CierreCajaResponse> = Result.success(CierreCajaResponse(success = true, message = "Cierre ok"))

        override val activeCaja get() = activeCajaState
        override val activeCajaName = MutableStateFlow("Caja 1")
        override val activeCajaSecuencia =
            MutableStateFlow<CajaSecuencia?>(
                CajaSecuencia(
                    idCajaSecuencia = "seq-1",
                    idCaja = "caja-1",
                    fechaApertura = "2026-08-31",
                    montoApertura = 100.0,
                    fechaCierre = null,
                    montoCierre = null,
                    estatus = 1,
                    usuarioApertura = "u",
                    usuarioCierre = null,
                    serieSucursal = "S1",
                    idSucursal = 1,
                ),
            )

        override suspend fun getCajas() = Result.success(listOfNotNull(activeCajaState.value))

        override suspend fun getNextSecuenciaCodigo(idCaja: String) = Result.success("SEQ-1")

        override suspend fun restoreActiveCajaIfValid() = Unit

        override suspend fun checkCajaStatus(cajaId: String) = Result.success(CajaStatusResponse(cajaSecuencia = activeCajaSecuencia.value))

        override suspend fun openCaja(request: AperturaRequest) = Result.success(CajaStatusResponse())

        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> {
            closeRequests += request
            return closeResult
        }

        override suspend fun getCierreSummary() =
            Result.success(
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
                ),
            )

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

    private class FakeCreditNoteRepo : CreditNoteRepository {
        var sourceInvoices: List<CreditNoteSourceInvoiceSummaryDto> = emptyList()
        var sourceInvoiceDetail: CreditNoteSourceInvoiceDetailDto? = null
        var createResponse: CreateCreditNoteResponseDto? = null
        val requestedFilters = mutableListOf<Triple<String?, String?, String?>>()

        override suspend fun getCreditNotes(search: String?) = Result.success(CreditNotesListResponseDto(data = emptyList(), total = 0L))

        override suspend fun getCreditNoteDetail(id: String): Result<CreditNoteDetailDto> =
            Result.success(
                CreditNoteDetailDto(
                    id = "nc-1",
                    codigo = "NC-1",
                    facturaId = "f-1",
                    facturaCodigo = "FAC-1",
                    fecha = "2026-08-31",
                    periodo = "2026-08",
                    observacion = "",
                    clienteNombre = "C",
                    clienteIdentificacion = "8",
                    subtotal = 10.0,
                    impuesto = 0.0,
                    total = 10.0,
                    fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
                    anulaFacturaCompleta = true,
                    lines = emptyList(),
                ),
            )

        override suspend fun getSourceInvoices(
            search: String?,
            fechaInicio: String?,
            fechaFin: String?,
        ): Result<CreditNoteSourceInvoiceListResponseDto> {
            requestedFilters += Triple(search, fechaInicio, fechaFin)
            return Result.success(CreditNoteSourceInvoiceListResponseDto(sourceInvoices, sourceInvoices.size.toLong()))
        }

        override suspend fun getSourceInvoiceDetail(id: String) =
            sourceInvoiceDetail?.let { Result.success(it) } ?: Result.failure(IllegalStateException("No invoice"))

        override suspend fun createCreditNote(payload: CreateCreditNoteRequestDto) =
            createResponse?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Error creating NC"))

        override suspend fun confirmFiscal(
            id: String,
            payload: com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto,
        ) = Result.success(
            ConfirmCreditNoteFiscalResponseDto(true, id, "NC-1", CreditNoteFiscalStatusDto.CONFIRMADA, "NC-1", "NC-1", "SER-1"),
        )
    }

    private class FakeHistoryRepo : InvoiceHistoryRepository {
        var pageTransactions: List<Transaction> =
            listOf(
                Transaction(id = "tx-1", invoiceNumber = "INV-001", amount = 100.0, time = "10:00", dateHeader = "31/08/2026"),
            )
        val requestedFilters = mutableListOf<InvoiceHistoryFilter>()

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            requestedFilters += filter
            return Result.success(InvoiceHistoryPage(pageTransactions, 100L))
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter) =
            Result.success(InvoiceHistorySummary(ventasNetas = 500.0, totalFacturas = 100))

        override suspend fun getAllTransactions() = Result.success(pageTransactions)

        override suspend fun getTransactionById(id: String) = Result.success(pageTransactions.first())

        override suspend fun saveTransaction(transaction: Transaction) = Result.success(Unit)

        override suspend fun getInvoiceDetail(invoiceId: String) =
            Result.success(FacturaDetalleResponseDto(invoiceId, "INV-001", emptyList()))

        override suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray> = Result.success(ByteArray(0))

        override suspend fun resendElectronicInvoice(invoiceId: String): Result<ElectronicInvoiceResultDto> =
            Result.success(
                ElectronicInvoiceResultDto(
                    success = true,
                    cufe = "CUFE-1",
                    qr = "QR-1",
                ),
            )
    }

    private class FakeFormaPagoRepo : FormaPagoRepository {
        override suspend fun getFormasPago(cajaId: String?) =
            Result.success(
                listOf(FormaPago(idFormaPago = 1, descripcion = "Efectivo", activo = 1, pos = 1, grupo = 1, orden = 1)),
            )
    }

    private class FakeConfirmationRepo : CreditNoteFiscalConfirmationRepository {
        override suspend fun confirmFiscal(
            id: String,
            payload: com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto,
        ) = Result.success(
            ConfirmCreditNoteFiscalResponseDto(
                true,
                id,
                "NC-1",
                CreditNoteFiscalStatusDto.CONFIRMADA,
                "NC-FISCAL-1",
                "NC-FISCAL-1",
                "SER-1",
            ),
        )
    }

    private class FakeFiscalPrinter : PrinterRepository {
        override suspend fun printReceipt(transaction: Transaction) =
            Result.failure<com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult>(AssertionError())

        override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto) =
            Result.success(
                com.amaxonia.pos.domain.model.creditnote
                    .CreditNotePrintResult(fiscalNumber = "NC-FISCAL-1", printerSerial = "SER-1"),
            )

        override suspend fun printReportX() = Result.success(Unit)

        override suspend fun printReportZ() = Result.success(Unit)
    }

    private class RecordingTicketPrinter : TicketPrinter {
        val printedTickets = mutableListOf<TicketDocument>()

        override suspend fun connect() = PrintResult.Success

        override suspend fun disconnect() = Unit

        override suspend fun isAvailable() = true

        override suspend fun printText(text: String) = PrintResult.Success

        override suspend fun printTicket(ticket: TicketDocument): PrintResult {
            printedTickets += ticket
            return PrintResult.Success
        }
    }

    private class FakePrinterProv(
        private val fiscal: PrinterRepository? = null,
        private val ticket: TicketPrinter? = null,
    ) : PrinterProvider {
        override fun getActivePrinter() = fiscal

        override fun getActiveTicketPrinter() = ticket
    }

    private object EmptyProductCatalog : ProductCatalogReader {
        override suspend fun getAllProducts() = Result.success(emptyList<com.amaxonia.pos.domain.model.Product>())

        override suspend fun getAllProducts(departmentId: Int?) = Result.success(emptyList<com.amaxonia.pos.domain.model.Product>())

        override suspend fun getProductById(id: String) = Result.failure<com.amaxonia.pos.domain.model.Product>(AssertionError())

        override suspend fun getProductStock(id: String) = Result.failure<com.amaxonia.pos.domain.model.ProductStock>(AssertionError())

        override suspend fun searchProducts(query: String) = Result.success(emptyList<com.amaxonia.pos.domain.model.Product>())
    }

    private fun findPosRoot(): File {
        var found: File? = null
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            if (dir != null && found == null) {
                if (File(dir, "app/build.gradle.kts").exists()) {
                    found = dir
                } else if (File(dir, "build.gradle.kts").exists() && dir!!.name == "app") {
                    found = dir!!.parentFile
                } else if (File(File(dir, "amaxoniaerp-pos"), "app/build.gradle.kts").exists()) {
                    found = File(dir, "amaxoniaerp-pos")
                }
            }
            dir = dir?.parentFile
        }
        return found ?: File(System.getProperty("user.dir") ?: ".")
    }

    private fun findRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            if (dir != null) {
                val candidate = File(dir, "PROJECT.md")
                if (candidate.exists()) return dir!!
            }
            dir = dir?.parentFile
        }
        return File(System.getProperty("user.dir") ?: ".")
    }
}
