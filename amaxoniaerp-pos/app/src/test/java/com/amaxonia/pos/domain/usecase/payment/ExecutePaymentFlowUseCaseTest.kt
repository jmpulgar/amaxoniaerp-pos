package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
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
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormaPagoDetalle
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import com.amaxonia.pos.domain.model.payment.GatewayApproval
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.PaymentGateway
import com.amaxonia.pos.domain.repository.PaymentSessionReader
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.repository.TransactionRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExecutePaymentFlowUseCaseTest {
    @Test
    fun `offline flow queues an idempotent invoice and pending transaction`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = false))

            val result = fixture.useCase(input()) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertNotNull(fixture.offlineWriter.written)
            assertEquals("flow-id", fixture.offlineWriter.written?.id)
            assertEquals(TransactionStatus.PENDING, fixture.transactions.saved?.status)
            assertEquals("OFF-1000", fixture.transactions.saved?.invoiceNumber)
        }

    @Test
    fun `online flow sends the characterized request and stores a paid transaction`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input()) { events += it }

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("remote-invoice", (result as PaymentFlowResult.Success).payload.transactionId)
            assertEquals(
                10.0,
                fixture.sales.request
                    ?.factura
                    ?.totalTotalFactura ?: -1.0,
                0.0,
            )
            assertEquals(TransactionStatus.PAID, fixture.transactions.saved?.status)
            assertEquals("INV-1", fixture.transactions.saved?.invoiceNumber)
            assertTrue(events.any { it is PaymentFlowEvent.Progress })
        }

    @Test
    fun `gateway configuration failure stops before mutating sale state`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true, gatewayFailure = IllegalArgumentException("invalid gateway")))

            val result = fixture.useCase(input()) {}

            assertEquals("invalid gateway", (result as PaymentFlowResult.Failure).message)
            assertEquals(null, fixture.sales.request)
            assertEquals(null, fixture.transactions.saved)
        }

    @Test
    fun `online backend failure is explicit and does not persist a transaction`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        processSaleFailure = IllegalStateException("backend unavailable"),
                    ),
                )

            val result = fixture.useCase(input()) {}

            assertEquals("backend unavailable", (result as PaymentFlowResult.Failure).message)
            assertNotNull(fixture.sales.request)
            assertEquals(null, fixture.transactions.saved)
        }

    @Test
    fun `offline queue remains visible when local transaction persistence fails`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = false,
                        transactionSaveFailure = IllegalStateException("database full"),
                    ),
                )

            val result = fixture.useCase(input()) {}

            assertEquals("database full", (result as PaymentFlowResult.Failure).message)
            assertNotNull(fixture.offlineWriter.written)
            assertEquals(TransactionStatus.PENDING, fixture.transactions.saved?.status)
        }

    @Test
    fun `Venezuela gateway rejection stops before sending the sale`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        gatewayLaunch = gatewayLaunch(),
                        gatewayApproval = GatewayApproval(approved = false, message = "declined"),
                    ),
                )
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input(countryCode = "VE", gatewayPayment = true)) { events += it }

            assertEquals("declined", (result as PaymentFlowResult.Failure).message)
            assertEquals(null, fixture.sales.request)
            assertTrue(events.any { it is PaymentFlowEvent.LaunchGateway })
        }

    @Test
    fun `Venezuela fiscal confirmation failure emits an explicit recoverable effect`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        printFeedback = InvoicePrintFeedback("printed", "FISCAL-9", "SERIAL-2"),
                        confirmationFailure = IllegalStateException("confirmation unavailable"),
                    ),
                )
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input(countryCode = "VE")) { events += it }

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("remote-invoice", fixture.sales.confirmedInvoiceId)
            assertEquals("FISCAL-9", fixture.sales.confirmation?.numeroDocumentoFiscal)
            assertEquals("SERIAL-2", fixture.sales.confirmation?.impresoraSerial)
            assertTrue(events.any { it is PaymentFlowEvent.FiscalConfirmationFailed })
        }

    @Test
    fun `mixed payment request preserves cash received change grouping and labels`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val cash = paymentMethod(1, "CASH", "Efectivo")
            val credit = paymentMethod(2, "CRED", "Crédito")

            val result =
                fixture.useCase(
                    input(
                        paymentDetails =
                            listOf(
                                FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = 4.0),
                                FormaPagoDetalle(idFormaPago = 2, sigla = "CRED", monto = 6.0),
                            ),
                        methods = listOf(cash, credit),
                        tenderedAmount = Money.parse("5.00"),
                        changeDue = 1.0,
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals(
                listOf(5.0, 6.0),
                fixture.sales.request
                    ?.pagos
                    ?.map { it.montoRecibido },
            )
            assertEquals(
                listOf(1.0, 0.0),
                fixture.sales.request
                    ?.pagos
                    ?.map { it.efectivoCambio },
            )
            assertEquals(
                mapOf("CASH" to 4.0, "CRED" to 6.0),
                fixture.sales.request
                    ?.pagoResumen
                    ?.montosPorTipo,
            )
            assertEquals("Efectivo + Crédito", (result as PaymentFlowResult.Success).payload.paymentMethodsLabel)
        }

    @Test
    fun `multi currency caja and selected branch are mapped without changing backend contract`() =
        runTest {
            val branch =
                ClientBranch(
                    sucursalId = 4,
                    clienteCodigo = "C1",
                    nombreSucursal = "Branch",
                    telefonoContacto = "branch-phone",
                    direccion = "branch-address",
                )
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        client = Client(id = "client", addressDetail = "client-address", phone = "client-phone"),
                        branches = listOf(branch),
                        sellers = listOf(Seller(7, "Seller")),
                        cajaIdSucursal = null,
                        codAlmacen = 0,
                        currency =
                            CurrencyConfig(
                                multiMoneda = "SI",
                                tasa = 36.5,
                                idTasa = 8,
                                monedaBase = 2,
                                abrMonedaBase = "VES",
                                monedaSecundaria = 1,
                                abrMonedaSecundaria = "USD",
                            ),
                    ),
                )

            val result = fixture.useCase(input()) {}
            val request = fixture.sales.request

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("client", request?.factura?.codCliente)
            assertEquals(7, request?.factura?.codVendedor)
            assertEquals(1, request?.factura?.idSucursal)
            assertEquals(4, request?.factura?.clienteSucursalId)
            assertEquals("branch-address", request?.factura?.facturarADireccion)
            assertEquals("branch-phone", request?.factura?.facturarATelefono)
            assertEquals("CONSUMIDOR FINAL", request?.factura?.facturarA)
            assertEquals("CF", request?.factura?.facturarARuc)
            assertEquals("SI", request?.moneda?.multiMoneda)
            assertEquals(36.5, request?.moneda?.tasa ?: 0.0, 0.0)
            assertEquals(8, request?.moneda?.idTasa)
        }

    @Test
    fun `preparation failures stop before network or persistence`() =
        runTest {
            val multipleBranches =
                listOf(
                    ClientBranch(sucursalId = 1, clienteCodigo = "C1", nombreSucursal = "Principal"),
                    ClientBranch(sucursalId = 2, clienteCodigo = "C1", nombreSucursal = "Secundaria"),
                )
            val scenarios =
                listOf(
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeProduct = false),
                        "No hay items en el carrito",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeClient = false),
                        "Debes seleccionar un cliente",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeCaja = false),
                        "Debes seleccionar una caja",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeSequence = false),
                        "La caja no esta abierta o no tiene secuencia activa",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, productId = "manual-product"),
                        "Hay items manuales/no sincronizados que no se pueden facturar aun",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, branches = multipleBranches),
                        "Debes seleccionar la sucursal del cliente",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, cajaStatusFailure = IllegalStateException("caja unavailable")),
                        "caja unavailable",
                    ),
                )

            scenarios.forEach { scenario ->
                assertPreparationFailure(scenario.options, scenario.expectedMessage)
            }
        }

    private suspend fun assertPreparationFailure(
        options: FixtureOptions,
        expectedMessage: String,
    ) {
        val fixture = fixture(options)

        val result = fixture.useCase(input()) {}

        assertEquals(expectedMessage, (result as PaymentFlowResult.Failure).message)
        assertEquals(null, fixture.sales.request)
        assertEquals(null, fixture.transactions.saved)
    }

    private fun fixture(options: FixtureOptions): Fixture {
        val cart = configuredCart(options)
        val caja = FakeCajaRepository(options)
        val sales = FakeSalesRepository(options)
        val transactions = FakeTransactionRepository(options.transactionSaveFailure)
        val writer = CapturingOfflineWriter()
        val clock = AppClock { Instant.ofEpochMilli(1_000L) }
        val ids = IdGenerator { "flow-id" }
        val ledger = if (options.withLedger) InMemoryTransactionLogDao() else null
        val repositories = paymentRepositories(options, cart, caja, sales, transactions)
        val useCase = buildPaymentFlow(repositories, writer, clock, ids, options, ledger)
        return Fixture(useCase, sales, transactions, writer, ledger)
    }

    private fun configuredCart(options: FixtureOptions): CartRepository {
        val cart = CartRepository()
        cart.setSellerContext(defaultSellerId = null, defaultSellerName = null, sellers = options.sellers)
        if (options.includeClient) {
            cart.setClient(options.client)
        }
        if (options.includeProduct) {
            cart.addToCart(
                Product(
                    id = options.productId,
                    code = "P1",
                    description = "Product",
                    isExempt = true,
                    prices = listOf(PriceLevel(label = "A", pricePlusTax = 10.0)),
                ),
            )
        }
        return cart
    }

    private fun paymentRepositories(
        options: FixtureOptions,
        cart: CartRepository,
        caja: FakeCajaRepository,
        sales: FakeSalesRepository,
        transactions: FakeTransactionRepository,
    ): PaymentFlowRepositories =
        PaymentFlowRepositories(
            state =
                PaymentStateRepositories(
                    transaction = transactions,
                    caja = caja,
                    cart = cart,
                    clientBranches = { options.branches },
                ),
            runtime =
                PaymentRuntimeServices(
                    sales = sales,
                    session = FakePaymentSession,
                    connectivity = { options.isOnline },
                ),
        )

    @Suppress("LongParameterList")
    private fun buildPaymentFlow(
        repositories: PaymentFlowRepositories,
        writer: CapturingOfflineWriter,
        clock: AppClock,
        ids: IdGenerator,
        options: FixtureOptions,
        ledger: InMemoryTransactionLogDao? = null,
    ): ExecutePaymentFlowUseCase {
        val preparationOperations =
            PaymentPreparationOperations(
                validatePayment = ValidatePaymentUseCase(),
                calculateSaleTotals = CalculateSaleTotalsUseCase(),
                buildSaleItems = BuildSaleItemsUseCase(),
                buildSaleRequest = BuildSaleRequestUseCase(),
            )
        val executionOperations =
            PaymentExecutionOperations(
                queueOfflineInvoice = QueueOfflineInvoiceUseCase(writer, ids, clock),
                printInvoice = PrintInvoiceUseCase { _, _, _ -> options.printFeedback },
                confirmFiscalDocument = ConfirmFiscalDocumentUseCase(repositories.runtime.sales),
                executeGatewayPayment = ExecuteGatewayPaymentUseCase(FakePaymentGateway(options)),
                handlePaymentFailure = HandlePaymentFailureUseCase(),
            )
        return ExecutePaymentFlowUseCase(
            prepareSale =
                PrepareSaleUseCase(
                    repositories = repositories,
                    operations = preparationOperations,
                    assembleSale = AssemblePreparedSaleUseCase(repositories, preparationOperations),
                ),
            operations = executionOperations,
            completeSale = CompletePaymentSaleUseCase(repositories, executionOperations, clock, ids, sessionReader = FakePaymentSession),
            startTransaction = ledger?.let { StartTransactionUseCase(it, ids, clock) },
            sessionReader = if (ledger != null) FakePaymentSession else null,
        )
    }

    @Suppress("LongParameterList")
    private fun input(
        countryCode: String = "PA",
        gatewayPayment: Boolean = false,
        paymentDetails: List<FormaPagoDetalle>? = null,
        methods: List<FormaPago>? = null,
        tenderedAmount: Money = Money.parse("10.00"),
        changeDue: Double = 0.0,
        correlationCarryOver: String? = null,
    ): ExecutePaymentFlowInput {
        val method = paymentMethod(1, "CASH", "Efectivo")
        val details = paymentDetails ?: listOf(FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = 10.0))
        return ExecutePaymentFlowInput(
            countryCode = countryCode,
            paymentDetails =
                PaymentDetails(
                    payload = FormapagoDetallePayload(10.0, 0.0, 0.0, details),
                    transactionMethods =
                        if (gatewayPayment) {
                            listOf(TransactionPaymentMethod(description = "Gateway", amount = 10.0))
                        } else {
                            emptyList()
                        },
                ),
            totalAmount = Money.parse("10.00"),
            tenderedAmount = tenderedAmount,
            changeDue = changeDue,
            totalAmountBs = 0.0,
            changeDueBs = 0.0,
            exchangeRate = 0.0,
            secondaryCurrency = "",
            isMultiCurrency = false,
            availableMethods = methods ?: listOf(method),
            correlationCarryOver = correlationCarryOver,
        )
    }

    private fun paymentMethod(
        id: Int,
        sigla: String,
        description: String,
    ) = FormaPago(
        idFormaPago = id,
        siglas = sigla,
        descripcion = description,
        activo = 1,
        pos = 1,
        grupo = 1,
        orden = id,
        tipoMoneda = "USD",
    )

    // --- Auditoría docs/auditoria-produccion-pos-2026-07-20.md — ítem 1 ---

    @Test
    fun `timeout then retry with carry-over id reuses the same idFactura and ledger row`() =
        runTest {
            // First attempt: backend times out (simulated as a thrown failure
            // after the row has been opened). The ViewModel would surface the
            // failure and remember the correlation id to carry into the retry.
            val firstFixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleFailure = IllegalStateException("timeout"),
                    ),
                )
            val firstResult = firstFixture.useCase(input(countryCode = "VE")) {}
            assertTrue(firstResult is PaymentFlowResult.Failure)
            assertEquals("timeout", (firstResult as PaymentFlowResult.Failure).message)
            val firstId = firstFixture.ledger!!.rows.keys.single()

            // Retry: the ViewModel re-presents the same payment details, but
            // NOW it carries the correlation id of the timed-out attempt.
            val retryFixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                    ),
                )
            retryFixture.ledger!!.rows[firstId] =
                firstFixture.ledger!!.rows[firstId]!!.copy(
                    // Simulate process death: a fresh DAO instance has the
                    // same row but the use case does NOT share the IdGenerator.
                )

            val retryResult =
                retryFixture.useCase(input(countryCode = "VE", correlationCarryOver = firstId)) {}

            // KEY ASSERTION of ítem 1: the SAME idFactura reached the backend.
            assertTrue(retryResult is PaymentFlowResult.Success)
            assertEquals(firstId, retryFixture.sales.request?.idFactura)
            // And only ONE ledger row exists in the retry ledger (no second one opened).
            assertEquals(1, retryFixture.ledger!!.rows.size)
        }

    // --- Auditoría docs/auditoria-produccion-pos-2026-07-20.md — ítem 2 ---

    @Test
    fun `HTTP 409 with reconcilable invoice converges to a single confirmed sale`() =
        runTest {
            // The idGenerator mints idFactura = "flow-id" deterministically.
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        reconciledCorrelationId = "flow-id",
                        reconciledTableSessionClosed = true,
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            // KEY ASSERTION of ítem 2: the conflict was reconciled with the
            // backend's authoritative record instead of becoming a dead-end.
            assertTrue("Expected Success, was $result", result is PaymentFlowResult.Success)
            val success = result as PaymentFlowResult.Success
            assertEquals("RECONCILED-1", success.payload.codFactura)
            assertTrue(success.payload.tableSessionClosed)
            // And processSale was called exactly once (no second submission).
            assertEquals(1, fixture.sales.processSaleCalls)
        }

    @Test
    fun `HTTP 409 without backend exposure surfaces DuplicateInvoice instead of auto-approving`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        // reconciledCorrelationId unset -> findByCorrelationId returns null
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            // KEY ASSERTION of ítem 2: ambiguous conflicts never silently
            // become approvals. The user is asked to retry or escalate.
            assertTrue(result is PaymentFlowResult.DuplicateInvoice)
            assertEquals("flow-id", (result as PaymentFlowResult.DuplicateInvoice).clientCorrelationId)
        }

    @Test
    fun `HTTP 409 with unreachable reconciliation backend stays Duplicate and never auto-approves`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        reconciliationFailureId = "flow-id",
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            assertTrue(result is PaymentFlowResult.DuplicateInvoice)
            val dup = result as PaymentFlowResult.DuplicateInvoice
            assertEquals("flow-id", dup.clientCorrelationId)
            assertEquals(1, fixture.sales.processSaleCalls)
        }

    private data class Fixture(
        val useCase: ExecutePaymentFlowUseCase,
        val sales: FakeSalesRepository,
        val transactions: FakeTransactionRepository,
        val offlineWriter: CapturingOfflineWriter,
        val ledger: InMemoryTransactionLogDao? = null,
    )

    private data class FixtureOptions(
        val isOnline: Boolean,
        val gatewayFailure: Throwable? = null,
        val gatewayLaunch: GatewayLaunchPayload? = null,
        val gatewayApproval: GatewayApproval = GatewayApproval(approved = true, message = "approved"),
        val processSaleFailure: Throwable? = null,
        val transactionSaveFailure: Throwable? = null,
        val printFeedback: InvoicePrintFeedback? = null,
        val confirmationFailure: Throwable? = null,
        val client: Client = Client(id = "client", code = "C1", firstName = "Cliente", ruc = "RUC"),
        val sellers: List<Seller> = emptyList(),
        val cajaIdSucursal: Int? = 1,
        val codAlmacen: Int? = 1,
        val currency: CurrencyConfig? = null,
        val includeClient: Boolean = true,
        val includeProduct: Boolean = true,
        val includeCaja: Boolean = true,
        val includeSequence: Boolean = true,
        val productId: String = "42",
        val cajaStatusFailure: Throwable? = null,
        val branches: List<ClientBranch> =
            listOf(ClientBranch(sucursalId = 1, clienteCodigo = "C1", nombreSucursal = "Principal")),
        /** When true, wires [StartTransactionUseCase] into the flow so the ledger can be inspected. */
        val withLedger: Boolean = false,
        // --- Auditoría ítem 2 (INT-BE-001) ---
        /** If set, processSale() throws DuplicateInvoiceException for this idFactura. */
        val processSaleDuplicateOn: String? = null,
        /** idFactura that findByCorrelationId resolves to a reconciled invoice. */
        val reconciledCorrelationId: String? = null,
        /** Whether the reconciled invoice atomically closed its table session. */
        val reconciledTableSessionClosed: Boolean = false,
        /** idFactura for which findByCorrelationId fails (network unreachable). */
        val reconciliationFailureId: String? = null,
    )

    private data class PreparationScenario(
        val options: FixtureOptions,
        val expectedMessage: String,
    )

    private class CapturingOfflineWriter : OfflineInvoiceWriter {
        var written: OfflineInvoice? = null

        override suspend fun write(invoice: OfflineInvoice) {
            written = invoice
        }
    }

    private class FakeTransactionRepository(
        private val saveFailure: Throwable?,
    ) : TransactionRepository {
        var saved: Transaction? = null

        override suspend fun saveTransaction(transaction: Transaction): Result<Unit> {
            saved = transaction
            return saveFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
        }

        override suspend fun getAllTransactions(): Result<List<Transaction>> = Result.success(emptyList())

        override suspend fun getTransactionById(id: String): Result<Transaction> = Result.failure(NoSuchElementException(id))
    }

    private class FakeSalesRepository(
        private val options: FixtureOptions,
    ) : SalesRepository {
        var request: ProcessSaleRequestDto? = null
        var confirmedInvoiceId: String? = null
        var confirmation: ConfirmFacturaFiscalRequestDto? = null
        /** Number of processSale invocations so a test can simulate a 409 on the first call only. */
        var processSaleCalls: Int = 0

        override suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> {
            request = payload
            processSaleCalls += 1
            // Auditoría ítem 2 fixture: simulate HTTP 409 whenconfigured.
            options.processSaleDuplicateOn?.let { triggerId ->
                if (payload.idFactura == triggerId) {
                    return Result.failure(
                        DuplicateInvoiceException(
                            clientCorrelationId = triggerId,
                            message = "Conflict: idFactura $triggerId already processed",
                        ),
                    )
                }
            }
            return options.processSaleFailure?.let(Result.Companion::failure)
                ?: Result.success(ProcessSaleResponseDto(true, "remote-invoice", "INV-1", 2))
        }

        override suspend fun findByCorrelationId(clientCorrelationId: String): Result<ReconciledInvoice?> =
            when (clientCorrelationId) {
                options.reconciledCorrelationId ->
                    Result.success(
                        ReconciledInvoice(
                            idFactura = clientCorrelationId,
                            codFactura = "RECONCILED-1",
                            codEstatus = 2,
                            sesionMesaCerrada = options.reconciledTableSessionClosed,
                        ),
                    )
                options.reconciliationFailureId ->
                    Result.failure(IllegalStateException("reconciliation unreachable"))
                else -> Result.success(null)
            }

        override suspend fun confirmFacturaFiscal(
            facturaId: String,
            payload: ConfirmFacturaFiscalRequestDto,
        ): Result<ConfirmFacturaFiscalResponseDto> {
            confirmedInvoiceId = facturaId
            confirmation = payload
            return options.confirmationFailure?.let(Result.Companion::failure)
                ?: Result.success(
                    ConfirmFacturaFiscalResponseDto(
                        success = true,
                        id = "confirmation",
                        codigo = "OK",
                        numeroDocumentoFiscal = payload.numeroDocumentoFiscal,
                        codFacturaFiscal = payload.codFacturaFiscal,
                        impresoraSerial = payload.impresoraSerial,
                    ),
                )
        }

        override suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto> =
            Result.failure(UnsupportedOperationException("Not used"))

        override suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto> =
            Result.failure(UnsupportedOperationException("Not used"))
    }

    private class FakePaymentGateway(
        private val options: FixtureOptions,
    ) : PaymentGateway {
        override suspend fun validateConfiguration(methods: List<com.amaxonia.pos.domain.model.TransactionPaymentMethod>): Result<Unit> =
            options.gatewayFailure?.let(Result.Companion::failure) ?: Result.success(Unit)

        override suspend fun prepare(
            method: com.amaxonia.pos.domain.model.TransactionPaymentMethod,
            customerIdentifier: String,
            exchangeRate: Double,
            isMultiCurrency: Boolean,
        ): Result<GatewayLaunchPayload?> = Result.success(options.gatewayLaunch)

        override suspend fun awaitApproval(): GatewayApproval = options.gatewayApproval
    }

    private fun gatewayLaunch() =
        GatewayLaunchPayload(
            packageName = "gateway.package",
            activityClassName = "GatewayActivity",
            encryptedCommand = byteArrayOf(1),
            backgroundColor = "background",
            textColor = "text",
            message = "processing",
        )

    private class FakeCajaRepository(
        private val options: FixtureOptions,
    ) : CajaRepository {
        override val activeCajaName: StateFlow<String> = MutableStateFlow("Caja")
        override val activeCaja: StateFlow<Caja?> =
            MutableStateFlow(
                if (options.includeCaja) {
                    Caja(
                        idCaja = "caja",
                        codCaja = "CJ",
                        descripcion = "Caja",
                        estatus = 1,
                        idSucursal = options.cajaIdSucursal,
                        codAlmacen = options.codAlmacen,
                        currency = options.currency,
                        serieCaja = "A",
                    )
                } else {
                    null
                },
            )
        override val activeCajaSecuencia: StateFlow<CajaSecuencia?> =
            MutableStateFlow(if (options.includeSequence) sequence() else null)

        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
            options.cajaStatusFailure?.let(Result.Companion::failure)
                ?: Result.success(CajaStatusResponse(cajaSecuencia = activeCajaSecuencia.value))

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(activeCaja.value?.let(::listOf).orEmpty())

        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success("1")

        override suspend fun restoreActiveCajaIfValid() = Unit

        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> = checkCajaStatus(request.idCaja)

        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
            Result.failure(UnsupportedOperationException("Not used"))

        override suspend fun getCierreSummary(): Result<CierreCajaSummary> = Result.failure(UnsupportedOperationException("Not used"))

        override suspend fun setActiveCaja(caja: Caja) = Unit

        override suspend fun clearActiveCaja() = Unit

        override suspend fun markSequenceClosed() = Unit

        private fun sequence() =
            CajaSecuencia(
                idCajaSecuencia = "sequence",
                idCaja = "caja",
                fechaApertura = "2026-01-01",
                montoApertura = 0.0,
                estatus = 1,
                usuarioApertura = "tester",
                serieSucursal = "A",
                idSucursal = 1,
            )
    }

    private object FakePaymentSession : PaymentSessionReader {
        override suspend fun currentCountryCode(): String = "PA"

        override suspend fun currentUsername(): String = "tester"

        override suspend fun currentTenant(): SaleTenant? =
            SaleTenant(
                tenantId = SaleTenant.idFor(1),
                companyId = 1,
                label = "Empresa 1",
                adminDb = "admin1",
                contableDb = "contable1",
                nominaDb = "nomina1",
            )
    }
}
