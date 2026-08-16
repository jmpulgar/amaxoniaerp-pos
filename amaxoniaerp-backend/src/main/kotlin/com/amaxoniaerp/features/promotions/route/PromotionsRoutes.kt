package com.amaxoniaerp.features.promotions.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.promotions.domain.PromotionsListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val ERR_MISSING_COUNTRY = "Falta country_code en token"
private const val ERR_MISSING_ADMIN_DB = "Falta admin_db en token"

fun Route.promotionsRoutes(repository: PromotionsRepository) {
    authenticate {
        route("/promociones") {
            get {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
                }
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_MISSING_COUNTRY))
                val adminDb =
                    principal.getAdminDb()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_MISSING_ADMIN_DB))
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                call.respond(PromotionsListResponse(repository.listPromotions(companyDb)))
            }
        }
    }
}
