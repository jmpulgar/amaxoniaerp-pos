package com.amaxoniaerp

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory
import com.amaxoniaerp.features.assets.route.assetsRoutes
import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.auth.route.authRoutes
import com.amaxoniaerp.features.caja.cajaRouting
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.clients.data.ClientTypesRepository
import com.amaxoniaerp.features.clients.data.ClientsRepository
import com.amaxoniaerp.features.clients.route.clientTypesRoutes
import com.amaxoniaerp.features.clients.route.clientsRoutes
import com.amaxoniaerp.features.companies.domain.CompanyService
import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.application.PanamaCreditNoteProcessor
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.route.creditNoteRoutes
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.domain.VenezuelaInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaCreditNotePayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.route.electronicInvoiceRoutes
import com.amaxoniaerp.features.electronicinvoice.storage.FileSystemPanamaCreditNotePdfStorage
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.route.facturasRoutes
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.route.geographyRoutes
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.route.itemsRoutes
import com.amaxoniaerp.features.mesas.cuentaMesaRouting
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.mesasRouting
import com.amaxoniaerp.features.mesas.pedidoMesaRouting
import com.amaxoniaerp.features.mesas.sesionMesaRouting
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.posRouting
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.promotions.route.promotionsRoutes
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.route.salesRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L
private const val SERVER_ERROR_THRESHOLD = 500

private val routingLog = LoggerFactory.getLogger("Routing")

/**
 * Mapeo canónico de categorías de error a códigos HTTP. Respuesta pública
 * estable; los detalles internos solo van al log.
 */
internal fun statusFor(category: ErrorCategory): HttpStatusCode =
    when (category) {
        ErrorCategory.Validation -> HttpStatusCode.BadRequest
        ErrorCategory.Unauthorized -> HttpStatusCode.Unauthorized
        ErrorCategory.Forbidden -> HttpStatusCode.Forbidden
        ErrorCategory.NotFound -> HttpStatusCode.NotFound
        ErrorCategory.Conflict -> HttpStatusCode.Conflict
        ErrorCategory.DomainRule -> HttpStatusCode.BadRequest
        ErrorCategory.ExternalService -> HttpStatusCode.BadGateway
        ErrorCategory.Unexpected -> HttpStatusCode.InternalServerError
    }

private fun Application.installStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            val status = statusFor(cause.category)
            if (status.value >= SERVER_ERROR_THRESHOLD) {
                routingLog.error(
                    "API error. method={} path={} category={} message={}",
                    call.request.httpMethod.value,
                    call.request.uri,
                    cause.category,
                    cause.message,
                    cause,
                )
            } else {
                routingLog.warn(
                    "API error. method={} path={} category={} message={}",
                    call.request.httpMethod.value,
                    call.request.uri,
                    cause.category,
                    cause.message,
                )
            }
            call.respond(status, mapOf("error" to cause.message))
        }
        exception<Throwable> { call, cause ->
            routingLog.error(
                "Unhandled request error. method={} path={} message={}",
                call.request.httpMethod.value,
                call.request.uri,
                cause.message,
                cause,
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error interno del servidor"),
            )
        }
    }
}

fun Application.configureRouting() {
    installStatusPages()

    val jwtConfig = loadJwtConfig()
    val dotenv = loadDotEnv()
    val dataBasePath = loadConfigValue("DATA_BASE_PATH", "assets.dataBasePath", dotenv)

    // Servicios inicializados sin DB fija (se resuelve dinámicamente)
    val authService = AuthService(jwtConfig)
    val companyService = CompanyService(jwtConfig)

    // Repositorios
    val repositories = Repositories()
    val feHttpClient = buildFeHttpClient()
    environment.monitor.subscribe(ApplicationStopped) {
        feHttpClient.close()
    }

    val feDependencies = buildElectronicInvoiceDependencies(feHttpClient)
    val creditNoteDependencies = buildCreditNoteDependencies(feDependencies, dataBasePath)
    val routingConfig =
        RoutingConfig(
            dataBasePath = dataBasePath,
            assetsBaseUrls = this.resolveAssetsBaseUrls(dotenv),
        )

    routing {
        installCoreRoutes()
        authRoutes(authService, companyService)
        installPosRoutes(
            repositories,
            feDependencies,
            creditNoteDependencies,
            routingConfig,
        )
    }
}

private class RoutingConfig(
    val dataBasePath: String?,
    val assetsBaseUrls: MutableMap<String, String>,
)

private class Repositories {
    val itemsRepository = ItemsRepository()
    val clientsRepository = ClientsRepository()
    val clientTypesRepository = ClientTypesRepository()
    val facturasRepository = FacturasRepository()
    val geographyRepository = GeographyRepository()
    val cajaRepository = CajaRepository()
    val formasPagoRepository = FormasPagoRepository()
    val promotionsRepository = PromotionsRepository()
    val mesasRepository = MesasRepository()
}

private fun Route.installCoreRoutes() {
    get("/") {
        call.respondText("Amaxonia ERP API - Multi-Tenant Ready")
    }

    get("/health") {
        call.respond(mapOf("status" to "UP"))
    }
}

