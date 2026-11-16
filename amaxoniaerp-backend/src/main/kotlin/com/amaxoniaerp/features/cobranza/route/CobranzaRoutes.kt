package com.amaxoniaerp.features.cobranza.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.requireCompanyDbHeader
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.cobranza.data.CobranzaRepository
import com.amaxoniaerp.features.cobranza.domain.CobranzaPendientesResponse
import com.amaxoniaerp.features.cobranza.domain.CreateCobranzaRequest
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
import java.math.BigDecimal

private data class CobranzaScope(
    val database: Database,
    val countryCode: String,
    val username: String,
)

private suspend fun ApplicationCall.resolveCobranzaScope(): CobranzaScope? =
    run {
        val ctx = resolveCompanyRequestContext() ?: return@run null
        val companyDb = ctx.requireCompanyDbHeader(this) ?: return@run null
        val username =
            ctx.principal.payload
                .getClaim("username")
                .asString()
                .orEmpty()
                .ifBlank { "POS" }
        CobranzaScope(ctx.connectDatabase(companyDb), ctx.countryCode, username)
    }

fun Route.cobranzaRoutes(cobranzaRepository: CobranzaRepository) {
    val log = LoggerFactory.getLogger("CobranzaRoutes")
    authenticate {
        route("/api/pos/cobranzas") {
            get("/clientes/{idCliente}/pendientes") {
                val scope = call.resolveCobranzaScope() ?: return@get
                val idCliente = call.parameters["idCliente"].orEmpty()
                val facturas = cobranzaRepository.pendientes(scope.database, idCliente)
                val total = facturas.sumOf { BigDecimal.valueOf(it.saldo) }.toDouble()
                call.respond(CobranzaPendientesResponse(success = true, totalPendiente = total, data = facturas))
            }
            post {
                val scope = call.resolveCobranzaScope() ?: return@post
                val request = call.receive<CreateCobranzaRequest>()
                val result = cobranzaRepository.create(scope.database, scope.countryCode, request, scope.username)
                log.info(
                    "POS cobranza saved. country={} idCobranza={} recibo={} facturas={} pagos={} total={} duplicado={}",
                    scope.countryCode,
                    result.idCobranza,
                    result.nroRecibo,
                    request.facturas.size,
                    request.pagos.size,
                    result.montoTotal,
                    result.duplicado,
                )
                call.respond(if (result.duplicado) HttpStatusCode.OK else HttpStatusCode.Created, result)
            }
        }
    }
}
