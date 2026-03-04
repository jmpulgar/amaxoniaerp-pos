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
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.route.facturasRoutes
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.route.geographyRoutes
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.route.itemsRoutes
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.posRouting
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.route.salesRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
            cause.printStackTrace()
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
    val processSaleUseCase = ProcessSaleUseCase(ProcessSaleTransactionalRepository())

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
        salesRoutes(processSaleUseCase)

        val assetsBaseUrl = environment.config.propertyOrNull("assets.baseUrl")?.getString()
            ?: System.getenv("ASSETS_BASE_URL")
        val dataBasePath = environment.config.propertyOrNull("assets.dataBasePath")?.getString()
            ?: System.getenv("DATA_BASE_PATH")
        assetsRoutes(assetsBaseUrl = assetsBaseUrl, dataBasePath = dataBasePath)
        
        // Rutas legacy que aún podrían necesitar refactoring
        clientsRoutes(clientsRepository)
        clientTypesRoutes(clientTypesRepository)
        facturasRoutes(facturasRepository)
        geographyRoutes(geographyRepository)
    }
}
