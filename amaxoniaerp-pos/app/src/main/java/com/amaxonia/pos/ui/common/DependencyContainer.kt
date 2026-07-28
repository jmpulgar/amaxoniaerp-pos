package com.amaxonia.pos.ui.common

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.ClientSucursalDao
import com.amaxonia.pos.data.local.db.DraftInvoiceDao
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.data.printer.DefaultInvoicePrintGateway
import com.amaxonia.pos.data.printer.HkaConnectionHelper
import com.amaxonia.pos.data.printer.HkaFiscalDeviceDiagnostics
import com.amaxonia.pos.data.printer.HkaPaymentGateway
import com.amaxonia.pos.data.printer.PrinterFactory
import com.amaxonia.pos.data.printer.TheFactoryRapidPayClient
import com.amaxonia.pos.data.printer.panama.PanamaCashCloseTicketFormatter
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiServerEnvironment
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.RemoteImageUrlResolver
import com.amaxonia.pos.data.remote.api.AreasApiImpl
import com.amaxonia.pos.data.remote.api.CreditNoteApiImpl
import com.amaxonia.pos.data.remote.api.CuentaMesaApiImpl
import com.amaxonia.pos.data.remote.api.FormaPagoApiImpl
import com.amaxonia.pos.data.remote.api.PedidosMesaApiImpl
import com.amaxonia.pos.data.remote.api.SalesApiImpl
import com.amaxonia.pos.data.remote.api.SesionMesaApiImpl
import com.amaxonia.pos.data.repository.ApiReportRepository
import com.amaxonia.pos.data.repository.ApiTransactionRepository
import com.amaxonia.pos.data.repository.AreaRepositoryImpl
import com.amaxonia.pos.data.repository.AuthRepositoryImpl
import com.amaxonia.pos.data.repository.CachedCompanyRepository
import com.amaxonia.pos.data.repository.CajaRepositoryImpl
import com.amaxonia.pos.data.repository.CreditNoteRepositoryImpl
import com.amaxonia.pos.data.repository.CuentaMesaRepositoryImpl
import com.amaxonia.pos.data.repository.FormaPagoRepositoryImpl
import com.amaxonia.pos.data.repository.InMemorySelectedTableHolder
import com.amaxonia.pos.data.repository.JsonDraftInvoiceRestorer
import com.amaxonia.pos.data.repository.LocalAddressCatalogRepository
import com.amaxonia.pos.data.repository.LocalClientTypeRepository
import com.amaxonia.pos.data.repository.LocalPosConfigurationRepository
import com.amaxonia.pos.data.repository.OfflineFirstClientFormCatalogSource
import com.amaxonia.pos.data.repository.OfflineFirstClientRepository
import com.amaxonia.pos.data.repository.OfflineFirstProductRepository
import com.amaxonia.pos.data.repository.PedidosMesaRepositoryImpl
import com.amaxonia.pos.data.repository.PromotionRepositoryImpl
import com.amaxonia.pos.data.repository.RemoteProductLotRepository
import com.amaxonia.pos.data.repository.RoomClientBranchRepository
import com.amaxonia.pos.data.repository.RoomDraftInvoiceRepository
import com.amaxonia.pos.data.repository.RoomOfflineInvoiceWriter
import com.amaxonia.pos.data.repository.RoomPendingSalesReader
import com.amaxonia.pos.data.repository.SalesRepositoryImpl
import com.amaxonia.pos.data.repository.SesionMesaRepositoryImpl
import com.amaxonia.pos.data.sync.CatalogSyncer
import com.amaxonia.pos.data.sync.SyncScheduler
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.printer.FiscalDeviceDiagnostics
import com.amaxonia.pos.domain.repository.AddressCatalogRepository
import com.amaxonia.pos.domain.repository.AreaRepository
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientBranchRepository
import com.amaxonia.pos.domain.repository.ClientFormCatalogSource
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.ClientTypeRepository
import com.amaxonia.pos.domain.repository.CompanyRepository
import com.amaxonia.pos.domain.repository.CreditNoteRepository
import com.amaxonia.pos.domain.repository.CuentaMesaRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.InMemoryTableAccountPaymentHolder
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import com.amaxonia.pos.domain.repository.PendingSalesReader
import com.amaxonia.pos.domain.repository.ProductLotRepository
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.PromotionRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.repository.SelectedTableHolder
import com.amaxonia.pos.domain.repository.ServerEnvironment
import com.amaxonia.pos.domain.repository.SesionMesaRepository
import com.amaxonia.pos.domain.repository.TableAccountPaymentHolder
import com.amaxonia.pos.domain.repository.TransactionRepository
import com.amaxonia.pos.domain.system.SystemAppClock
import com.amaxonia.pos.domain.system.UuidGenerator
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.CashCloseTicketPayloadBuilder
import com.amaxonia.pos.domain.usecase.cart.RefreshCartProductLotsUseCase
import com.amaxonia.pos.domain.usecase.cart.SaveDraftInvoiceUseCase
import com.amaxonia.pos.domain.usecase.drafts.RestoreDraftInvoiceUseCase
import com.amaxonia.pos.domain.usecase.payment.AssemblePreparedSaleUseCase
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsUseCase
import com.amaxonia.pos.domain.usecase.payment.BuildSaleRequestUseCase
import com.amaxonia.pos.domain.usecase.payment.CalculateSaleTotalsUseCase
import com.amaxonia.pos.domain.usecase.payment.CompletePaymentSaleUseCase
import com.amaxonia.pos.domain.usecase.payment.ConfirmFiscalDocumentUseCase
import com.amaxonia.pos.domain.usecase.payment.ExecuteGatewayPaymentUseCase
import com.amaxonia.pos.domain.usecase.payment.ExecutePaymentFlowUseCase
import com.amaxonia.pos.domain.usecase.payment.GatewayCallbackLedger
import com.amaxonia.pos.domain.usecase.payment.GatewayCallbackOutcome
import com.amaxonia.pos.domain.usecase.payment.HandlePaymentFailureUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentExecutionOperations
import com.amaxonia.pos.domain.usecase.payment.PaymentFiscalConfirmationLedger
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowRepositories
import com.amaxonia.pos.domain.usecase.payment.PaymentPreparationOperations
import com.amaxonia.pos.domain.usecase.payment.PaymentRuntimeServices
import com.amaxonia.pos.domain.usecase.payment.PaymentStateRepositories
import com.amaxonia.pos.domain.usecase.payment.PrepareSaleUseCase
import com.amaxonia.pos.domain.usecase.payment.PrintInvoiceUseCase
import com.amaxonia.pos.domain.usecase.payment.QueueFiscalConfirmationUseCase
import com.amaxonia.pos.domain.usecase.payment.QueueGatewayCallbackUseCase
import com.amaxonia.pos.domain.usecase.payment.QueueOfflineInvoiceUseCase
import com.amaxonia.pos.domain.usecase.payment.StartTransactionUseCase
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase

