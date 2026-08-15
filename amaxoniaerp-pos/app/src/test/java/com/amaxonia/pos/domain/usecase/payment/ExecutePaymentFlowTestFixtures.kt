package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
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
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.model.tenant.SaleTenant
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
import java.time.Instant

internal data class Fixture(
    val useCase: ExecutePaymentFlowUseCase,
    val sales: FakeSalesRepository,
    val transactions: FakeTransactionRepository,
    val offlineWriter: CapturingOfflineWriter,
    val ledger: InMemoryTransactionLogDao? = null,
)

internal data class FixtureOptions(
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
    /** If set, processSale() throws DuplicateInvoiceException for this idFactura. */
    val processSaleDuplicateOn: String? = null,
    /** idFactura that findByCorrelationId resolves to a reconciled invoice. */
    val reconciledCorrelationId: String? = null,
    /** Whether the reconciled invoice atomically closed its table session. */
    val reconciledTableSessionClosed: Boolean = false,
    /** idFactura for which findByCorrelationId fails (network unreachable). */
    val reconciliationFailureId: String? = null,
)

internal fun fixture(options: FixtureOptions): Fixture {
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

internal fun configuredCart(options: FixtureOptions): CartRepository {
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

internal fun paymentRepositories(
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
internal fun buildPaymentFlow(
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
internal fun input(
    countryCode: String = "PA",
    gatewayPayment: Boolean = false,
    amount: Double = 10.0,
    paymentDetails: List<FormaPagoDetalle>? = null,
    methods: List<FormaPago>? = null,
    tenderedAmount: Money? = null,
    changeDue: Double = 0.0,
    correlationCarryOver: String? = null,
    printerType: PrinterType = PrinterType.NONE,
    paymentCondition: PaymentCondition = PaymentCondition.CONTADO,
    saleItemsOverride: List<SaleItemDto>? = null,
    financialSnapshotOverride: SaleFinancialSnapshot? = null,
): ExecutePaymentFlowInput {
    val method = paymentMethod(1, "CASH", "Efectivo")
    val details = paymentDetails ?: listOf(FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = amount))
    return ExecutePaymentFlowInput(
        countryCode = countryCode,
        paymentDetails =
            PaymentDetails(
                payload = FormapagoDetallePayload(amount, 0.0, 0.0, details),
                transactionMethods =
                    if (gatewayPayment) {
                        listOf(TransactionPaymentMethod(description = "Gateway", amount = amount))
                    } else {
                        emptyList()
                    },
            ),
        totalAmount = Money.fromDouble(amount),
        tenderedAmount = tenderedAmount ?: Money.fromDouble(amount),
        changeDue = changeDue,
        totalAmountBs = 0.0,
        changeDueBs = 0.0,
        exchangeRate = 0.0,
        secondaryCurrency = "",
        isMultiCurrency = false,
        availableMethods = methods ?: listOf(method),
        correlationCarryOver = correlationCarryOver,
        saleItemsOverride = saleItemsOverride,
        financialSnapshotOverride = financialSnapshotOverride,
        printerType = printerType,
        paymentCondition = paymentCondition,
    )
}

internal fun paymentMethod(
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

internal class CapturingOfflineWriter : OfflineInvoiceWriter {
    var written: OfflineInvoice? = null

    override suspend fun write(invoice: OfflineInvoice) {
        written = invoice
    }
}

internal class FakeTransactionRepository(
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

internal class FakeSalesRepository(
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

internal class FakePaymentGateway(
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

internal fun gatewayLaunch() =
    GatewayLaunchPayload(
        packageName = "gateway.package",
        activityClassName = "GatewayActivity",
        encryptedCommand = byteArrayOf(1),
        backgroundColor = "background",
        textColor = "text",
        message = "processing",
    )

internal class FakeCajaRepository(
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

internal object FakePaymentSession : PaymentSessionReader {
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
