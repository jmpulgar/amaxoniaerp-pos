package com.amaxonia.erp.composition

import android.annotation.SuppressLint
import android.content.Context
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.repository.AuthRepositoryImpl
import com.amaxonia.erp.domain.repository.AuthRepository
import com.amaxonia.erp.domain.usecase.AuthenticateUserUseCase
import com.amaxonia.erp.ui.login.LoginViewModel

@SuppressLint("StaticFieldLeak")
object DependencyContainer {
    private var appContext: Context? = null

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
        com.amaxonia.erp.data.repository.ClientRepositoryImpl(apiService, localStore)
    }

    val productRepository: com.amaxonia.erp.domain.repository.ProductRepository by lazy {
        com.amaxonia.erp.data.repository.ProductRepositoryImpl(apiService, localStore)
    }

    val sucursalRepository: com.amaxonia.erp.domain.repository.SucursalRepository by lazy {
        com.amaxonia.erp.data.repository.SucursalRepositoryImpl(apiService, localStore)
    }

    val cajaRepository: com.amaxonia.erp.domain.repository.CajaRepository by lazy {
        com.amaxonia.erp.data.repository.CajaRepositoryImpl(apiService, localStore)
    }

    val salesRepository: com.amaxonia.erp.domain.repository.SalesRepository by lazy {
        com.amaxonia.erp.data.repository.SalesRepositoryImpl(apiService, localStore)
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
        )

    fun createPosTerminalViewModel(): com.amaxonia.erp.ui.pos.PosTerminalViewModel =
        com.amaxonia.erp.ui.pos.PosTerminalViewModel(
            productRepository = productRepository,
            clientRepository = clientRepository,
            cajaRepository = cajaRepository,
            salesRepository = salesRepository,
            localStore = localStore,
            printGateway = defaultInvoicePrintGateway,
        )
}
