package com.amaxonia.pos.ui.creditnotes

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
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.CreditNoteFiscalConfirmationRepository
import com.amaxonia.pos.domain.repository.CreditNoteRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.domain.usecase.creditnote.ProcessCreditNoteFiscalUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreditNotesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceInvoice =
        CreditNoteSourceInvoiceDetailDto(
            id = "fact-1",
            codigo = "F-001",
            codigoFiscal = "",
            numeroDocumentoFiscal = "",
            fecha = "2026-01-01",
            clienteId = "1",
            clienteNombre = "Cliente",
            clienteIdentificacion = "V-1",
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

    private val createdDetail =
        CreditNoteDetailDto(
            id = "nc-1",
            codigo = "NC-1",
            facturaId = "fact-1",
            facturaCodigo = "F-001",
            fecha = "2026-01-01",
            periodo = "2026-01",
            observacion = "",
            clienteNombre = "Cliente",
            clienteIdentificacion = "V-1",
            subtotal = 100.0,
            impuesto = 0.0,
            total = 100.0,
            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
            anulaFacturaCompleta = true,
            lines = emptyList(),
        )

    @Test
    fun `al iniciar carga notas y metodos de reintegro sin punto de venta`() =
        runTest(mainDispatcherRule.dispatcher) {
            val creditNotes = listOf(summary())
            val formas =
                listOf(
                    FormaPago(idFormaPago = 1, descripcion = "Efectivo", activo = 1, pos = 1, grupo = 1, orden = 1),
                    FormaPago(idFormaPago = 2, descripcion = "PUNTO DE VENTA", activo = 1, pos = 1, grupo = 1, orden = 2),
                )
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            this.creditNotes = creditNotes
                        },
                    formasPagoResult = Result.success(formas),
                )

            advanceUntilIdle()

            assertEquals(creditNotes, vm.state.value.creditNotes)
            assertEquals(
                listOf("Efectivo"),
                vm.state.value.availableRefundMethods
                    .map { it.descripcion },
            )
            assertNull(vm.state.value.error)
        }

    @Test
    fun `fallo al cargar notas expone el mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            creditNotesError = IllegalStateException()
                        },
                )

            advanceUntilIdle()

            assertEquals("No se pudieron cargar las notas de crédito", vm.state.value.error)
        }

    @Test
    fun `fallo al cargar metodos deja la lista vacia sin error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel(formasPagoResult = Result.failure(IllegalStateException()))

            advanceUntilIdle()

            assertTrue(
                vm.state.value.availableRefundMethods
                    .isEmpty(),
            )
            assertNull(vm.state.value.error)
        }

    @Test
    fun `openInvoicePicker cambia de modo y carga facturas origen`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoices = listOf(sourceSummary())
                        },
                )

            advanceUntilIdle()
            vm.openInvoicePicker()
            advanceUntilIdle()

            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)
            assertEquals(1, vm.state.value.sourceInvoices.size)
        }

    @Test
    fun `selectInvoice exitoso entra a CREATE con la factura y el formulario por defecto`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                        },
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(CreditNotesMode.CREATE, state.mode)
            assertEquals(sourceInvoice, state.selectedInvoice)
            assertEquals("", state.form.observacion)
            assertTrue(state.form.devolverStock)
            assertTrue(state.form.generarAbono)
        }

    @Test
    fun `selectInvoice fallido expone el mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetailError = IllegalStateException()
                        },
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()

            assertEquals("No se pudo cargar la factura", vm.state.value.error)
        }

    @Test
    fun `submit sin factura seleccionada expone error de seleccion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("Selecciona una factura para continuar", vm.state.value.error)
        }

    @Test
    fun `submit sin caja activa expone error de caja`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                        },
                    cajaConfig = CajaConfig(caja = null),
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("Debes tener una caja activa", vm.state.value.error)
        }

    @Test
    fun `submit con validacion de caja fallida expone el mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                        },
                    cajaConfig = CajaConfig(cajaStatusError = IllegalStateException()),
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("No se pudo validar la caja", vm.state.value.error)
        }

    @Test
    fun `submit sin secuencia abierta expone error de secuencia`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                        },
                    cajaConfig = CajaConfig(cajaSecuencia = null),
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("La caja activa no tiene secuencia abierta", vm.state.value.error)
        }

    @Test
    fun `submit exitoso crea devolucion total con abono y muestra el detalle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeCreditNoteRepository().apply {
                    sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                    createResponse =
                        CreateCreditNoteResponseDto(
                            success = true,
                            id = "nc-1",
                            codigo = "NC-1",
                            subtotal = 100.0,
                            impuesto = 0.0,
                            total = 100.0,
                            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
                            detail = createdDetail,
                        )
                }
            val vm = viewModel(repo = repo)

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            val payload = repo.createdPayloads.single()
            assertEquals("fact-1", payload.idFactura)
            assertEquals("seq-1", payload.idCajaSecuencia)
            assertTrue(payload.detalle.isEmpty())
            assertTrue(payload.anular)
            assertEquals(CreditNoteSettlementTypeDto.ABONO, payload.settlementType)

            val state = vm.state.value
            assertEquals(CreditNotesMode.LIST, state.mode)
            assertEquals("Nota de crédito NC-1 generada correctamente", state.successMessage)
            assertTrue(state.showCreditNoteDetail)
            assertEquals("nc-1", state.selectedCreditNote?.id)
            assertFalse(state.isSubmitting)
        }

    @Test
    fun `fallo al crear expone el mensaje fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                            createError = IllegalStateException()
                        },
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.submitCreditNote()
            advanceUntilIdle()

            assertEquals("No se pudo crear la nota de crédito", vm.state.value.error)
        }

    @Test
    fun `procesar fiscal confirma la nota seleccionada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fiscalPrinter = FiscalCreditNotePrinter()
            val confirmation =
                FakeConfirmationRepository(
                    confirmation =
                        ConfirmCreditNoteFiscalResponseDto(
                            success = true,
                            id = "nc-1",
                            codigo = "NC-1",
                            fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
                            codDevolucionFiscal = "NC-123",
                            numeroDocumentoFiscal = "NC-123",
                            printerSerial = "SER-1",
                        ),
                )
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            creditNoteDetail = createdDetail.copy(fiscalDocument = fiscalDocument())
                        },
                    fiscalPrinter = fiscalPrinter,
                    confirmationRepository = confirmation,
                )

            advanceUntilIdle()
            vm.openCreditNoteDetail("nc-1")
            advanceUntilIdle()
            vm.processSelectedCreditNoteFiscal()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(CreditNoteFiscalStatusDto.CONFIRMADA, state.selectedCreditNote?.fiscalStatus)
            assertEquals("Nota de crédito fiscal confirmada", state.successMessage)
            assertEquals("NC-123", state.selectedCreditNote?.fiscalNumber)
            assertEquals("SER-1", state.selectedCreditNote?.printerSerial)
        }

    @Test
    fun `backFromFlow desde CREATE vuelve al picker y resetea el formulario`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                viewModel(
                    repo =
                        FakeCreditNoteRepository().apply {
                            sourceInvoiceDetail = this@CreditNotesViewModelTest.sourceInvoice
                        },
                )

            advanceUntilIdle()
            vm.selectInvoice("fact-1")
            advanceUntilIdle()
            vm.formController.onObservacionChange("roto")
            vm.backFromFlow()

            assertEquals(CreditNotesMode.INVOICE_PICKER, vm.state.value.mode)
            assertNull(vm.state.value.selectedInvoice)
            assertEquals("", vm.state.value.form.observacion)
        }

    private fun viewModel(
        repo: FakeCreditNoteRepository = FakeCreditNoteRepository(),
        cajaConfig: CajaConfig = CajaConfig(),
        formasPagoResult: Result<List<FormaPago>> = Result.success(emptyList()),
        fiscalPrinter: PrinterRepository? = null,
        confirmationRepository: FakeConfirmationRepository = FakeConfirmationRepository(),
    ): CreditNotesViewModel {
        val processFiscal =
            ProcessCreditNoteFiscalUseCase(
                confirmationRepository = confirmationRepository,
                printerProvider = FakePrinterProvider(fiscalPrinter),
                contextReader = FakeCreditNoteContextReader,
            )
        return CreditNotesViewModel(
            creditNoteRepository = repo,
            cajaRepository = FakeCajaRepository(cajaConfig),
            formaPagoRepository = FakeFormaPagoRepository(formasPagoResult),
            processCreditNoteFiscal = processFiscal,
        )
    }

    private data class CajaConfig(
        val caja: Caja? = activeCaja,
        val cajaStatusError: IllegalStateException? = null,
        val cajaSecuencia: CajaSecuencia? = secuencia,
    )

    private fun summary() =
        CreditNoteSummaryDto(
            id = "nc-1",
            codigo = "NC-1",
            facturaId = "fact-1",
            facturaCodigo = "F-001",
            fecha = "2026-01-01",
            fechaCreacion = "2026-01-01",
            clienteNombre = "Cliente",
            clienteIdentificacion = "V-1",
            total = 100.0,
            subtotal = 100.0,
            impuesto = 0.0,
            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
        )

    private fun sourceSummary() =
        CreditNoteSourceInvoiceSummaryDto(
            id = "fact-1",
            codigo = "F-001",
            codigoFiscal = "",
            numeroDocumentoFiscal = "",
            fecha = "2026-01-01",
            clienteNombre = "Cliente",
            clienteIdentificacion = "V-1",
            total = 100.0,
            remainingAmount = 100.0,
            items = 1,
            moneda = "USD",
        )

    private fun fiscalDocument() =
        CreditNoteFiscalDocumentDto(
            creditNoteId = "nc-1",
            creditNoteCode = "NC-1",
            date = "2026-01-01",
            customerName = "Cliente",
            customerIdentifier = "V-1",
            customerAddress = "",
            customerPhone = "",
            originalInvoiceCode = "F-001",
            originalFiscalNumber = "",
            originalInvoiceDate = "2026-01-01",
            printerSerial = "SER-1",
            comment = "",
            lines = emptyList(),
        )

    private class FakeCreditNoteRepository : CreditNoteRepository {
        var creditNotes: List<CreditNoteSummaryDto> = emptyList()
        var creditNotesError: IllegalStateException? = null
        var sourceInvoices: List<CreditNoteSourceInvoiceSummaryDto> = emptyList()
        var sourceInvoiceDetail: CreditNoteSourceInvoiceDetailDto? = null
        var sourceInvoiceDetailError: IllegalStateException? = null
        var creditNoteDetail: CreditNoteDetailDto? = null
        var createResponse: CreateCreditNoteResponseDto? = null
        var createError: IllegalStateException? = null
        val createdPayloads = mutableListOf<CreateCreditNoteRequestDto>()

        override suspend fun getCreditNotes(search: String?): Result<CreditNotesListResponseDto> =
            creditNotesError?.let { Result.failure(it) }
                ?: Result.success(CreditNotesListResponseDto(data = creditNotes, total = creditNotes.size.toLong()))

        override suspend fun getCreditNoteDetail(id: String): Result<CreditNoteDetailDto> =
            creditNoteDetail?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no detail"))

        override suspend fun getSourceInvoices(search: String?): Result<CreditNoteSourceInvoiceListResponseDto> =
            Result.success(CreditNoteSourceInvoiceListResponseDto(data = sourceInvoices, total = sourceInvoices.size.toLong()))

        override suspend fun getSourceInvoiceDetail(id: String): Result<CreditNoteSourceInvoiceDetailDto> =
            sourceInvoiceDetailError?.let { Result.failure(it) }
                ?: sourceInvoiceDetail?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("no invoice"))

        override suspend fun createCreditNote(payload: CreateCreditNoteRequestDto): Result<CreateCreditNoteResponseDto> {
            createdPayloads += payload
            return createError?.let { Result.failure(it) } ?: Result.success(createResponse!!)
        }

        override suspend fun confirmFiscal(
            id: String,
            payload: com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto,
        ) = Result.failure<ConfirmCreditNoteFiscalResponseDto>(AssertionError("must not be called"))
    }

    private class FakeConfirmationRepository(
        private val confirmation: ConfirmCreditNoteFiscalResponseDto? = null,
    ) : CreditNoteFiscalConfirmationRepository {
        override suspend fun confirmFiscal(
            id: String,
            payload: com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto,
        ): Result<ConfirmCreditNoteFiscalResponseDto> =
            confirmation?.let { Result.success(it) }
                ?: Result.failure(AssertionError("must not be called"))
    }

    private class FakeCajaRepository(
        config: CajaConfig,
    ) : CajaRepository {
        private val cajaConfig = config
        val activeCajaState = MutableStateFlow(config.caja)

        override val activeCaja get() = activeCajaState
        override val activeCajaName = MutableStateFlow("Caja 1")
        override val activeCajaSecuencia = MutableStateFlow(config.cajaSecuencia)

        override suspend fun getCajas() = Result.success(emptyList<Caja>())

        override suspend fun getNextSecuenciaCodigo(idCaja: String) = Result.success("SEQ")

        override suspend fun restoreActiveCajaIfValid() = Unit

        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
            cajaConfig.cajaStatusError?.let { Result.failure(it) }
                ?: Result.success(CajaStatusResponse(cajaSecuencia = cajaConfig.cajaSecuencia))

        override suspend fun openCaja(request: AperturaRequest) = Result.success(CajaStatusResponse())

        override suspend fun closeCaja(request: CierreCajaRequest) = Result.success(CierreCajaResponse(success = true, message = "ok"))

        override suspend fun getCierreSummary() = Result.success(CierreCajaSummary())

        override suspend fun setActiveCaja(caja: Caja) {
            activeCajaState.value = caja
        }

        override suspend fun clearActiveCaja() {
            activeCajaState.value = null
        }

        override suspend fun markSequenceClosed() = Unit
    }

    private class FakeFormaPagoRepository(
        private val result: Result<List<FormaPago>>,
    ) : FormaPagoRepository {
        override suspend fun getFormasPago(cajaId: String?) = result
    }

    private class FakePrinterProvider(
        private val printer: PrinterRepository?,
    ) : PrinterProvider {
        override fun getActivePrinter(): PrinterRepository? = printer

        override fun getActiveTicketPrinter(): com.amaxonia.pos.domain.model.printer.TicketPrinter? = null
    }

    private object FakeCreditNoteContextReader : CreditNoteContextReader {
        override suspend fun currentCountryCode() = "VE"

        override suspend fun selectedPrinterType() = PrinterType.THE_FACTORY_HKA
    }

    private class FiscalCreditNotePrinter : PrinterRepository {
        override suspend fun printReceipt(transaction: com.amaxonia.pos.domain.model.Transaction) =
            Result.failure<com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult>(
                AssertionError("must not be called"),
            )

        override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto) =
            Result.success(
                com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult(
                    fiscalNumber = "NC-123",
                    printerSerial = "SER-1",
                ),
            )

        override suspend fun printReportX() = Result.success(Unit)

        override suspend fun printReportZ() = Result.success(Unit)
    }

    private companion object {
        val activeCaja =
            Caja(
                idCaja = "caja-1",
                codCaja = "C1",
                caja = null,
                descripcion = "Caja",
                estatus = 1,
                idSucursal = 1,
                serieCaja = "S1",
            )
        val secuencia =
            CajaSecuencia(
                idCajaSecuencia = "seq-1",
                idCaja = "caja-1",
                fechaApertura = "2026-01-01",
                montoApertura = 0.0,
                estatus = 1,
                usuarioApertura = "u",
                serieSucursal = "S",
                idSucursal = 1,
            )
    }
}
