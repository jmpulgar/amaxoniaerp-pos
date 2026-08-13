package com.amaxoniaerp

import com.amaxoniaerp.features.assets.route.assetsRoutes
import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.auth.route.authRoutes
import com.amaxoniaerp.features.caja.cajaRouting
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.clients.data.ClientsRepository
import com.amaxoniaerp.features.clients.data.ClientTypesRepository
import com.amaxoniaerp.features.clients.route.clientTypesRoutes
import com.amaxoniaerp.features.clients.route.clientsRoutes
import com.amaxoniaerp.features.companies.domain.CompanyService
import com.amaxoniaerp.features.creditnotes.application.PanamaCreditNoteProcessor
import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.route.creditNoteRoutes
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.route.facturasRoutes
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.route.geographyRoutes
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.route.itemsRoutes
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.cuentaMesaRouting
import com.amaxoniaerp.features.mesas.mesasRouting
import com.amaxoniaerp.features.mesas.pedidoMesaRouting
import com.amaxoniaerp.features.mesas.sesionMesaRouting
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.posRouting
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.promotions.route.promotionsRoutes
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.domain.VenezuelaInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaCreditNotePayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.route.electronicInvoiceRoutes
import com.amaxoniaerp.features.electronicinvoice.storage.FileSystemPanamaCreditNotePdfStorage
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.route.salesRoutes
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val routingLog = LoggerFactory.getLogger("Routing")

fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            routingLog.error(
                "Unhandled request error. method={} path={} message={}",
                call.request.httpMethod.value,
                call.request.uri,
                cause.message,
                cause
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Error interno del servidor"))
            )
        }
    }

    val jwtConfig = loadJwtConfig()
    val dotenv = loadDotEnv()
    val dataBasePath = loadConfigValue("DATA_BASE_PATH", "assets.dataBasePath", dotenv)

    // Servicios inicializados sin DB fija (se resuelve dinámicamente)
    val authService = AuthService(jwtConfig)
    val companyService = CompanyService(jwtConfig)

    // Repositorios
    val itemsRepository = ItemsRepository()
    val clientsRepository = ClientsRepository()
    val clientTypesRepository = ClientTypesRepository()
    val facturasRepository = FacturasRepository()
    val geographyRepository = GeographyRepository()
    val cajaRepository = CajaRepository()
    val formasPagoRepository = FormasPagoRepository()
    val promotionsRepository = PromotionsRepository()
    val mesasRepository = MesasRepository()
    // Facturación Electrónica Panamá - HTTP Client + PAC + Strategy
    val feHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = false
                explicitNulls = false
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = 30_000
        }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        feHttpClient.close()
    }

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
    val venezuelaProcessor = VenezuelaInvoiceStrategy(
        repository = veRepository,
        hkaClient = veHkaClient,
        payloadBuilder = vePayloadBuilder,
    )
    val feFactory = ElectronicInvoiceProcessorFactory(panamaProcessor, venezuelaProcessor)

    val creditNoteRepository = CreditNoteRepository()
    val creditNoteProcessor = PanamaCreditNoteProcessor(
        repository = feRepository,
        pacClient = pacClient,
        payloadBuilder = TheFactoryHkaCreditNotePayloadBuilder(payloadBuilder),
        pdfStorage = dataBasePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::FileSystemPanamaCreditNotePdfStorage),
    )
    val creditNoteService = CreditNoteService(creditNoteRepository, creditNoteProcessor)

    routing {
        get("/") {
            call.respondText("Amaxonia ERP API - Multi-Tenant Ready")
        }

        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }

        authRoutes(authService, companyService)
        itemsRoutes(itemsRepository)
        cajaRouting(cajaRepository)
        posRouting(formasPagoRepository)
        mesasRouting(mesasRepository)

        // PedidoMesaRepository se inicializa primero: SesionMesaRepository lo usa como
        // lookup de operaciones para decidir si la sesión se puede cerrar/cancelar.
        val pedidoMesaRepository = PedidoMesaRepository()
        val sesionMesaRepository = SesionMesaRepository(pedidoMesaRepository::tieneOperaciones)
        // CuentaMesaRepository depende de ambos: sesion (para transiciones ABIERTA ->
        // CUENTA_SOLICITADA -> CERRADA_PAGADA) y pedidos (para saldos facturables).
        val cuentaMesaRepository = CuentaMesaRepository(sesionMesaRepository, pedidoMesaRepository)
        val processSaleUseCase =
            ProcessSaleUseCase(ProcessSaleTransactionalRepository(cuentaMesaRepository), feFactory)
        sesionMesaRouting(mesasRepository, sesionMesaRepository)
        pedidoMesaRouting(pedidoMesaRepository)
        cuentaMesaRouting(cuentaMesaRepository, sesionMesaRepository, mesasRepository)

        promotionsRoutes(promotionsRepository)
        salesRoutes(processSaleUseCase)
        creditNoteRoutes(creditNoteService)
        electronicInvoiceRoutes(feFactory)

        val genericAssetsUrl = loadConfigValue("ASSETS_BASE_URL", "assets.baseUrl", dotenv)
        val veAssetsUrl = loadConfigValue("ASSETS_BASE_URL_VE", "assets.baseUrlVE", dotenv)
            ?: genericAssetsUrl
        val paAssetsUrl = loadConfigValue("ASSETS_BASE_URL_PA", "assets.baseUrlPA", dotenv)
            ?: genericAssetsUrl
        val assetsBaseUrls = mutableMapOf<String, String>()
        if (!veAssetsUrl.isNullOrBlank()) assetsBaseUrls["VE"] = veAssetsUrl.trimEnd('/')
        if (!paAssetsUrl.isNullOrBlank()) assetsBaseUrls["PA"] = paAssetsUrl.trimEnd('/')
        assetsRoutes(assetsBaseUrls = assetsBaseUrls, dataBasePath = dataBasePath)
        
        // Rutas auxiliares que aún podrían necesitar refactoring
        clientsRoutes(clientsRepository)
        clientTypesRoutes(clientTypesRepository)
        facturasRoutes(facturasRepository, panamaProcessor)
        geographyRoutes(geographyRepository)
    }
}
