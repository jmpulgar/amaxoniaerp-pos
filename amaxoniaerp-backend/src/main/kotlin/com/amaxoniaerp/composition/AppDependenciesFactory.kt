package com.amaxoniaerp.composition

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.companies.domain.CompanyService
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.KioskYappyService
import com.amaxoniaerp.features.kiosk.application.PlaceKioskOrderService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.application.YappySessionManager
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskYappyConfigRepository
import com.amaxoniaerp.features.kiosk.data.yappy.YappyClient
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyGateway
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.loadConfigValue
import com.amaxoniaerp.loadDotEnv
import com.amaxoniaerp.loadJwtConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

/**
 * Construye el grafo completo de dependencias con constructor DI manual. El
 * `HttpClient` de FE se cierra al detener la aplicación.
 */
fun buildAppDependencies(application: Application): AppDependencies {
    val jwtConfig = application.loadJwtConfig()
    val dotenv = loadDotEnv()
    val dataBasePath = application.loadConfigValue("DATA_BASE_PATH", "assets.dataBasePath", dotenv)

    // Servicios inicializados sin DB fija (se resuelve dinámicamente)
    val auth = AuthDependencies(AuthService(jwtConfig), CompanyService(jwtConfig))

    val repositories = Repositories()
    val caja = buildCajaDependencies()
    val feHttpClient = buildFeHttpClient()
    application.environment.monitor.subscribe(ApplicationStopped) { feHttpClient.close() }
    val feDependencies = buildElectronicInvoiceDependencies(feHttpClient)
    val creditNoteDependencies = buildCreditNoteDependencies(feDependencies, dataBasePath)
    val mesas = buildMesasDependencies(feDependencies.feFactory)
    val yappyHttpClient = buildYappyHttpClient()
    application.environment.monitor.subscribe(ApplicationStopped) { yappyHttpClient.close() }
    // Respaldo del tipo de QR Yappy: el valor por empresa sale de parametros_generales.yappy_tipo_qr
    // (columna opcional); si no existe o está vacío se usa YAPPY_QR_TYPE y, por último, DYN.
    val yappyQrType = YappyQrType.fromConfig(application.loadConfigValue("YAPPY_QR_TYPE", "yappy.qrType", dotenv))
    val kiosk =
        buildKioskDependencies(
            jwtConfig = jwtConfig,
            cajaSession = caja.cajaSession,
            processSaleUseCase = mesas.processSaleUseCase,
            yappyGateway = YappyClient(yappyHttpClient),
            yappyQrType = yappyQrType,
        )
    val routingConfig =
        RoutingConfig(
            dataBasePath = dataBasePath,
            assetsBaseUrls = resolveAssetsBaseUrls(application, dotenv),
        )

    return AppDependencies(
        auth = auth,
        repositories = repositories,
        caja = caja,
        mesas = mesas,
        fiscal = FiscalDependencies(feDependencies, creditNoteDependencies),
        kiosk = kiosk,
        routingConfig = routingConfig,
    )
}

private fun buildCajaDependencies(): CajaDependencies =
    CajaDependencies(
        cajaRepository = CajaRepository(),
        cajaSession = CajaSessionWorkflow(ExposedCajaSessionStore()),
    )

private fun buildMesasDependencies(feFactory: ElectronicInvoiceProcessorFactory): MesasDependencies {
    // PedidoMesaRepository se inicializa primero: SesionMesaRepository lo usa como
    // lookup de operaciones para decidir si la sesión se puede cerrar/cancelar.
    val pedidoMesaRepository = PedidoMesaRepository()
    val sesionMesaRepository = SesionMesaRepository(pedidoMesaRepository::tieneOperaciones)
    // CuentaMesaRepository depende de ambos: sesion (para transiciones ABIERTA ->
    // CUENTA_SOLICITADA -> CERRADA_PAGADA) y pedidos (para saldos facturables).
    val cuentaMesaRepository = CuentaMesaRepository()
    val processSaleUseCase =
        ProcessSaleUseCase(ProcessSaleTransactionalRepository(cuentaMesaRepository), feFactory)
    return MesasDependencies(pedidoMesaRepository, sesionMesaRepository, cuentaMesaRepository, processSaleUseCase)
}

private fun buildKioskDependencies(
    jwtConfig: JwtConfig,
    cajaSession: CajaSessionWorkflow,
    processSaleUseCase: ProcessSaleUseCase,
    yappyGateway: YappyGateway,
    yappyQrType: YappyQrType,
): KioskDependencies {
    val kioskDeviceRepository = KioskDeviceRepository()
    val unlockRateLimiter = UnlockRateLimiter()
    val kioskOrderRepository = KioskOrderRepository()
    val kioskYappyService =
        KioskYappyService(
            kioskOrderRepository = kioskOrderRepository,
            yappyConfigRepository = KioskYappyConfigRepository(),
            yappyGateway = yappyGateway,
            sessionManager = YappySessionManager(yappyGateway),
            defaultQrType = yappyQrType,
        )
    val placeKioskOrderService =
        PlaceKioskOrderService(
            kioskOrderRepository = kioskOrderRepository,
            cajaSessionWorkflow = cajaSession,
            processSaleUseCase = processSaleUseCase,
            yappyPaymentVerifier = kioskYappyService,
        )
    val kioskService =
        KioskService(
            kioskDeviceRepository = kioskDeviceRepository,
            unlockRateLimiter = unlockRateLimiter,
            jwtConfig = jwtConfig,
            databaseResolver = { countryCode, companyDb ->
                DatabaseManager.connectToCompanyDb(countryCode, companyDb)
            },
            kioskOrderRepository = kioskOrderRepository,
            placeKioskOrderService = placeKioskOrderService,
            kioskYappyService = kioskYappyService,
        )
    return KioskDependencies(kioskService)
}

/**
 * Cliente HTTP dedicado a Yappy: timeouts de 15 s y sin plugin de logging para que api-key,
 * secret-key y el token de sesión nunca lleguen a los logs.
 */
private fun buildYappyHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = YAPPY_TIMEOUT_MS
            connectTimeoutMillis = YAPPY_TIMEOUT_MS
            socketTimeoutMillis = YAPPY_TIMEOUT_MS
        }
    }

private const val YAPPY_TIMEOUT_MS = 15_000L

private fun resolveAssetsBaseUrls(
    application: Application,
    dotenv: Map<String, String>,
): MutableMap<String, String> {
    val genericAssetsUrl = application.loadConfigValue("ASSETS_BASE_URL", "assets.baseUrl", dotenv)
    val veAssetsUrl =
        application.loadConfigValue("ASSETS_BASE_URL_VE", "assets.baseUrlVE", dotenv) ?: genericAssetsUrl
    val paAssetsUrl =
        application.loadConfigValue("ASSETS_BASE_URL_PA", "assets.baseUrlPA", dotenv) ?: genericAssetsUrl
    val assetsBaseUrls = mutableMapOf<String, String>()
    if (!veAssetsUrl.isNullOrBlank()) assetsBaseUrls["VE"] = veAssetsUrl.trimEnd('/')
    if (!paAssetsUrl.isNullOrBlank()) assetsBaseUrls["PA"] = paAssetsUrl.trimEnd('/')
    return assetsBaseUrls
}
