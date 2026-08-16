package com.amaxoniaerp.features.clients.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.clients.data.ClientTypesRepository
import com.amaxoniaerp.features.clients.domain.ClientTypesListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000

fun Route.clientTypesRoutes(clientTypesRepository: ClientTypesRepository) {
    authenticate {
        get("/client-types") {
            val principal =
                call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing token"),
                    )

            val tokenType = principal.payload.getClaim("token_type").asString()
            if (tokenType != "company") {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Company token required"),
                )
            }

            val adminDb = principal.getAdminDb()
            if (adminDb.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Company database not found in token"),
                )
            }

            val countryCode =
                principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found in token"),
                    )

            val limitParam = call.request.queryParameters["limit"]?.toIntOrNull()
            val offsetParam = call.request.queryParameters["offset"]?.toLongOrNull()
            val limit = limitParam ?: DEFAULT_PAGE_LIMIT
            val offset = offsetParam ?: 0L
            val includeTotalParam = call.request.queryParameters["includeTotal"]
            val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

            if (limit <= 0 || limit > MAX_PAGE_LIMIT || offset < 0) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid pagination parameters"),
                )
            }
            if (includeTotalParam != null && includeTotalParam.toBooleanStrictOrNull() == null) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid includeTotal parameter"),
                )
            }

            val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
            val (types, total) =
                clientTypesRepository.listClientTypes(
                    database = companyDb,
                    limit = limit,
                    offset = offset,
                    includeTotal = includeTotal,
                )
            call.respond(ClientTypesListResponse(data = types, total = total))
        }
    }
}
