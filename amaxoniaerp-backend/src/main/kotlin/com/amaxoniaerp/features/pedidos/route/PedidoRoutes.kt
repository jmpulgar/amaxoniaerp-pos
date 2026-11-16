package com.amaxoniaerp.features.pedidos.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.requireCompanyDbHeader
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.pedidos.data.PedidoListQuery
import com.amaxoniaerp.features.pedidos.data.PedidoRepository
import com.amaxoniaerp.features.pedidos.domain.CreatePedidoRequest
import com.amaxoniaerp.features.pedidos.domain.PedidoDetailResponse
import com.amaxoniaerp.features.pedidos.domain.PedidoEstatus
import com.amaxoniaerp.features.pedidos.domain.PedidoListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private const val DEFAULT_LIMIT = 50

/** Contexto resuelto de una petición de pedidos POS. */
internal data class PosRequestScope(
    val database: Database,
    val countryCode: String,
    val username: String,
)

/**
 * Resuelve tenant (token de empresa + `Company-DB`) y usuario del JWT, igual que notas de crédito.
 */
internal suspend fun ApplicationCall.resolvePosScope(): PosRequestScope? =
    run {
        val ctx = resolveCompanyRequestContext() ?: return@run null
        val companyDb = ctx.requireCompanyDbHeader(this) ?: return@run null
        val username =
            ctx.principal.payload
                .getClaim("username")
                .asString()
                .orEmpty()
                .ifBlank { "POS" }
        PosRequestScope(ctx.connectDatabase(companyDb), ctx.countryCode, username)
    }

fun Route.pedidoRoutes(pedidoRepository: PedidoRepository) {
    val log = LoggerFactory.getLogger("PedidoRoutes")
    authenticate {
        route("/api/pos/pedidos") {
            get {
                val scope = call.resolvePosScope() ?: return@get
                val params = call.request.queryParameters
                val estatus =
                    params["estatus"]
                        ?.split(',')
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(PedidoEstatus.PENDIENTE)
                val query =
                    PedidoListQuery(
                        estatus = estatus,
                        search = params["search"],
                        idSucursal = params["id_sucursal"]?.toIntOrNull(),
                        limit = params["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT,
                    )
                call.respond(PedidoListResponse(success = true, data = pedidoRepository.list(scope.database, query)))
            }
            get("/{id}") {
                val scope = call.resolvePosScope() ?: return@get
                val id = call.parameters["id"].orEmpty()
                call.respond(PedidoDetailResponse(success = true, data = pedidoRepository.detail(scope.database, id)))
            }
            post {
                val scope = call.resolvePosScope() ?: return@post
                val request = call.receive<CreatePedidoRequest>()
                val result = pedidoRepository.create(scope.database, scope.countryCode, request, scope.username)
                log.info(
                    "POS pedido saved. country={} idPedido={} codPedido={} items={} duplicado={}",
                    scope.countryCode,
                    result.idPedido,
                    result.codPedido,
                    request.items.size,
                    result.duplicado,
                )
                call.respond(if (result.duplicado) HttpStatusCode.OK else HttpStatusCode.Created, result)
            }
        }
    }
}
