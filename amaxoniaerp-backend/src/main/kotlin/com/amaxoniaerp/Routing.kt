package com.amaxoniaerp

import com.amaxoniaerp.composition.AppDependencies
import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory
import com.amaxoniaerp.features.assets.route.assetsRoutes
import com.amaxoniaerp.features.auth.route.authRoutes
import com.amaxoniaerp.features.caja.cajaRouting
import com.amaxoniaerp.features.clients.route.clientTypesRoutes
import com.amaxoniaerp.features.clients.route.clientsRoutes
import com.amaxoniaerp.features.creditnotes.route.creditNoteRoutes
import com.amaxoniaerp.features.electronicinvoice.route.electronicInvoiceRoutes
import com.amaxoniaerp.features.facturas.route.facturasRoutes
import com.amaxoniaerp.features.geography.route.geographyRoutes
import com.amaxoniaerp.features.items.route.itemsRoutes
import com.amaxoniaerp.features.mesas.cuentaMesaRouting
import com.amaxoniaerp.features.mesas.mesasRouting
import com.amaxoniaerp.features.mesas.pedidoMesaRouting
import com.amaxoniaerp.features.mesas.sesionMesaRouting
import com.amaxoniaerp.features.pos.posRouting
import com.amaxoniaerp.features.promotions.route.promotionsRoutes
import com.amaxoniaerp.features.sales.route.salesRoutes
import com.amaxoniaerp.features.sync.route.syncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
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
import org.slf4j.LoggerFactory

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

/**
 * Registra todas las rutas a partir del grafo de dependencias construido en
 * `com.amaxoniaerp.composition` (constructor DI manual).
 */
fun Application.configureRouting(deps: AppDependencies) {
    installStatusPages()

    routing {
        installCoreRoutes()
        authRoutes(deps.auth.authService, deps.auth.companyService)
        installPosRoutes(deps)
    }
}

private fun Route.installCoreRoutes() {
    get("/") {
        call.respondText("Amaxonia ERP API - Multi-Tenant Ready")
    }

    get("/health") {
        call.respond(mapOf("status" to "UP"))
    }
}

private fun Route.installPosRoutes(deps: AppDependencies) {
    itemsRoutes(deps.repositories.itemsRepository)
    syncRoutes(deps.repositories.syncRepository, deps.repositories.itemsRepository)
    cajaRouting(deps.caja.cajaRepository, deps.caja.cajaSession)
    posRouting(deps.repositories.formasPagoRepository)
    mesasRouting(deps.repositories.mesasRepository)

    sesionMesaRouting(deps.repositories.mesasRepository, deps.mesas.sesionMesaRepository)
    pedidoMesaRouting(deps.mesas.pedidoMesaRepository)
    cuentaMesaRouting(
        deps.mesas.cuentaMesaRepository,
        deps.mesas.sesionMesaRepository,
        deps.repositories.mesasRepository,
    )

    promotionsRoutes(deps.repositories.promotionsRepository)
    salesRoutes(deps.mesas.processSaleUseCase)
    creditNoteRoutes(deps.fiscal.creditNoteDependencies.creditNoteService)
    electronicInvoiceRoutes(deps.fiscal.feDependencies.feFactory)

    assetsRoutes(assetsBaseUrls = deps.routingConfig.assetsBaseUrls, dataBasePath = deps.routingConfig.dataBasePath)
    // Rutas auxiliares que aún podrían necesitar refactoring
    clientsRoutes(deps.repositories.clientsRepository)
    clientTypesRoutes(deps.repositories.clientTypesRepository)
    facturasRoutes(deps.repositories.facturasRepository, deps.fiscal.feDependencies.panamaProcessor)
    geographyRoutes(deps.repositories.geographyRepository)
}
