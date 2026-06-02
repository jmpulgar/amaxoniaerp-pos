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
import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.route.creditNoteRoutes
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.route.facturasRoutes
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.route.geographyRoutes
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.route.itemsRoutes
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.posRouting
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.promotions.route.promotionsRoutes
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.route.electronicInvoiceRoutes
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
    // Facturación Electrónica Panamá - HTTP Client + PAC + Strategy
    val feHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = false
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
    val feRepository = ElectronicInvoiceRepository()
    val pacClient = TheFactoryHkaRestClient(feHttpClient)
    val payloadBuilder = TheFactoryHkaPayloadBuilder()
    val panamaProcessor = PanamaInvoiceProcessor(feRepository, pacClient, payloadBuilder)
    val feFactory = ElectronicInvoiceProcessorFactory(panamaProcessor)

    val processSaleUseCase = ProcessSaleUseCase(ProcessSaleTransactionalRepository(), feFactory)
    val creditNoteService = CreditNoteService(CreditNoteRepository())

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
        promotionsRoutes(promotionsRepository)
        salesRoutes(processSaleUseCase)
        creditNoteRoutes(creditNoteService)
        electronicInvoiceRoutes(feFactory)

        val dotenv = loadDotEnv()
        val genericAssetsUrl = loadConfigValue("ASSETS_BASE_URL", "assets.baseUrl", dotenv)
        val veAssetsUrl = loadConfigValue("ASSETS_BASE_URL_VE", "assets.baseUrlVE", dotenv)
            ?: genericAssetsUrl
        val paAssetsUrl = loadConfigValue("ASSETS_BASE_URL_PA", "assets.baseUrlPA", dotenv)
            ?: genericAssetsUrl
        val assetsBaseUrls = mutableMapOf<String, String>()
        if (!veAssetsUrl.isNullOrBlank()) assetsBaseUrls["VE"] = veAssetsUrl.trimEnd('/')
        if (!paAssetsUrl.isNullOrBlank()) assetsBaseUrls["PA"] = paAssetsUrl.trimEnd('/')
        val dataBasePath = loadConfigValue("DATA_BASE_PATH", "assets.dataBasePath", dotenv)
        assetsRoutes(assetsBaseUrls = assetsBaseUrls, dataBasePath = dataBasePath)
        
        // Rutas auxiliares que aún podrían necesitar refactoring
        clientsRoutes(clientsRepository)
        clientTypesRoutes(clientTypesRepository)
        facturasRoutes(facturasRepository)
        geographyRoutes(geographyRepository)
    }
}