object DependencyContainer {
    private var initialized = false
    private lateinit var appContext: Context

    /**
     * Evento one-shot para pedir que el Dashboard abra el diálogo de apertura de
     * caja al recibir el foco (p. ej. tras cerrar caja y pulsar "Aperturar nueva
     * caja"). El Dashboard lo consume con [consumeAperturaRequest].
     */
    private val _pendingAperturaRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    val pendingAperturaRequest: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = _pendingAperturaRequest

    fun requestAperturaOnDashboard() {
        _pendingAperturaRequest.value = true
    }

    fun consumeAperturaRequest() {
        _pendingAperturaRequest.value = false
    }

    /**
     * Evento one-shot equivalente al de apertura, para que "Áreas y mesas" pueda pedir al
     * Dashboard que abra el selector de caja cuando todavía no hay una caja activa (sin caja no
     * hay sucursal y por tanto no hay áreas que mostrar).
     */
    private val _pendingCajaSelectorRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    val pendingCajaSelectorRequest: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = _pendingCajaSelectorRequest

    fun requestCajaSelectorOnDashboard() {
        _pendingCajaSelectorRequest.value = true
    }

    fun consumeCajaSelectorRequest() {
        _pendingCajaSelectorRequest.value = false
    }

    lateinit var authRepository: AuthRepository
        private set
    lateinit var productRepository: ProductRepository
        private set
    lateinit var productLotRepository: ProductLotRepository
        private set
    lateinit var promotionRepository: PromotionRepository
        private set
    lateinit var clientRepository: ClientRepository
        private set
    lateinit var addressCatalogRepository: AddressCatalogRepository
        private set
    lateinit var clientTypeRepository: ClientTypeRepository
        private set
    lateinit var clientFormCatalogSource: ClientFormCatalogSource
        private set
    lateinit var companyRepository: CompanyRepository
        private set
    lateinit var catalogSyncer: CatalogSyncer
        private set
    lateinit var apiService: ApiService
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var localStore: LocalStore
        private set
    val posConfigurationRepository: LocalPosConfigurationRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalPosConfigurationRepository(localStore)
    }
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var apiConfigManager: ApiConfigManager
        private set
    lateinit var serverEnvironment: ServerEnvironment
        private set
    lateinit var imageUrlResolver: ImageUrlResolver
        private set
    val transactionRepository: TransactionRepository get() = _apiTransactionRepository
    lateinit var apiTransactionRepository: ApiTransactionRepository
        private set
    val invoiceHistoryRepository: InvoiceHistoryRepository get() = apiTransactionRepository
    private lateinit var _apiTransactionRepository: ApiTransactionRepository
    lateinit var reportRepository: ReportRepository
        private set
    val appClock = SystemAppClock()
    val cartRepository = CartRepository()
    val restoreDraftInvoiceUseCase = RestoreDraftInvoiceUseCase(JsonDraftInvoiceRestorer(cartRepository))
    lateinit var cajaRepository: CajaRepository
        private set
    lateinit var formaPagoRepository: FormaPagoRepository
        private set
    lateinit var areaRepository: AreaRepository
        private set
    lateinit var sesionMesaRepository: SesionMesaRepository
        private set
    lateinit var pedidosMesaRepository: PedidosMesaRepository
        private set
    lateinit var cuentaMesaRepository: CuentaMesaRepository
        private set

    /** Selección de mesa en memoria; no persiste ni escribe en el backend. */
    val selectedTableHolder: SelectedTableHolder = InMemorySelectedTableHolder()

    /** Cuenta concreta que atraviesa el flujo estándar de pago/facturación. */
    val tableAccountPaymentHolder: TableAccountPaymentHolder =
        InMemoryTableAccountPaymentHolder()

    lateinit var salesRepository: SalesRepository
        private set
    lateinit var creditNoteRepository: CreditNoteRepository
        private set
    lateinit var draftInvoiceDao: DraftInvoiceDao
        private set
    lateinit var draftInvoiceRepository: DraftInvoiceRepository
        private set
    val saveDraftInvoiceUseCase: SaveDraftInvoiceUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SaveDraftInvoiceUseCase(draftInvoiceRepository, UuidGenerator, SystemAppClock())
    }
    val refreshCartProductLotsUseCase: RefreshCartProductLotsUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RefreshCartProductLotsUseCase(cartRepository, productLotRepository)
    }
    lateinit var clientSucursalDao: ClientSucursalDao
        private set
    lateinit var clientBranchRepository: ClientBranchRepository
        private set
    lateinit var pendingInvoiceDao: PendingInvoiceDao
        private set
    lateinit var pendingSalesReader: PendingSalesReader
        private set
    lateinit var transactionLogDao: TransactionLogDao
        private set
    lateinit var startTransactionUseCase: StartTransactionUseCase
        private set
    lateinit var queueFiscalConfirmationUseCase: QueueFiscalConfirmationUseCase
        private set
    lateinit var fiscalConfirmationLedger: PaymentFiscalConfirmationLedger
        private set
    lateinit var queueGatewayCallbackUseCase: QueueGatewayCallbackUseCase
        private set
    lateinit var gatewayCallbackLedger: GatewayCallbackLedger
        private set
    val cashCloseTicketFormatter: CashCloseTicketFormatter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PanamaCashCloseTicketFormatter()
    }
    val cashCloseTicketPayloadBuilder: CashCloseTicketPayloadBuilder by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CashCloseTicketPayloadBuilder(
            posConfigurationRepository,
            productRepository,
            pendingSalesReader,
            cashCloseTicketFormatter,
        )
    }
    val cashClosePrintingService: CashClosePrintingService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CashClosePrintingService(
            printerFactory,
            posConfigurationRepository,
            cashCloseTicketFormatter,
        )
    }
    lateinit var queueOfflineInvoiceUseCase: QueueOfflineInvoiceUseCase
        private set
    val printInvoiceUseCase: PrintInvoiceUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PrintInvoiceUseCase(DefaultInvoicePrintGateway(printerFactory, localStore, salesRepository))
    }
    val confirmFiscalDocumentUseCase: ConfirmFiscalDocumentUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConfirmFiscalDocumentUseCase(salesRepository)
    }
    val executeGatewayPaymentUseCase: ExecuteGatewayPaymentUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ExecuteGatewayPaymentUseCase(HkaPaymentGateway(theFactoryRapidPayClient, localStore))
    }
    val validatePaymentUseCase = ValidatePaymentUseCase()
    val buildPaymentDetailsUseCase = BuildPaymentDetailsUseCase()
    val calculateSaleTotalsUseCase = CalculateSaleTotalsUseCase()
    val buildSaleItemsUseCase = BuildSaleItemsUseCase()
    val buildSaleRequestUseCase = BuildSaleRequestUseCase()
    val handlePaymentFailureUseCase = HandlePaymentFailureUseCase()
    val executePaymentFlowUseCase: ExecutePaymentFlowUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val repositories =
            PaymentFlowRepositories(
                state =
                    PaymentStateRepositories(
                        transaction = transactionRepository,
                        caja = cajaRepository,
                        cart = cartRepository,
                        clientBranches = clientBranchRepository,
                    ),
                runtime =
                    PaymentRuntimeServices(
                        sales = salesRepository,
                        session = localStore,
                        connectivity = networkMonitor,
                    ),
            )
        val preparationOperations =
            PaymentPreparationOperations(
                validatePayment = validatePaymentUseCase,
                calculateSaleTotals = calculateSaleTotalsUseCase,
                buildSaleItems = buildSaleItemsUseCase,
                buildSaleRequest = buildSaleRequestUseCase,
            )
        val executionOperations =
            PaymentExecutionOperations(
                queueOfflineInvoice = queueOfflineInvoiceUseCase,
                printInvoice = printInvoiceUseCase,
                confirmFiscalDocument = confirmFiscalDocumentUseCase,
                executeGatewayPayment = executeGatewayPaymentUseCase,
                handlePaymentFailure = handlePaymentFailureUseCase,
            )
        ExecutePaymentFlowUseCase(
            prepareSale =
                PrepareSaleUseCase(
                    repositories = repositories,
                    operations = preparationOperations,
                    assembleSale = AssemblePreparedSaleUseCase(repositories, preparationOperations),
                ),
            operations = executionOperations,
            completeSale =
                CompletePaymentSaleUseCase(
                    repositories = repositories,
                    operations = executionOperations,
                    clock = SystemAppClock(),
                    idGenerator = UuidGenerator,
                    fiscalConfirmationLedger = fiscalConfirmationLedger,
                    sessionReader = localStore,
                ),
            startTransaction = startTransactionUseCase,
            gatewayCallbackLedger = gatewayCallbackLedger,
            sessionReader = localStore,
        )
    }

    val printerFactory: PrinterFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "DependencyContainer no inicializado" }
        PrinterFactory(appContext, localStore)
    }

    val theFactoryRapidPayClient: TheFactoryRapidPayClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "DependencyContainer no inicializado" }
        TheFactoryRapidPayClient(appContext, localStore)
    }

    val hkaConnectionHelper: HkaConnectionHelper by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "DependencyContainer no inicializado" }
        HkaConnectionHelper(appContext)
    }

    val fiscalDeviceDiagnostics: FiscalDeviceDiagnostics by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HkaFiscalDeviceDiagnostics(hkaConnectionHelper, theFactoryRapidPayClient)
    }

    fun initialize(context: Context) {
        if (initialized) return
        val apiConfigManager = ApiConfigManager()
        apiConfigManager.updateBaseUrl(ServerCountries.AVAILABLE[0])
        val apiClient = ApiClient(apiConfigManager)
        val apiService = ApiService(apiClient)
        val applicationContext = context.applicationContext
        val localStore = LocalStore(applicationContext)
        val networkMonitor = NetworkMonitor(applicationContext)
        val database = AppDatabase.getInstance(context.applicationContext)
        this.appContext = applicationContext
        this.apiService = apiService
        this.apiClient = apiClient
        this.localStore = localStore
        this.productLotRepository = RemoteProductLotRepository(apiService, localStore)
        this.networkMonitor = networkMonitor
        this.apiConfigManager = apiConfigManager
        this.serverEnvironment = ApiServerEnvironment(apiConfigManager, apiClient)
        this.imageUrlResolver = RemoteImageUrlResolver(apiConfigManager)
        authRepository = AuthRepositoryImpl(apiService, localStore)
        companyRepository = CachedCompanyRepository(localStore)
        productRepository = OfflineFirstProductRepository(apiService, localStore, database.productDao(), networkMonitor)
        promotionRepository =
            PromotionRepositoryImpl(apiService, localStore, database.promocionDao(), database.productDao(), networkMonitor)
        reportRepository = ApiReportRepository(apiService, localStore)
        clientRepository = OfflineFirstClientRepository(apiService, localStore, database.clientDao(), networkMonitor)
        addressCatalogRepository =
            LocalAddressCatalogRepository(
                countryDao = database.countryDao(),
                addressLevel1Dao = database.addressLevel1Dao(),
                addressLevel2Dao = database.addressLevel2Dao(),
                addressLevel3Dao = database.addressLevel3Dao(),
            )
        cajaRepository =
            CajaRepositoryImpl(
                com.amaxonia.pos.data.remote.api
                    .CajaApiImpl(apiClient),
                localStore,
            )
        formaPagoRepository = FormaPagoRepositoryImpl(FormaPagoApiImpl(apiClient), localStore, networkMonitor)
        areaRepository = AreaRepositoryImpl(AreasApiImpl(apiClient), localStore, localStore, networkMonitor)
        sesionMesaRepository = SesionMesaRepositoryImpl(SesionMesaApiImpl(apiClient), localStore)
        pedidosMesaRepository = PedidosMesaRepositoryImpl(PedidosMesaApiImpl(apiClient), localStore)
        cuentaMesaRepository = CuentaMesaRepositoryImpl(CuentaMesaApiImpl(apiClient), localStore)
        salesRepository = SalesRepositoryImpl(SalesApiImpl(apiClient), localStore)
        creditNoteRepository = CreditNoteRepositoryImpl(CreditNoteApiImpl(apiClient), localStore)
        _apiTransactionRepository = ApiTransactionRepository(SalesApiImpl(apiClient), localStore)
        apiTransactionRepository = _apiTransactionRepository
        clientTypeRepository = LocalClientTypeRepository(database.clientTypeDao())
        clientFormCatalogSource =
            OfflineFirstClientFormCatalogSource(
                apiService = apiService,
                localStore = localStore,
                networkMonitor = networkMonitor,
                localAddressCatalogs = addressCatalogRepository,
                localClientTypes = clientTypeRepository,
            )
        draftInvoiceDao = database.draftInvoiceDao()
        draftInvoiceRepository = RoomDraftInvoiceRepository(draftInvoiceDao)
        clientSucursalDao = database.clientSucursalDao()
        clientBranchRepository = RoomClientBranchRepository(clientSucursalDao)
        pendingInvoiceDao = database.pendingInvoiceDao()
        pendingSalesReader = RoomPendingSalesReader(pendingInvoiceDao)
        transactionLogDao = database.transactionLogDao()
        startTransactionUseCase =
            StartTransactionUseCase(
                dao = transactionLogDao,
                idGenerator = UuidGenerator,
                clock = SystemAppClock(),
            )
        queueFiscalConfirmationUseCase =
            QueueFiscalConfirmationUseCase(
                dao = transactionLogDao,
                clock = SystemAppClock(),
            )
        queueGatewayCallbackUseCase =
            QueueGatewayCallbackUseCase(
                dao = transactionLogDao,
                clock = SystemAppClock(),
            )
        fiscalConfirmationLedger =
            PaymentFiscalConfirmationLedger { outcome ->
                when (outcome) {
                    is com.amaxonia.pos.domain.usecase.payment.FiscalConfirmationOutcome.Confirmed -> {
                        transactionLogDao.markFiscalConfirmed(
                            id = outcome.correlationId,
                            status = QueueFiscalConfirmationUseCase.STATUS_CONFIRMED,
                            fiscalNumber = outcome.fiscalNumber,
                            printerSerial = outcome.printerSerial,
                        )
                    }
                    is com.amaxonia.pos.domain.usecase.payment.FiscalConfirmationOutcome.Retryable -> {
                        queueFiscalConfirmationUseCase.enqueue(
                            clientCorrelationId = outcome.correlationId,
                            remoteInvoiceId = outcome.remoteInvoiceId,
                            fiscalNumber = outcome.fiscalNumber,
                            printerSerial = outcome.printerSerial,
                            failureMessage = outcome.failureMessage,
                        )
                        SyncScheduler.enqueueFiscalConfirmations(appContext)
                    }
                }
            }
        gatewayCallbackLedger =
            GatewayCallbackLedger { outcome ->
                when (outcome) {
                    is GatewayCallbackOutcome.Awaiting -> {
                        queueGatewayCallbackUseCase.markAwaiting(outcome.correlationId)
                        SyncScheduler.enqueueGatewayCallbacks(appContext)
                    }
                    is GatewayCallbackOutcome.Resolved -> {
                        queueGatewayCallbackUseCase.markResolved(
                            clientCorrelationId = outcome.correlationId,
                            responseCode = outcome.responseCode,
                        )
                    }
                }
            }
        queueOfflineInvoiceUseCase =
            QueueOfflineInvoiceUseCase(
                writer = RoomOfflineInvoiceWriter(pendingInvoiceDao),
                idGenerator = UuidGenerator,
                clock = SystemAppClock(),
            )
        catalogSyncer =
            CatalogSyncer(
                apiService = apiService,
                localStore = localStore,
                clientDao = database.clientDao(),
                clientSucursalDao = database.clientSucursalDao(),
                productDao = database.productDao(),
                countryDao = database.countryDao(),
                addressLevel1Dao = database.addressLevel1Dao(),
                addressLevel2Dao = database.addressLevel2Dao(),
                addressLevel3Dao = database.addressLevel3Dao(),
                clientTypeDao = database.clientTypeDao(),
                promocionDao = database.promocionDao(),
            )
        initialized = true
    }
}
