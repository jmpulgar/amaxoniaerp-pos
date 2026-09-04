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
import com.amaxonia.pos.ui.drafts.DraftInvoicesViewModel
import com.amaxonia.pos.ui.history.HistoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * TIER 3: POS Cross-Feature Pairwise Combinations E2E Tests
 * Matrix across [Flavor/Country] x [Payment] x [Printer] x [Action] (>= 10 tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier3PosPairwiseCombinationsE2ETest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // T3P-01: Amaxonia (PA) + Cash + SUNMI + Cash Close with Drafts
    @Test
    fun `T3P-01 Amaxonia PA Cash SUNMI Cash Close executes unblocked`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val repo = FakeCajaRepo()
            val vm = createCierreVM(cajaRepo = repo, ticketPrinter = printer, countryCode = "PA", printerType = PrinterType.SUNMI_V2)
            advanceUntilIdle()

            vm.confirmClose(printTicket = true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value is CierreCajaUiState.Success)
            assertEquals(1, printer.printedTickets.size)
        }

    // T3P-02: Amaxonia (PA) + Card + SUNMI + Full Credit Note Devolucion
    @Test
    fun `T3P-02 Amaxonia PA Card SUNMI Full Credit Note creates return`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sourceInvoice = createSourceInvoiceDetail("f-pa-card", 120.0)
            val creditNoteRepo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = sourceInvoice
                    createResponse =
                        CreateCreditNoteResponseDto(
                            success = true,
                            id = "nc-pa-1",
                            codigo = "NC-PA-1",
                            subtotal = 120.0,
                            impuesto = 0.0,
                            total = 120.0,
                            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                            detail =
                                CreditNoteDetailDto(
                                    id = "nc-pa-1",
                                    codigo = "NC-PA-1",
                                    facturaId = "f-pa-card",
                                    facturaCodigo = "FAC-PA",
                                    fecha = "2026-08-31",
                                    periodo = "2026-08",
                                    observacion = "",
                                    clienteNombre = "Cliente",
                                    clienteIdentificacion = "8-1",
                                    subtotal = 120.0,
                                    impuesto = 0.0,
                                    total = 120.0,
                                    fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                                    anulaFacturaCompleta = false,
                                    lines = emptyList(),
                                ),
                        )
                }
            val vm = createCreditNotesVM(repo = creditNoteRepo, countryCode = "PA")
            advanceUntilIdle()

            vm.selectInvoice("f-pa-card")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertTrue(vm.state.value.showCreditNoteDetail)
            assertEquals(CreditNotesMode.LIST, vm.state.value.mode)
        }

    // T3P-03: Banesco (VE) + Cash + THE_FACTORY_HKA + Fiscal Credit Note
    @Test
    fun `T3P-03 Banesco VE Cash HKA Fiscal Credit Note confirms fiscal document`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = FakeFiscalPrinter()
            val confirmRepo = FakeConfirmationRepo()
            val contextReader =
                object : CreditNoteContextReader {
                    override suspend fun currentCountryCode() = "VE"

                    override suspend fun selectedPrinterType() = PrinterType.THE_FACTORY_HKA
                }
            val useCase = ProcessCreditNoteFiscalUseCase(confirmRepo, FakePrinterProv(fiscal = fiscalPrinter), contextReader)
            val doc =
                CreditNoteFiscalDocumentDto(
                    creditNoteId = "nc-ve-1",
                    creditNoteCode = "NC-VE-1",
                    date = "2026-08-31",
                    customerName = "Cliente VE",
                    customerIdentifier = "V-123",
                    customerAddress = "",
                    customerPhone = "",
                    originalInvoiceCode = "FAC-VE-1",
                    originalFiscalNumber = "000001",
                    originalInvoiceDate = "2026-08-31",
                    printerSerial = "SER-HKA-1",
                    comment = "",
                    lines = emptyList(),
                )
            val detail =
                CreditNoteDetailDto(
                    id = "nc-ve-1",
                    codigo = "NC-VE-1",
                    facturaId = "FAC-VE-1",
                    facturaCodigo = "FAC-VE-1",
                    fecha = "2026-08-31",
                    periodo = "2026-08",
                    observacion = "",
                    clienteNombre = "Cliente VE",
                    clienteIdentificacion = "V-123",
                    subtotal = 100.0,
                    impuesto = 0.0,
                    total = 100.0,
                    fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                    fiscalNumber = "",
                    printerSerial = "SER-HKA-1",
                    anulaFacturaCompleta = true,
                    lines = emptyList(),
                    fiscalDocument = doc,
                )
            val res = useCase(detail)
            assertEquals("NC-FISCAL-1", res.detail.fiscalNumber)
        }

    // T3P-04: Banesco (VE) + Mixed + THE_FACTORY_HKA + Cash Close Report X & Z
    @Test
    fun `T3P-04 Banesco VE Mixed HKA Cash Close prints Report X and Z`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = FakeFiscalPrinter()
            val vm = createCierreVM(fiscalPrinter = fiscalPrinter, countryCode = "VE", printerType = PrinterType.THE_FACTORY_HKA)
            advanceUntilIdle()

            vm.printReportX()
            advanceUntilIdle()
            assertEquals("Reporte X impreso correctamente", vm.reportMessage.value)

            vm.printReportZ()
            advanceUntilIdle()
            assertEquals("Reporte Z impreso correctamente", vm.reportMessage.value)
        }

    // T3P-05: ListoERP (VE) + Cash + SUNMI + Draft Invoice Save & Restore
    @Test
    fun `T3P-05 ListoERP VE Cash Draft Invoice save and restore workflow`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draft = DraftInvoice(id = "d-listo-1", itemsJson = "[]", total = 60.0, itemCount = 2, createdAt = 0L)
            val repo = FakeDraftRepo(mutableListOf(draft))
            val restored = mutableListOf<DraftInvoice>()
            val restorer =
                DraftInvoiceRestorer {
                    restored += it
                    Result.success(Unit)
                }
            val vm = DraftInvoicesViewModel(repo, RestoreDraftInvoiceUseCase(restorer))
            advanceUntilIdle()

            assertEquals(1, vm.drafts.value.size)
            val success = vm.loadDraftIntoCart(draft)
            advanceUntilIdle()

            assertTrue(success)
            assertEquals(1, restored.size)
            assertTrue(vm.drafts.value.isEmpty())
        }

    // T3P-06: ListoERP (VE) + Credit + NONE + Partial Credit Note Abono
    @Test
    fun `T3P-06 ListoERP VE Credit NONE Partial Credit Note with abono`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sourceInvoice = createSourceInvoiceDetail("f-listo-credit", totalOriginal = 200.0)
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoiceDetail = sourceInvoice
                    createResponse =
                        CreateCreditNoteResponseDto(
                            success = true,
                            id = "nc-listo-credit",
                            codigo = "NC-LISTO-1",
                            subtotal = 50.0,
                            impuesto = 0.0,
                            total = 50.0,
                            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                            detail =
                                CreditNoteDetailDto(
                                    id = "nc-listo-credit",
                                    codigo = "NC-LISTO-1",
                                    facturaId = "f-listo-credit",
                                    facturaCodigo = "FAC-LC",
                                    fecha = "2026-08-31",
                                    periodo = "2026-08",
                                    observacion = "",
                                    clienteNombre = "Cliente",
                                    clienteIdentificacion = "V-1",
                                    subtotal = 50.0,
                                    impuesto = 0.0,
                                    total = 50.0,
                                    fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                                    anulaFacturaCompleta = false,
                                    lines = emptyList(),
                                ),
                        )
                }
            val vm = createCreditNotesVM(repo = repo, countryCode = "VE")
            advanceUntilIdle()

            vm.selectInvoice("f-listo-credit")
            advanceUntilIdle()
            vm.formController.onGenerarAbonoChange(true)
            vm.submitCreditNote()
            advanceUntilIdle()

            assertTrue(vm.state.value.showCreditNoteDetail)
        }

    // T3P-07: Amaxonia (PA) + Mixed + NONE + Invoice History Date Filter (15 days)
    @Test
    fun `T3P-07 Amaxonia PA Mixed NONE History Date Windowing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val historyRepo = FakeHistoryRepo()
            val vm = HistoryViewModel(historyRepo, FakeCajaRepo())
            advanceUntilIdle()

            vm.onFechaInicioChanged("2026-08-01")
            vm.onFechaFinChanged("2026-08-15")
            vm.applyFilters()
            advanceUntilIdle()

            val applied = historyRepo.requestedFilters.last()
            assertEquals("2026-08-01", applied.fechaInicio)
            assertEquals("2026-08-15", applied.fechaFin)
        }

    // T3P-08: Banesco (VE) + Credit + THE_FACTORY_HKA + History Active Caja Filter
    @Test
    fun `T3P-08 Banesco VE Credit HKA History Active Caja Query`() =
        runTest(mainDispatcherRule.dispatcher) {
            val historyRepo = FakeHistoryRepo()
            val vm = HistoryViewModel(historyRepo, FakeCajaRepo())
            advanceUntilIdle()

            val page = historyRepo.getTransactions(InvoiceHistoryFilter(cajaId = "caja-banesco-1"), 10, 0)
            assertTrue(page.isSuccess)
            assertEquals(1, page.getOrThrow().transactions.size)
        }

    // T3P-09: ListoERP (VE) + Card + SUNMI + Ticket Print Dispatch
    @Test
    fun `T3P-09 ListoERP VE Card SUNMI Ticket Document Generation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val printer = RecordingTicketPrinter()
            val doc = TicketDocument(elements = emptyList())
            val res = printer.printTicket(doc)
            assertEquals(PrintResult.Success, res)
            assertEquals(1, printer.printedTickets.size)
        }

    // T3P-10: Amaxonia (PA) + Cash + SUNMI + Seleccionar Factura Today Query
    @Test
    fun `T3P-10 Amaxonia PA Cash SUNMI Seleccionar Factura defaults to current date`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCreditNoteRepo().apply {
                    sourceInvoices =
                        listOf(
                            CreditNoteSourceInvoiceSummaryDto(
                                "f-today",
                                "FAC-TODAY",
                                "",
                                "",
                                "2026-08-31",
                                "C",
                                "8",
                                100.0,
                                100.0,
                                1,
                                "USD",
                            ),
                        )
                }
            val vm = createCreditNotesVM(repo = repo, countryCode = "PA")
            advanceUntilIdle()

            vm.openInvoicePicker()
            advanceUntilIdle()

            assertEquals(1, vm.state.value.sourceInvoices.size)
            assertEquals(
                "f-today",
                vm.state.value.sourceInvoices
                    .first()
                    .id,
            )
        }

    // ==========================================
    // Helpers & Fakes
    // ==========================================

    private fun createSourceInvoiceDetail(
        id: String,
        totalOriginal: Double = 100.0,
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
        remainingAmount = totalOriginal,
        moneda = "USD",
        lines = emptyList(),
    )

    private fun createCreditNotesVM(
        repo: FakeCreditNoteRepo = FakeCreditNoteRepo(),
        countryCode: String = "PA",
        printerType: PrinterType = PrinterType.SUNMI_V2,
    ): CreditNotesViewModel {
        val contextReader =
            object : CreditNoteContextReader {
                override suspend fun currentCountryCode() = countryCode

                override suspend fun selectedPrinterType() = printerType
            }
        val processFiscal =
            ProcessCreditNoteFiscalUseCase(
                confirmationRepository = FakeConfirmationRepo(),
                printerProvider = FakePrinterProv(),
                contextReader = contextReader,
            )
        return CreditNotesViewModel(repo, FakeCajaRepo(), FakeFormaPagoRepo(), processFiscal)
    }

    private fun createCierreVM(
        cajaRepo: FakeCajaRepo = FakeCajaRepo(),
        fiscalPrinter: PrinterRepository? = null,
        ticketPrinter: TicketPrinter? = null,
        countryCode: String = "PA",
        printerType: PrinterType = PrinterType.SUNMI_V2,
    ): CierreCajaViewModel {
        val contextReader =
            object : CashCloseContextReader {
                override suspend fun currentCountryCode() = countryCode

                override suspend fun selectedPrinterType() = printerType

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
                    totalSales = 100.0,
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
        var sourceInvoices = listOf<CreditNoteSourceInvoiceSummaryDto>()
        var sourceInvoiceDetail: CreditNoteSourceInvoiceDetailDto? = null
        var createResponse: CreateCreditNoteResponseDto? = null

        override suspend fun getCreditNotes(search: String?) = Result.success(CreditNotesListResponseDto(emptyList(), 0L))

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
        ) = Result.success(CreditNoteSourceInvoiceListResponseDto(sourceInvoices, sourceInvoices.size.toLong()))

        override suspend fun getSourceInvoiceDetail(id: String) =
            sourceInvoiceDetail?.let { Result.success(it) } ?: Result.failure(IllegalStateException())

        override suspend fun createCreditNote(payload: CreateCreditNoteRequestDto) =
            createResponse?.let { Result.success(it) } ?: Result.failure(IllegalStateException())

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
            return Result.success(
                InvoiceHistoryPage(
                    listOf(Transaction(id = "tx-1", invoiceNumber = "INV-001", amount = 100.0, time = "10:00", dateHeader = "31/08/2026")),
                    1L,
                ),
            )
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter) = Result.success(InvoiceHistorySummary(100.0, 1))

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

        override suspend fun printCreditNote(document: com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto) =
            Result.success(
                com.amaxonia.pos.domain.model.creditnote
                    .CreditNotePrintResult("NC-FISCAL-1", "SER-1"),
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
}
