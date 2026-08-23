package com.amaxoniaerp.composition

import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.application.CloseCajaUseCase
import com.amaxoniaerp.features.caja.application.OpenCajaUseCase
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.companies.domain.CompanyService
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.loadConfigValue
import com.amaxoniaerp.loadDotEnv
import com.amaxoniaerp.loadJwtConfig
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
        routingConfig = routingConfig,
    )
}

private fun buildCajaDependencies(): CajaDependencies {
    val cajaRepository = CajaRepository()
    val closeCajaUseCase = CloseCajaUseCase(cajaRepository)
    val openCajaUseCase = OpenCajaUseCase(cajaRepository, closeCajaUseCase)
    val cajaSession = CajaSessionWorkflow(ExposedCajaSessionStore())
    return CajaDependencies(cajaRepository, openCajaUseCase, cajaSession)
}

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
