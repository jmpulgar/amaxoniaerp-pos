package com.amaxonia.erp.composition

import android.annotation.SuppressLint
import android.content.Context
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.AppDatabase
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.SyncApi
import com.amaxonia.erp.data.repository.AuthRepositoryImpl
import com.amaxonia.erp.data.sync.OfflineSyncSettingsRepositoryImpl
import com.amaxonia.erp.data.sync.OfflineSyncSettingsStore
import com.amaxonia.erp.data.sync.SyncScheduler
import com.amaxonia.erp.domain.repository.AuthRepository
import com.amaxonia.erp.domain.repository.OfflineSyncSettingsRepository
import com.amaxonia.erp.domain.usecase.AuthenticateUserUseCase
import com.amaxonia.erp.ui.login.LoginViewModel
import com.amaxonia.erp.ui.offlinesettings.OfflineSettingsViewModel

@SuppressLint("StaticFieldLeak")
object DependencyContainer {
    private var appContext: Context? = null

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(checkNotNull(appContext) { "DependencyContainer not initialized" })
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(checkNotNull(appContext) { "DependencyContainer not initialized" })
    }

    val offlineSyncSettingsStore: OfflineSyncSettingsStore by lazy {
        OfflineSyncSettingsStore(checkNotNull(appContext) { "DependencyContainer not initialized" })
    }

    val syncApi: SyncApi by lazy {
        SyncApi(apiService)
    }

    val offlineSyncSettingsRepository: OfflineSyncSettingsRepository by lazy {
        OfflineSyncSettingsRepositoryImpl(
            database = database,
            syncApi = syncApi,
            localStore = localStore,
            productRepository = productRepository,
            scopeStore = offlineSyncSettingsStore,
            sucursalRepository = sucursalRepository,
            cajaRepository = cajaRepository,
            bootstrapEnqueuer = {
                appContext?.let { ctx ->
                    SyncScheduler.enqueueBootstrap(ctx)
                }
            },
        )
    }

    val localStore: LocalStore by lazy {
        LocalStore(checkNotNull(appContext) { "DependencyContainer not initialized" })
    }

    val apiClient: ApiClient by lazy {
        ApiClient()
    }

    val apiService: ApiService by lazy {
        ApiService(apiClient)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(apiService, localStore)
    }

    val authenticateUserUseCase: AuthenticateUserUseCase by lazy {
        AuthenticateUserUseCase(authRepository)
    }

    val clientRepository: com.amaxonia.erp.domain.repository.ClientRepository by lazy {
        com.amaxonia.erp.data.repository.ClientRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            clientDao = database.clientDao(),
            clientSucursalDao = database.clientSucursalDao(),
            networkMonitor = networkMonitor,
            scopeStore = offlineSyncSettingsStore,
        )
    }

    val productRepository: com.amaxonia.erp.domain.repository.ProductRepository by lazy {
        com.amaxonia.erp.data.repository.ProductRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            productDao = database.productDao(),
            departmentDao = database.departmentDao(),
            networkMonitor = networkMonitor,
            scopeStore = offlineSyncSettingsStore,
        )
    }

    val sucursalRepository: com.amaxonia.erp.domain.repository.SucursalRepository by lazy {
        com.amaxonia.erp.data.repository.SucursalRepositoryImpl(apiService, localStore)
    }

    val cajaRepository: com.amaxonia.erp.domain.repository.CajaRepository by lazy {
        com.amaxonia.erp.data.repository.CajaRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            cajaSesionDao = database.cajaSesionDao(),
            networkMonitor = networkMonitor,
        )
    }

    val salesRepository: com.amaxonia.erp.domain.repository.SalesRepository by lazy {
        com.amaxonia.erp.data.repository.SalesRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            paymentMethodDao = database.paymentMethodDao(),
            networkMonitor = networkMonitor,
        )
    }

    val invoiceHistoryRepository: com.amaxonia.erp.domain.repository.InvoiceHistoryRepository by lazy {
        com.amaxonia.erp.data.repository.InvoiceHistoryRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            pendingInvoiceDao = database.pendingInvoiceDao(),
            networkMonitor = networkMonitor,
        )
    }

    val promotionRepository: com.amaxonia.erp.domain.repository.PromotionRepository by lazy {
        com.amaxonia.erp.data.repository.PromotionRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            productRepository = productRepository,
            promocionDao = database.promocionDao(),
            productDao = database.productDao(),
            networkMonitor = networkMonitor,
        )
    }

    val printerProvider: com.amaxonia.erp.domain.repository.PrinterProvider by lazy {
        com.amaxonia.erp.data.printer.PrinterFactory(
            context = checkNotNull(appContext) { "DependencyContainer not initialized" },
            localStore = localStore,
        )
    }

    val defaultInvoicePrintGateway: com.amaxonia.erp.data.printer.DefaultInvoicePrintGateway by lazy {
        com.amaxonia.erp.data.printer.DefaultInvoicePrintGateway(
            printerProvider = printerProvider,
            localStore = localStore,
            salesRepository = salesRepository,
        )
    }

    val fiscalDiagnostics: com.amaxonia.erp.domain.model.printer.FiscalDeviceDiagnostics by lazy {
        com.amaxonia.erp.data.printer.HkaConnectionHelper(
            context = checkNotNull(appContext) { "DependencyContainer not initialized" },
        )
    }

    val customerDisplayManager: com.amaxonia.erp.ui.customerdisplay.CustomerDisplayManager by lazy {
        com.amaxonia.erp.ui.customerdisplay.CustomerDisplayManager(
            appContext = checkNotNull(appContext) { "DependencyContainer not initialized" },
            localStore = localStore,
        )
    }

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun createLoginViewModel(): LoginViewModel =
        LoginViewModel(
            authenticateUser = authenticateUserUseCase,
            countryStore = localStore,
            authRepository = authRepository,
        )

    fun createCompanySelectionViewModel(session: com.amaxonia.erp.domain.model.AuthSession): com.amaxonia.erp.ui.company.CompanySelectionViewModel =
        com.amaxonia.erp.ui.company.CompanySelectionViewModel(
            authRepository = authRepository,
            session = session,
        )

    fun createClientListViewModel(): com.amaxonia.erp.ui.clients.ClientListViewModel =
        com.amaxonia.erp.ui.clients.ClientListViewModel(clientRepository)

    fun createProductListViewModel(): com.amaxonia.erp.ui.products.ProductListViewModel =
        com.amaxonia.erp.ui.products.ProductListViewModel(productRepository)

    fun createSucursalesViewModel(): com.amaxonia.erp.ui.sucursales.SucursalesViewModel =
        com.amaxonia.erp.ui.sucursales.SucursalesViewModel(sucursalRepository)

    fun createCajasOverviewViewModel(): com.amaxonia.erp.ui.caja.CajasOverviewViewModel =
        com.amaxonia.erp.ui.caja.CajasOverviewViewModel(cajaRepository, sucursalRepository)

    fun createSettingsViewModel(): com.amaxonia.erp.ui.settings.SettingsViewModel =
        com.amaxonia.erp.ui.settings.SettingsViewModel(
            localStore = localStore,
            fiscalDiagnostics = fiscalDiagnostics,
            printGateway = defaultInvoicePrintGateway,
            customerDisplayManager = customerDisplayManager,
        )

    fun createOfflineSettingsViewModel(): OfflineSettingsViewModel =
        OfflineSettingsViewModel(offlineSyncSettingsRepository)

    fun createPosTerminalViewModel(): com.amaxonia.erp.ui.pos.PosTerminalViewModel =
        com.amaxonia.erp.ui.pos.PosTerminalViewModel(
            productRepository = productRepository,
            clientRepository = clientRepository,
            cajaRepository = cajaRepository,
            salesRepository = salesRepository,
            localStore = localStore,
            printGateway = defaultInvoicePrintGateway,
            customerDisplayManager = customerDisplayManager,
            promotionRepository = promotionRepository,
            pendingInvoiceDao = database.pendingInvoiceDao(),
            networkMonitor = networkMonitor,
            onOfflineInvoiceQueued = {
                appContext?.let { ctx ->
                    SyncScheduler.enqueuePendingInvoices(ctx)
                }
            },
        )

    fun createHistoryViewModel(companySession: com.amaxonia.erp.domain.model.CompanySession? = null): com.amaxonia.erp.ui.history.HistoryViewModel =
        com.amaxonia.erp.ui.history.HistoryViewModel(
            transactionRepository = invoiceHistoryRepository,
            cajaRepository = cajaRepository,
            printGateway = defaultInvoicePrintGateway,
            countryCodeProvider = {
                companySession?.company?.countryCode?.takeIf { it.isNotBlank() } ?: "PA"
            },
            networkMonitor = networkMonitor,
        )
}
