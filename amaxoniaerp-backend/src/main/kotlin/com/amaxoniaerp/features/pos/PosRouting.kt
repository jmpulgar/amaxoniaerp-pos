package com.amaxoniaerp.features.pos

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.domain.FormaPagoResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.posRouting(formasPagoRepository: FormasPagoRepository) {
    authenticate {
        route("/api/pos") {
            get("/formas-pago") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Token inválido")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Falta country_code en token")
                    )

                val adminDb = principal.getAdminDb()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Falta admin_db en token")
                    )

                val cajaId = call.request.queryParameters["cajaId"]?.takeIf { it.isNotBlank() }
                val tipoRegistro = call.request.queryParameters["tipoRegistro"]
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?: listOf(1, 3)

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val formasPago = formasPagoRepository.listFormasPago(
                    database = companyDb,
                    cajaId = cajaId,
                    tipoRegistro = tipoRegistro
                )

                call.respond(
                    HttpStatusCode.OK,
                    FormaPagoResponse(success = true, data = formasPago)
                )
            }
        }
    }
}
