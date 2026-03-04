package com.amaxonia.pos.ui.common

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.repository.AuthRepositoryImpl
import com.amaxonia.pos.data.repository.CachedCompanyRepository
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.data.repository.LocalAddressCatalogRepository
import com.amaxonia.pos.data.repository.LocalClientTypeRepository
import com.amaxonia.pos.data.repository.FormaPagoRepositoryImpl
import com.amaxonia.pos.data.repository.ApiReportRepository
import com.amaxonia.pos.data.repository.MockReportRepository
import com.amaxonia.pos.data.repository.SalesRepositoryImpl
import com.amaxonia.pos.data.repository.MockTransactionRepository
import com.amaxonia.pos.data.repository.OfflineFirstClientRepository
import com.amaxonia.pos.data.repository.OfflineFirstProductRepository
import com.amaxonia.pos.data.sync.CatalogSyncer
import com.amaxonia.pos.domain.repository.AddressCatalogRepository
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.ClientTypeRepository
import com.amaxonia.pos.domain.repository.CompanyRepository
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import com.amaxonia.pos.domain.repository.TransactionRepository
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.data.repository.CajaRepositoryImpl
import com.amaxonia.pos.data.remote.api.FormaPagoApiImpl
import com.amaxonia.pos.data.remote.api.SalesApiImpl

object DependencyContainer {
    private var initialized = false

    lateinit var authRepository: AuthRepository
        private set
    lateinit var productRepository: ProductRepository
        private set
    lateinit var clientRepository: ClientRepository
        private set
    lateinit var addressCatalogRepository: AddressCatalogRepository
        private set
    lateinit var clientTypeRepository: ClientTypeRepository
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
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var apiConfigManager: ApiConfigManager
        private set
    val transactionRepository: TransactionRepository = MockTransactionRepository()
    lateinit var reportRepository: ReportRepository
        private set
    val cartRepository = CartRepository()
    lateinit var cajaRepository: CajaRepository
        private set
    lateinit var formaPagoRepository: FormaPagoRepository
        private set
    lateinit var salesRepository: SalesRepository
        private set

    fun initialize(context: Context) {
        if (initialized) return
        val apiConfigManager = ApiConfigManager()
        apiConfigManager.updateBaseUrl(ServerCountries.AVAILABLE[0])
        val apiClient = ApiClient(apiConfigManager)
        val apiService = ApiService(apiClient)
        val appContext = context.applicationContext
        val localStore = LocalStore(appContext)
        val networkMonitor = NetworkMonitor(appContext)
        val database = AppDatabase.getInstance(context.applicationContext)
        this.apiService = apiService
        this.apiClient = apiClient
        this.localStore = localStore
        this.networkMonitor = networkMonitor
        this.apiConfigManager = apiConfigManager
        authRepository = AuthRepositoryImpl(apiService, localStore)
        companyRepository = CachedCompanyRepository(localStore)
        productRepository = OfflineFirstProductRepository(apiService, localStore, database.productDao(), networkMonitor)
        reportRepository = ApiReportRepository(apiService, localStore, MockReportRepository())
        clientRepository = OfflineFirstClientRepository(apiService, localStore, database.clientDao(), networkMonitor)
        addressCatalogRepository = LocalAddressCatalogRepository(
            countryDao = database.countryDao(),
            addressLevel1Dao = database.addressLevel1Dao(),
            addressLevel2Dao = database.addressLevel2Dao(),
            addressLevel3Dao = database.addressLevel3Dao()
        )
        cajaRepository = CajaRepositoryImpl(com.amaxonia.pos.data.remote.api.CajaApiImpl(apiClient), localStore)
        formaPagoRepository = FormaPagoRepositoryImpl(FormaPagoApiImpl(apiClient), localStore)
        salesRepository = SalesRepositoryImpl(SalesApiImpl(apiClient), localStore)
        clientTypeRepository = LocalClientTypeRepository(database.clientTypeDao())
        catalogSyncer = CatalogSyncer(
            apiService = apiService,
            localStore = localStore,
            clientDao = database.clientDao(),
            productDao = database.productDao(),
            countryDao = database.countryDao(),
            addressLevel1Dao = database.addressLevel1Dao(),
            addressLevel2Dao = database.addressLevel2Dao(),
            addressLevel3Dao = database.addressLevel3Dao(),
            clientTypeDao = database.clientTypeDao()
        )
        initialized = true
    }
}
