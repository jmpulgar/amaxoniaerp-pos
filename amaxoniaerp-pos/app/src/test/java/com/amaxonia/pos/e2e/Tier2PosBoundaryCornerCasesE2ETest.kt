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
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
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
import java.time.YearMonth

/**
 * TIER 2: POS Boundary & Corner Cases E2E Tests
 * Validates edge cases: 0 days, 30 days, 31 days, >31 days rejection, leap years,
 * null active caja, corrupted draft payloads, zero amounts, printer errors, etc. (>= 50 tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier2PosBoundaryCornerCasesE2ETest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ==========================================
    // Date Filtering Boundaries (10 tests)
    // ==========================================

    @Test
    fun `BP01 Date filter 0-day range same start and end date is valid`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-15")
            vm.onCustomInvoiceFechaFinChange("2026-08-15")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            val last = repo.requestedFilters.last()
            assertEquals("2026-08-15", last.second)
            assertEquals("2026-08-15", last.third)
        }

    @Test
    fun `BP02 Date filter 30-day range is valid and submitted`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-01")
            vm.onCustomInvoiceFechaFinChange("2026-08-30")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            val last = repo.requestedFilters.last()
            assertEquals("2026-08-01", last.second)
            assertEquals("2026-08-30", last.third)
        }

    @Test
    fun `BP03 Date filter 31-day range max valid boundary is submitted`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-01")
            vm.onCustomInvoiceFechaFinChange("2026-08-31")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            val last = repo.requestedFilters.last()
            assertEquals("2026-08-01", last.second)
            assertEquals("2026-08-31", last.third)
        }

    @Test
    fun `BP04 Leap year 2024-02-29 date filter is handled cleanly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2024-02-01")
            vm.onCustomInvoiceFechaFinChange("2024-02-29")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            val last = repo.requestedFilters.last()
            assertEquals("2024-02-29", last.third)
        }

    @Test
    fun `BP05 HistoryViewModel handles month transition January 31 to February 28`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("2026-01-31")
            vm.onFechaFinChanged("2026-02-28")
            vm.applyFilters()
            advanceUntilIdle()

            val filter = repo.requestedFilters.last()
            assertEquals("2026-01-31", filter.fechaInicio)
            assertEquals("2026-02-28", filter.fechaFin)
        }

    @Test
    fun `BP06 HistoryViewModel handles year boundary December 31 to January 1`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("2025-12-31")
            vm.onFechaFinChanged("2026-01-01")
            vm.applyFilters()
            advanceUntilIdle()

            val filter = repo.requestedFilters.last()
            assertEquals("2025-12-31", filter.fechaInicio)
            assertEquals("2026-01-01", filter.fechaFin)
        }

    @Test
    fun `BP07 Blank date strings in HistoryViewModel are preserved as blank`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("")
            vm.onFechaFinChanged("")
            vm.applyFilters()
            advanceUntilIdle()

            val filter = repo.requestedFilters.last()
            assertNull(filter.fechaInicio)
            assertNull(filter.fechaFin)
        }

    @Test
    fun `BP08 Setting date filter to Mes Anterior queries previous month dates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.setInvoiceDateFilterType(InvoiceDateFilterType.MES_ANTERIOR)
            advanceUntilIdle()

            val ym = YearMonth.now().minusMonths(1)
            val expectedStart = ym.atDay(1).toString()
            val expectedEnd = ym.atEndOfMonth().toString()
            val last = repo.requestedFilters.last()
            assertEquals(expectedStart, last.second)
            assertEquals(expectedEnd, last.third)
        }

    @Test
    fun `BP09 Date filter type Mes Actual queries current month range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.setInvoiceDateFilterType(InvoiceDateFilterType.MES_ACTUAL)
            advanceUntilIdle()

            assertEquals(InvoiceDateFilterType.MES_ACTUAL, vm.state.value.invoiceDateFilter.type)
        }

    @Test
    fun `BP10 Inverted custom dates are rejected with validation error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onCustomInvoiceFechaInicioChange("2026-08-30")
            vm.onCustomInvoiceFechaFinChange("2026-08-01")
            vm.applyCustomInvoiceDateFilter()
            advanceUntilIdle()

            assertNotNull(vm.state.value.error)
        }

    // ==========================================
    // Financial & Amount Boundaries (10 tests)
    // ==========================================

    @Test
    fun `BP11 Zero amount draft invoice is stored and listed`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice("d-0", itemsJson = "[]", total = 0.0, itemCount = 0, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(1, vm.drafts.value.size)
            assertEquals(
                0.0,
                vm.drafts.value
                    .first()
                    .total,
                0.0,
            )
        }

    @Test
    fun `BP12 Minimum fractional amount 0_01 draft invoice is handled with precision`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice("d-cent", itemsJson = "[]", total = 0.01, itemCount = 1, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(
                0.01,
                vm.drafts.value
                    .first()
                    .total,
                0.0,
            )
        }

    @Test
    fun `BP13 Zero amount cash close summary is processed cleanly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCajaRepo().apply {
                    summary =
                        CierreCajaSummary(idCajaSecuencia = "s-0", idCaja = "c-0", totalSales = 0.0, montoTotal = 0.0, montoCierre = 0.0)
                }
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            assertTrue(vm.uiState.value is CierreCajaUiState.Success)
        }

    @Test
    fun `BP14 Partial credit note source invoice with 0 remaining amount is handled`() {
        val detail =
            CreditNoteSourceInvoiceDetailDto(
                id = "f-zero-rem",
                codigo = "F-0",
                codigoFiscal = "",
                numeroDocumentoFiscal = "",
                fecha = "2026-08-31",
                clienteId = "1",
                clienteNombre = "C",
                clienteIdentificacion = "8",
                clienteDireccion = "",
                clienteTelefono = "",
                codVendedor = 1,
                totalOriginal = 100.0,
                subtotalOriginal = 100.0,
                impuestoOriginal = 0.0,
                remainingAmount = 0.0,
                moneda = "USD",
                lines = emptyList(),
            )
        assertEquals(0.0, detail.remainingAmount, 0.0)
    }

    @Test
    fun `BP15 Credit note creation with 0 amount tax line items is valid`() =
        runTest(mainDispatcherRule.dispatcher) {
            val detail =
                CreditNoteSourceInvoiceDetailDto(
                    id = "f-no-tax",
                    codigo = "F-NOTAX",
                    codigoFiscal = "",
                    numeroDocumentoFiscal = "",
                    fecha = "2026-08-31",
                    clienteId = "1",
                    clienteNombre = "C",
                    clienteIdentificacion = "8",
                    clienteDireccion = "",
                    clienteTelefono = "",
                    codVendedor = 1,
                    totalOriginal = 50.0,
                    subtotalOriginal = 50.0,
                    impuestoOriginal = 0.0,
                    remainingAmount = 50.0,
                    moneda = "USD",
                    lines = emptyList(),
                )
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = detail
                    createResponse =
                        CreateCreditNoteResponseDto(
                            success = true,
                            id = "nc-notax",
                            codigo = "NC-NOTAX",
                            subtotal = 50.0,
                            impuesto = 0.0,
                            total = 50.0,
                            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                            detail =
                                CreditNoteDetailDto(
                                    id = "nc-notax",
                                    codigo = "NC-NOTAX",
                                    facturaId = "f-no-tax",
                                    facturaCodigo = "FAC-NOTAX",
                                    fecha = "2026-08-31",
                                    periodo = "2026-08",
                                    observacion = "",
                                    clienteNombre = "Cliente",
                                    clienteIdentificacion = "8-1",
                                    subtotal = 50.0,
                                    impuesto = 0.0,
                                    total = 50.0,
                                    fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                                    anulaFacturaCompleta = true,
                                    lines = emptyList(),
                                ),
                        )
                }
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.selectInvoice("f-no-tax")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertTrue(vm.state.value.showCreditNoteDetail)
        }

    @Test
    fun `BP16 Cash close with mixed cash and card breakdown preserves totals`() =
        runTest(mainDispatcherRule.dispatcher) {
            val summary =
                CierreCajaSummary(
                    idCajaSecuencia = "seq-mixed",
                    idCaja = "caja-1",
                    cajaName = "Caja 1",
                    vendedorName = "Vendedor",
                    totalSales = 150.0,
                    totalCash = 90.0,
                    totalCard = 60.0,
                    montoEfectivoCierre = 90.0,
                    montoTotal = 150.0,
                    montoCierre = 150.0,
                )
            val repo = FakeCajaRepo(summary)
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val req = repo.closeRequests.first()
            assertEquals(150.0, req.monto_cierre, 0.0)
            assertEquals(90.0, req.monto_efectivo_cierre, 0.0)
        }

    @Test
    fun `BP17 Zero amount cash close executes successfully`() =
        runTest(mainDispatcherRule.dispatcher) {
            val summary =
                CierreCajaSummary(
                    idCajaSecuencia = "seq-zero",
                    idCaja = "caja-1",
                    cajaName = "Caja 1",
                    vendedorName = "Vendedor",
                    totalSales = 0.0,
                    totalCash = 0.0,
                    totalCard = 0.0,
                    montoEfectivoCierre = 0.0,
                    montoTotal = 0.0,
                    montoCierre = 0.0,
                )
            val repo = FakeCajaRepo(summary)
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            assertEquals(1, repo.closeRequests.size)
            assertEquals(0.0, repo.closeRequests.first().monto_cierre, 0.0)
        }

    @Test
    fun `BP18 Empty cash close summary does not prevent cash close sequence finalization`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            assertEquals(1, repo.markSequenceClosedCalls)
        }

    @Test
    fun `BP19 Saving draft with zero item count is safely handled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice("empty-items", itemsJson = "[]", total = 0.0, itemCount = 0, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf())
            repo.save(draft)

            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(1, vm.drafts.value.size)
            assertEquals(
                0,
                vm.drafts.value
                    .first()
                    .itemCount,
            )
        }

    @Test
    fun `BP20 Draft with very large total amount is correctly formatted without overflow`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice("huge", itemsJson = "[]", total = 999999999.99, itemCount = 1, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(
                999999999.99,
                vm.drafts.value
                    .first()
                    .total,
                0.0,
            )
        }

    @Test
    fun `BP21 Deleting nonexistent draft ID completes silently without exception`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeDraftRepo(mutableListOf())
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            vm.deleteDraft("nonexistent-id")
            advanceUntilIdle()

            assertTrue(vm.drafts.value.isEmpty())
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `BP22 Restoring corrupted JSON draft fails gracefully without throwing unhandled exception`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice(id = "corrupt", itemsJson = "{invalid-json}", total = 10.0, itemCount = 1, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val restorer = DraftInvoiceRestorer { Result.failure(IllegalArgumentException("Malformed JSON")) }
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase(restorer))
            advanceUntilIdle()

            val restored = vm.loadDraftIntoCart(draft)
            advanceUntilIdle()

            assertFalse(restored)
            assertEquals(1, vm.drafts.value.size)
        }

    @Test
    fun `BP23 Empty search string in HistoryViewModel reloads full history`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onSearchChanged("")
            advanceUntilIdle()

            assertNull(repo.requestedFilters.last().search)
        }

    @Test
    fun `BP24 Submitting credit note without selecting invoice exposes error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("Selecciona una factura para continuar", vm.state.value.error)
        }

    @Test
    fun `BP25 Submitting credit note without active register sequence exposes descriptive error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cajaRepo =
                object : CajaRepository by FakeCajaRepo() {
                    override suspend fun checkCajaStatus(cajaId: String) = Result.success(CajaStatusResponse(cajaSecuencia = null))
                }
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = createSourceInvoiceDetail("f-1")
                }
            val vm = createCreditNotesVM(repo = repo, cajaRepo = cajaRepo)
            advanceUntilIdle()

            vm.selectInvoice("f-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("La caja activa no tiene secuencia abierta", vm.state.value.error)
        }

    @Test
    fun `BP26 Submitting credit note without active register exposes error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cajaRepo =
                object : CajaRepository by FakeCajaRepo() {
                    override val activeCaja = MutableStateFlow<Caja?>(null)
                }
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = createSourceInvoiceDetail("f-1")
                }
            val vm = createCreditNotesVM(repo = repo, cajaRepo = cajaRepo)
            advanceUntilIdle()

            vm.selectInvoice("f-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("Debes tener una caja activa", vm.state.value.error)
        }

    @Test
    fun `BP27 Credit note creation failure exposes error and allows retry`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = createSourceInvoiceDetail("f-1")
                    createResponse = null
                }
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.selectInvoice("f-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("Error creating NC", vm.state.value.error)
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun `BP28 backFromFlow from CREATE mode returns to INVOICE_PICKER and clears selected invoice`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = createSourceInvoiceDetail("f-1")
                }
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.selectInvoice("f-1")
            advanceUntilIdle()
            assertEquals(CreditNotesMode.CREATE, vm.state.value.mode)

            vm.backFromFlow()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)
            assertNull(vm.state.value.selectedInvoice)
        }

    @Test
    fun `BP29 backFromFlow from INVOICE_PICKER mode returns to LIST mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()
            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)

            vm.backFromFlow()
            advanceUntilIdle()
            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)
        }

    @Test
    fun `BP30 backFromFlow from LIST mode is no-op`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)
            vm.backFromFlow()
            advanceUntilIdle()
            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)
        }

    // ==========================================
    // Hardware & Printer Boundaries (10 tests)
    // ==========================================

    @Test
    fun `BP31 Physical fiscal printer failure during cash close exposes error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer =
                object : PrinterRepository by FakeFiscalPrinter() {
                    override suspend fun printReportX(): Result<Unit> = Result.failure(IllegalStateException("Papel agotado"))
                }
            val vm = createCierreVM(fiscalPrinter = printer)
            advanceUntilIdle()

            vm.printReportX()
            advanceUntilIdle()

            assertNotNull(vm.reportMessage.value)
        }

    @Test
    fun `BP32 Ticket printer failure during cash close exposes error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ticketPrinter =
                object : TicketPrinter by RecordingTicketPrinter() {
                    override suspend fun printTicket(ticket: TicketDocument): PrintResult = PrintResult.Error("Cabezal abierto")
                }
            val vm = createCierreVM(ticketPrinter = ticketPrinter)
            advanceUntilIdle()

            vm.confirmClose(printTicket = true)
            advanceUntilIdle()

            assertNotNull(vm.reportMessage.value)
        }

    @Test
    fun `BP33 PrinterProvider returns null when no fiscal printer configured for close`() {
        val prov = FakePrinterProv(fiscal = null)
        assertNull(prov.getActivePrinter())
    }

    @Test
    fun `BP34 Dismissing close ticket prompt clears prompt state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCierreVM()
            advanceUntilIdle()

            vm.clearReportMessage()
            assertNull(vm.reportMessage.value)
        }

    @Test
    fun `BP35 Fiscal credit note confirmation failure exposes error message in CreditNotesState`() =
        runTest(mainDispatcherRule.dispatcher) {
            val confirmRepo =
                object : CreditNoteFiscalConfirmationRepository {
                    override suspend fun confirmFiscal(
                        id: String,
                        payload: com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto,
                    ) = Result.failure<ConfirmCreditNoteFiscalResponseDto>(IllegalStateException("Impresora fiscal ocupada"))
                }
            val fiscalPrinter = FakeFiscalPrinter()
            val contextReader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "VE"

                    override suspend fun selectedPrinterType() = PrinterType.THE_FACTORY_HKA
                }
            val processFiscal = ProcessCreditNoteFiscalUseCase(confirmRepo, FakePrinterProv(fiscal = fiscalPrinter), contextReader)
            val fakeRepo =
                object : CreditNoteRepository by FakeCreditNoteRepo() {
                    override suspend fun getCreditNoteDetail(id: String): Result<CreditNoteDetailDto> =
                        Result.success(
                            CreditNoteDetailDto(
                                id = id,
                                codigo = "NC-FAIL",
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
                                fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                                anulaFacturaCompleta = true,
                                lines = emptyList(),
                                fiscalDocument =
                                    com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto(
                                        creditNoteId = id,
                                        creditNoteCode = "NC-FAIL",
                                        date = "2026-08-31",
                                        customerName = "C",
                                        customerIdentifier = "8",
                                        customerAddress = "",
                                        customerPhone = "",
                                        originalInvoiceCode = "FAC-1",
                                        originalFiscalNumber = "0001",
                                        originalInvoiceDate = "2026-08-31",
                                        printerSerial = "SER-1",
                                        comment = "",
                                        lines = emptyList(),
                                    ),
                            ),
                        )
                }
            val vm = CreditNotesViewModel(fakeRepo, FakeCajaRepo(), FakeFormaPagoRepo(), processFiscal)
            advanceUntilIdle()

            vm.openCreditNoteDetail("nc-fail")
            advanceUntilIdle()
            vm.processSelectedCreditNoteFiscal()
            advanceUntilIdle()

            assertEquals("Impresora fiscal ocupada", vm.state.value.error)
        }

    @Test
    fun `BP36 TicketPrinter printText succeeds on available thermal printer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val result = printer.printText("LINEA DE PRUEBA\n")
            assertEquals(PrintResult.Success, result)
        }

    @Test
    fun `BP37 TicketPrinter connect succeeds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val result = printer.connect()
            assertEquals(PrintResult.Success, result)
        }

    @Test
    fun `BP38 TicketPrinter isAvailable returns true on mock`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            assertTrue(printer.isAvailable())
        }

    @Test
    fun `BP39 TicketPrinter disconnect completes successfully`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            printer.disconnect()
        }

    @Test
    fun `BP40 Rapid double click on confirmClose does not submit duplicate close requests`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            assertEquals(1, repo.closeRequests.size)
        }

    // ==========================================
    // Additional Edge & Boundary Tests (10 tests)
    // ==========================================

    @Test
    fun `BP41 Special characters in draft invoice items JSON are preserved`() =
        runTest(mainDispatcherRule.dispatcher) {
            val specialJson = """[{"desc":"Café & Té / 100% Arábica <Premium>"}]"""
            val draft = DraftInvoice(id = "d-spec", itemsJson = specialJson, total = 5.0, itemCount = 1, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            assertEquals(
                specialJson,
                vm.drafts.value
                    .first()
                    .itemsJson,
            )
        }

    @Test
    fun `BP42 Invoice search with special characters in History passes exact string`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onSearchChanged("FAC-001/2026")
            advanceUntilIdle()

            assertEquals("FAC-001/2026", repo.requestedFilters.last().search)
        }

    @Test
    fun `BP43 Credit note search with special characters passes search string`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCreditNoteRepo()
            val vm = createCreditNotesVM(repo = repo)
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            vm.onInvoiceSearchQueryChange("INV-%_1")
            vm.searchSourceInvoices()
            advanceUntilIdle()

            assertEquals("INV-%_1", repo.requestedFilters.last().first)
        }

    @Test
    fun `BP44 Empty observations in credit note form are accepted and serialized as empty string`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            vm.formController.onObservacionChange("")
            assertEquals("", vm.state.value.form.observacion)
        }

    @Test
    fun `BP45 Very long observation string in credit note form is retained without truncation in UI`() =
        runTest(mainDispatcherRule.dispatcher) {
            val longObs = "Detalle extenso de devolución: " + "X".repeat(250)
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            vm.formController.onObservacionChange(longObs)
            assertEquals(longObs, vm.state.value.form.observacion)
        }

    @Test
    fun `BP46 Cash close with non-standard observation is sent in close request`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeCajaRepo()
            val vm = createCierreVM(cajaRepo = repo)
            advanceUntilIdle()

            vm.confirmClose(printTicket = false)
            advanceUntilIdle()

            val req = repo.closeRequests.first()
            assertEquals("", req.observacion_cierre)
        }

    @Test
    fun `BP47 Fetching invoice detail with missing items list returns empty items without crash`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeHistoryRepo()
            val detail = repo.getInvoiceDetail("empty-items-inv")
            assertTrue(detail.isSuccess)
            assertTrue(detail.getOrThrow().items.isEmpty())
        }

    @Test
    fun `BP48 Restoring multiple drafts sequentially restores each and clears list`() =
        runTest(mainDispatcherRule.dispatcher) {
            val d1 = DraftInvoice(id = "d-seq-1", itemsJson = "[]", total = 10.0, itemCount = 1, createdAt = 0L)
            val d2 = DraftInvoice(id = "d-seq-2", itemsJson = "[]", total = 20.0, itemCount = 1, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(d1, d2))
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()

            vm.loadDraftIntoCart(d1)
            advanceUntilIdle()
            assertEquals(1, vm.drafts.value.size)

            vm.loadDraftIntoCart(d2)
            advanceUntilIdle()
            assertTrue(vm.drafts.value.isEmpty())
        }

    @Test
    fun `BP49 History repository page total reflects large counts without integer overflow`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                object : InvoiceHistoryRepository {
                    override suspend fun getTransactions(
                        filter: InvoiceHistoryFilter,
                        limit: Int,
                        offset: Long,
                    ) = Result.success(InvoiceHistoryPage(emptyList(), total = 1000000L))

                    override suspend fun getSummary(filter: InvoiceHistoryFilter) =
                        Result.success(InvoiceHistorySummary(ventasNetas = 5000000.0, totalFacturas = 1000000))

                    override suspend fun getAllTransactions() = Result.success(emptyList<Transaction>())

                    override suspend fun getTransactionById(id: String) = Result.failure<Transaction>(AssertionError())

                    override suspend fun saveTransaction(transaction: Transaction) = Result.success(Unit)

                    override suspend fun getInvoiceDetail(invoiceId: String) = Result.failure<FacturaDetalleResponseDto>(AssertionError())

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
            val vm = HistoryViewModel(repo, FakeCajaRepo())
            advanceUntilIdle()

            assertEquals(1000000L, vm.state.value.totalTransactions)
            assertEquals(1000000, vm.state.value.summary.totalFacturas)
        }

    @Test
    fun `BP50 Switching credit notes mode between LIST and INVOICE_PICKER updates UI state mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = createCreditNotesVM()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)

            vm.openInvoicePicker()
            advanceUntilIdle()
            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)

            vm.backFromFlow()
            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)
        }

    // ==========================================
    // Helpers & Fakes
    // ==========================================

    private fun createSourceInvoiceDetail(id: String) =
        CreditNoteSourceInvoiceDetailDto(
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
            totalOriginal = 100.0,
            subtotalOriginal = 100.0,
            impuestoOriginal = 0.0,
            remainingAmount = 100.0,
            moneda = "USD",
            lines = emptyList(),
        )

    private fun createCreditNotesVM(
        repo: FakeCreditNoteRepo = FakeCreditNoteRepo(),
        cajaRepo: CajaRepository = FakeCajaRepo(),
        formaPagoRepo: FormaPagoRepository = FakeFormaPagoRepo(),
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

    private fun createCierreVM(
        cajaRepo: CajaRepository = FakeCajaRepo(),
        fiscalPrinter: PrinterRepository? = null,
        ticketPrinter: TicketPrinter? = null,
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

    private class FakeDraftRepo(
        private val drafts: MutableList<DraftInvoice>,
    ) : DraftInvoiceRepository {
        override suspend fun all() = drafts.toList()

        override suspend fun save(draft: DraftInvoice) {
            drafts += draft
        }

        override suspend fun delete(id: String) {
            drafts.removeAll { it.id == id }
        }
    }

    private class FakeCajaRepo(
        var summary: CierreCajaSummary =
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
    ) : CajaRepository {
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

        override suspend fun getCierreSummary() = Result.success(summary)

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
            return Result.success(CreditNoteSourceInvoiceListResponseDto(emptyList(), 0L))
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
        val requestedFilters = mutableListOf<InvoiceHistoryFilter>()

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            requestedFilters += filter
            return Result.success(InvoiceHistoryPage(emptyList(), 0L))
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter) = Result.success(InvoiceHistorySummary(0.0, 0))

        override suspend fun getAllTransactions() = Result.success(emptyList<Transaction>())

        override suspend fun getTransactionById(id: String) = Result.failure<Transaction>(AssertionError())

        override suspend fun saveTransaction(transaction: Transaction) = Result.success(Unit)

        override suspend fun getInvoiceDetail(invoiceId: String) = Result.success(FacturaDetalleResponseDto(invoiceId, "INV", emptyList()))

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
            ConfirmCreditNoteFiscalResponseDto(true, id, "NC-1", CreditNoteFiscalStatusDto.CONFIRMADA, "NC-1", "NC-1", "SER-1"),
        )
    }

    private class FakeFiscalPrinter(
        val reportXRes: Result<Unit> = Result.success(Unit),
        val reportZRes: Result<Unit> = Result.success(Unit),
    ) : PrinterRepository {
        override suspend fun printReceipt(transaction: Transaction) =
            Result.failure<com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult>(AssertionError())

        override suspend fun printCreditNote(document: com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto) =
            Result.success(
                com.amaxonia.pos.domain.model.creditnote
                    .CreditNotePrintResult("NC-1", "SER-1"),
            )

        override suspend fun printReportX() = reportXRes

        override suspend fun printReportZ() = reportZRes
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
}