private fun Route.installPosRoutes(
    repositories: Repositories,
    feDependencies: FeDependencies,
    creditNoteDependencies: CreditNoteDependencies,
    config: RoutingConfig,
) {
    itemsRoutes(repositories.itemsRepository)
    cajaRouting(repositories.cajaRepository)
    posRouting(repositories.formasPagoRepository)
    mesasRouting(repositories.mesasRepository)

    // PedidoMesaRepository se inicializa primero: SesionMesaRepository lo usa como
    // lookup de operaciones para decidir si la sesión se puede cerrar/cancelar.
    val pedidoMesaRepository = PedidoMesaRepository()
    val sesionMesaRepository = SesionMesaRepository(pedidoMesaRepository::tieneOperaciones)
    // CuentaMesaRepository depende de ambos: sesion (para transiciones ABIERTA ->
    // CUENTA_SOLICITADA -> CERRADA_PAGADA) y pedidos (para saldos facturables).
    val cuentaMesaRepository = CuentaMesaRepository()
    val processSaleUseCase =
        ProcessSaleUseCase(ProcessSaleTransactionalRepository(cuentaMesaRepository), feDependencies.feFactory)
    sesionMesaRouting(repositories.mesasRepository, sesionMesaRepository)
    pedidoMesaRouting(pedidoMesaRepository)
    cuentaMesaRouting(cuentaMesaRepository, sesionMesaRepository, repositories.mesasRepository)

    promotionsRoutes(repositories.promotionsRepository)
    salesRoutes(processSaleUseCase)
    creditNoteRoutes(creditNoteDependencies.creditNoteService)
    electronicInvoiceRoutes(feDependencies.feFactory)

    assetsRoutes(assetsBaseUrls = config.assetsBaseUrls, dataBasePath = config.dataBasePath)
    // Rutas auxiliares que aún podrían necesitar refactoring
    clientsRoutes(repositories.clientsRepository)
    clientTypesRoutes(repositories.clientTypesRepository)
    facturasRoutes(repositories.facturasRepository, feDependencies.panamaProcessor)
    geographyRoutes(repositories.geographyRepository)
}

private fun buildFeHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    encodeDefaults = false
                    explicitNulls = false
                    ignoreUnknownKeys = true
                    prettyPrint = false
                },
            )
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = HTTP_REQUEST_TIMEOUT_MS
        }
    }

private class FeDependencies(
    val feFactory: ElectronicInvoiceProcessorFactory,
    val panamaProcessor: PanamaInvoiceProcessor,
    val feRepository: ElectronicInvoiceRepository,
    val pacClient: TheFactoryHkaRestClient,
    val payloadBuilder: TheFactoryHkaPayloadBuilder,
)

private fun buildElectronicInvoiceDependencies(feHttpClient: HttpClient): FeDependencies {
    // Facturación Electrónica Panamá - HTTP Client + PAC + Strategy
    val feRepository = ElectronicInvoiceRepository()
    val pacClient = TheFactoryHkaRestClient(feHttpClient)
    val payloadBuilder = TheFactoryHkaPayloadBuilder()
    val panamaProcessor = PanamaInvoiceProcessor(feRepository, pacClient, payloadBuilder)

    // Facturación Electrónica Venezuela (The Factory HKA FE).
    // Activate cuando parametros_generales.tipo_facturacion == 5; usa el mismo
    // HttpClient (con TLS+timeouts+hostname verification ya configurados).
    val veRepository = VenezuelaElectronicInvoiceRepository()
    val veHkaClient = VenezuelaHkaRestClient(feHttpClient)
    val vePayloadBuilder = VenezuelaHkaPayloadBuilder()
    val venezuelaProcessor =
        VenezuelaInvoiceStrategy(
            repository = veRepository,
            hkaClient = veHkaClient,
            payloadBuilder = vePayloadBuilder,
        )
    val feFactory = ElectronicInvoiceProcessorFactory(panamaProcessor, venezuelaProcessor)
    return FeDependencies(feFactory, panamaProcessor, feRepository, pacClient, payloadBuilder)
}

private class CreditNoteDependencies(
    val creditNoteService: CreditNoteService,
)

private fun buildCreditNoteDependencies(
    fe: FeDependencies,
    dataBasePath: String?,
): CreditNoteDependencies {
    val creditNoteRepository = CreditNoteRepository()
    val creditNoteProcessor =
        PanamaCreditNoteProcessor(
            repository = fe.feRepository,
            pacClient = fe.pacClient,
            payloadBuilder = TheFactoryHkaCreditNotePayloadBuilder(fe.payloadBuilder),
            pdfStorage =
                dataBasePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::FileSystemPanamaCreditNotePdfStorage),
        )
    return CreditNoteDependencies(CreditNoteService(creditNoteRepository, creditNoteProcessor))
}

private fun Application.resolveAssetsBaseUrls(dotenv: Map<String, String>): MutableMap<String, String> {
    val genericAssetsUrl = loadConfigValue("ASSETS_BASE_URL", "assets.baseUrl", dotenv)
    val veAssetsUrl = loadConfigValue("ASSETS_BASE_URL_VE", "assets.baseUrlVE", dotenv) ?: genericAssetsUrl
    val paAssetsUrl = loadConfigValue("ASSETS_BASE_URL_PA", "assets.baseUrlPA", dotenv) ?: genericAssetsUrl
    val assetsBaseUrls = mutableMapOf<String, String>()
    if (!veAssetsUrl.isNullOrBlank()) assetsBaseUrls["VE"] = veAssetsUrl.trimEnd('/')
    if (!paAssetsUrl.isNullOrBlank()) assetsBaseUrls["PA"] = paAssetsUrl.trimEnd('/')
    return assetsBaseUrls
}
