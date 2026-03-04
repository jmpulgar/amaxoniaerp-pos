package com.amaxoniaerp.features.facturas.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.domain.FacturasListResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Route.facturasRoutes(facturasRepository: FacturasRepository) {
    authenticate {
        route("/facturas") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid token")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required")
                    )
                }

                val adminDb = principal.payload.getClaim("admin_db").asString()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val search = call.request.queryParameters["search"]
                val fechaInicioParam = call.request.queryParameters["fecha_inicio"]
                val fechaFinParam = call.request.queryParameters["fecha_fin"]
                val estatusParam = call.request.queryParameters["estatus"]

                if (limit <= 0 || limit > 1000 || offset < 0) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid pagination parameters")
                    )
                }

                val fechaInicio = fechaInicioParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val fechaFin = fechaFinParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                if ((fechaInicioParam != null && fechaInicio == null) || (fechaFinParam != null && fechaFin == null)) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid date format, expected yyyy-MM-dd")
                    )
                }

                val estatusList = estatusParam?.split(",")?.mapNotNull { it.toIntOrNull() }

                val companyDb = DatabaseManager.connectToCompanyDb(adminDb)
                val (facturas, total) = facturasRepository.listFacturas(
                    database = companyDb,
                    limit = limit,
                    offset = offset,
                    search = search,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    estatusList = estatusList,
                )

                call.respond(FacturasListResponse(data = facturas, total = total))
            }
        }
    }
}
