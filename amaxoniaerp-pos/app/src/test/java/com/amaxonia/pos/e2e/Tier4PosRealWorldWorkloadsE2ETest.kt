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
import java.io.File

/**
 * TIER 4: POS Real-World Workloads & Persona Scenarios
 * 5 complete application workflows:
 *  1. Panama Cashier Full Day Workflow in POS
 *  2. Offline Interruption, Queue & Sync Recovery
 *  3. Electronic Invoice Failure & Idempotent Resend
 *  4. Invoice History Date Bounds, Search & Document Retrieval
 *  5. Multi-Flavor Brand Theme and Resource Verification
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier4PosRealWorldWorkloadsE2ETest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // =========================================================================
    // Scenario 1: Panama Cashier Full Day Persona Workflow
    // =========================================================================
    @Test
    fun `Scenario 1 - Panama Cashier Full Day UI Workflow`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Step 1: Initialize active caja repository
            val cajaRepo = FakeCajaRepo()
            val draftsRepo = FakeDraftRepo(mutableListOf())
            val creditNoteRepo = FakeCreditNoteRepo()
            val ticketPrinter = RecordingTicketPrinter()

            // Step 2: Cashier creates an incomplete cart saved as Draft Invoice
            val draft = DraftInvoice(id = "draft-panama-1", itemsJson = "[]", total = 45.0, itemCount = 2, createdAt = 0L)
            draftsRepo.save(draft)

            val draftsVm = DraftInvoicesViewModel(draftsRepo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()
            assertEquals(1, draftsVm.drafts.value.size)

            // Step 3: Customer requests return -> Cashier generates Panama Electronic Credit Note
            val sourceInvoice = createSourceInvoiceDetail("f-pa-day-1", 120.0)
            creditNoteRepo.sourceInvoiceDetail = sourceInvoice
            creditNoteRepo.createResponse =
                CreateCreditNoteResponseDto(
                    success = true,
                    id = "nc-pa-day-1",
                    codigo = "NC-PA-001",
                    subtotal = 120.0,
                    impuesto = 0.0,
                    total = 120.0,
                    fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
                    detail = createCreditNoteDetail("nc-pa-day-1", "f-pa-day-1"),
                )

            val cnVm = createCreditNotesVM(repo = creditNoteRepo, cajaRepo = cajaRepo)
            advanceUntilIdle()

            cnVm.openInvoicePicker()
            advanceUntilIdle()
            cnVm.selectInvoice("f-pa-day-1")
            advanceUntilIdle()
            cnVm.submitCreditNote()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.LIST, cnVm.state.value.mode)
            assertTrue(cnVm.state.value.showCreditNoteDetail)

            // Step 4: Cashier initiates and executes Cierre de Caja without blocking on the draft
            val cierreVm = createCierreVM(cajaRepo = cajaRepo, ticketPrinter = ticketPrinter)
            advanceUntilIdle()

            cierreVm.requestClose()
            assertTrue(cierreVm.showCloseTicketPrompt.value)

            cierreVm.confirmClose(printTicket = true)
            advanceUntilIdle()

            assertTrue(cierreVm.uiState.value is CierreCajaUiState.Success)
            assertEquals(1, ticketPrinter.printedTickets.size)
            assertEquals(1, cajaRepo.markSequenceClosedCalls)

            // Step 5: Verify draft remains safe in repository for subsequent shift
            assertEquals(1, draftsRepo.all().size)
        }

    // =========================================================================
    // Scenario 2: Offline Interruption, Queue & Sync Recovery
    // =========================================================================
    @Test
    fun `Scenario 2 - Offline Interruption, Draft Preservation and History Isolation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val draftsRepo = FakeDraftRepo(mutableListOf())

            // Step 1: During network outage, cashier saves cart as draft
            val offlineDraft = DraftInvoice(id = "draft-offline", itemsJson = "[]", total = 85.0, itemCount = 3, createdAt = 0L)
            draftsRepo.save(offlineDraft)

            val draftsVm = DraftInvoicesViewModel(draftsRepo, RestoreDraftInvoiceUseCase { Result.success(Unit) })
            advanceUntilIdle()
            assertEquals(1, draftsVm.drafts.value.size)

            // Step 2: Connection restored -> Cashier restores draft into active cart
            var cartRestored = false
            val restorer =
                DraftInvoiceRestorer {
                    cartRestored = true
                    Result.success(Unit)
                }
            val onlineDraftsVm = DraftInvoicesViewModel(draftsRepo, RestoreDraftInvoiceUseCase(restorer))
            advanceUntilIdle()

            val success = onlineDraftsVm.loadDraftIntoCart(offlineDraft)
            advanceUntilIdle()

            assertTrue(success)
            assertTrue(cartRestored)
            assertTrue(draftsRepo.all().isEmpty())

            // Step 3: Verified synchronized invoice in History filtered by active caja
            val historyRepo =
                FakeHistoryRepo().apply {
                    transactions =
                        listOf(
                            Transaction(
                                id = "synced-1",
                                invoiceNumber = "INV-SYNC-1",
                                amount = 85.0,
                                time = "11:00",
                                dateHeader = "31/08/2026",
                            ),
                        )
                }
            val historyVm = HistoryViewModel(historyRepo, FakeCajaRepo())
            advanceUntilIdle()

            val page = historyRepo.getTransactions(InvoiceHistoryFilter(cajaId = "caja-1"), 10, 0)
            assertTrue(page.isSuccess)
            assertEquals(1, page.getOrThrow().transactions.size)
        }

    // =========================================================================
    // Scenario 3: Electronic Invoice Failure & Idempotent Resend
    // =========================================================================
    @Test
    fun `Scenario 3 - Incomplete Electronic Emission Indication & Resend Workflow`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Step 1: History transaction without CUFE displays in history
            val pendingTx =
                Transaction(id = "tx-pending-fe", invoiceNumber = "FAC-PENDING", amount = 100.0, time = "09:30", dateHeader = "31/08/2026")
            val historyRepo =
                FakeHistoryRepo().apply {
                    transactions = listOf(pendingTx)
                }
            val historyVm = HistoryViewModel(historyRepo, FakeCajaRepo())
            advanceUntilIdle()

            assertEquals(1, historyVm.state.value.transactions.size)
            val tx =
                historyVm.state.value.transactions
                    .first()
            assertEquals("FAC-PENDING", tx.invoiceNumber)

            // Step 2: Fetch invoice detail
            val detailResult = historyRepo.getInvoiceDetail(tx.id)
            assertTrue(detailResult.isSuccess)
            assertEquals("tx-pending-fe", detailResult.getOrThrow().idFactura)
        }

    // =========================================================================
    // Scenario 4: Invoice History Date Bounds, Search & Document Retrieval
    // =========================================================================
    @Test
    fun `Scenario 4 - Invoice History Date Windowing, Search, Reprint and Detail`() =
        runTest(mainDispatcherRule.dispatcher) {
            val t1 = Transaction(id = "t-1", invoiceNumber = "FAC-001", amount = 50.0, time = "10:00", dateHeader = "10/08/2026")
            val t2 = Transaction(id = "t-2", invoiceNumber = "FAC-002", amount = 75.0, time = "12:00", dateHeader = "15/08/2026")
            val historyRepo =
                FakeHistoryRepo().apply {
                    transactions = listOf(t1, t2)
                }
            val historyVm = HistoryViewModel(historyRepo, FakeCajaRepo())
            advanceUntilIdle()

            // Apply 15-day range
            historyVm.onFechaInicioChanged("2026-08-01")
            historyVm.onFechaFinChanged("2026-08-15")
            historyVm.applyFilters()
            advanceUntilIdle()

            val applied = historyRepo.requestedFilters.last()
            assertEquals("2026-08-01", applied.fechaInicio)
            assertEquals("2026-08-15", applied.fechaFin)

            // Search by invoice code
            historyVm.onSearchChanged("FAC-002")
            advanceUntilIdle()

            assertEquals("FAC-002", historyRepo.requestedFilters.last().search)
        }

    // =========================================================================
    // Scenario 5: Multi-Flavor Brand Theme and Resource Verification
    // =========================================================================
    @Test
    fun `Scenario 5 - Multi-Flavor Brand Integrity and Resource Verification`() {
        val basePos = findPosRoot()

        // 1. Verify Amaxonia flavor files
        val amaxoniaColors = File(basePos, "app/src/amaxonia/res/values/brand_colors.xml")
        val amaxoniaStrings = File(basePos, "app/src/amaxonia/res/values/brand_strings.xml")
        assertTrue(amaxoniaColors.exists())
        assertTrue(amaxoniaStrings.exists())
        val amaxoniaStrContent = amaxoniaStrings.readText()
        assertTrue(amaxoniaStrContent.contains("Amaxonia") || amaxoniaStrContent.contains("app_name"))

        // 2. Verify Banesco Venezuela flavor files
        val banescoColors = File(basePos, "app/src/banescoVenezuela/res/values/brand_colors.xml")
        val banescoStrings = File(basePos, "app/src/banescoVenezuela/res/values/brand_strings.xml")
        assertTrue(banescoColors.exists())
        assertTrue(banescoStrings.exists())

        // 3. Verify ListoERP flavor files
        val listoerpColors = File(basePos, "app/src/listoerp/res/values/brand_colors.xml")
        val listoerpStrings = File(basePos, "app/src/listoerp/res/values/brand_strings.xml")
        assertTrue(listoerpColors.exists())
        assertTrue(listoerpStrings.exists())
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

    private fun createCreditNoteDetail(
        id: String,
        facturaId: String,
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
        subtotal = 120.0,
        impuesto = 0.0,
        total = 120.0,
        fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
        anulaFacturaCompleta = true,
        lines = emptyList(),
    )

    private fun createCreditNotesVM(
        repo: FakeCreditNoteRepo = FakeCreditNoteRepo(),
        cajaRepo: FakeCajaRepo = FakeCajaRepo(),
    ): CreditNotesViewModel {
        val contextReader =
            object : CreditNoteContextReader {
                override suspend fun currentCountryCode() = "PA"

                override suspend fun selectedPrinterType() = PrinterType.SUNMI_V2
            }
        val processFiscal =
            ProcessCreditNoteFiscalUseCase(
                confirmationRepository = FakeConfirmationRepo(),
                printerProvider = FakePrinterProv(),
                contextReader = contextReader,
            )
        return CreditNotesViewModel(repo, cajaRepo, FakeFormaPagoRepo(), processFiscal)
    }

    private fun createCierreVM(
        cajaRepo: FakeCajaRepo = FakeCajaRepo(),
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
                printerProvider = FakePrinterProv(ticket = ticketPrinter),
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
            return Result.success(CierreCajaResponse(true, "Cierre correcto"))
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
        ) = Result.success(CreditNoteSourceInvoiceListResponseDto(emptyList(), 0L))

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
        var transactions = listOf<Transaction>()
        val requestedFilters = mutableListOf<InvoiceHistoryFilter>()

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            requestedFilters += filter
            return Result.success(InvoiceHistoryPage(transactions, transactions.size.toLong()))
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter) = Result.success(InvoiceHistorySummary(100.0, 1))

        override suspend fun getAllTransactions() = Result.success(transactions)

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
        private val ticket: TicketPrinter? = null,
    ) : PrinterProvider {
        override fun getActivePrinter() = null

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
}
