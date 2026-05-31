package com.amaxoniaerp.features.geography.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.domain.AddressLevelsListResponse
import com.amaxoniaerp.features.geography.domain.CatalogListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.geographyRoutes(geographyRepository: GeographyRepository) {
    authenticate {
        get("/countries") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid or missing token")
                )

            val tokenType = principal.payload.getClaim("token_type").asString()
            if (tokenType != "company") {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Company token required")
                )
            }

            val adminDb = principal.getAdminDb()
            if (adminDb.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Company database not found in token")
                )
            }

            val countryCode = principal.getCountryCode()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Country code not found in token")
                )

            val limitParam = call.request.queryParameters["limit"]?.toIntOrNull()
            val offsetParam = call.request.queryParameters["offset"]?.toLongOrNull()
            val limit = limitParam ?: 100
            val offset = offsetParam ?: 0L
            val includeTotalParam = call.request.queryParameters["includeTotal"]
            val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

            if (limit <= 0 || limit > 1000 || offset < 0) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid pagination parameters")
                )
            }
            if (includeTotalParam != null && includeTotalParam.toBooleanStrictOrNull() == null) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid includeTotal parameter")
                )
            }

            val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
            val (countries, total) = geographyRepository.listCatalog(
                database = companyDb,
                tableName = "paises",
                limit = limit,
                offset = offset,
                includeTotal = includeTotal,
            )
            call.respond(CatalogListResponse(data = countries, total = total))
        }

        route("/address-levels") {
            get("/{level}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing token")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required")
                    )
                }

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Company database not found in token")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found in token")
                    )

                val level = call.parameters["level"]?.toIntOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid address level")
                    )

                val tableName = when (level) {
                    1 -> "direccion_nivel1"
                    2 -> "direccion_nivel2"
                    3 -> "direccion_nivel3"
                    else -> null
                } ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid address level")
                )

                val limitParam = call.request.queryParameters["limit"]?.toIntOrNull()
                val offsetParam = call.request.queryParameters["offset"]?.toLongOrNull()
                val limit = limitParam ?: 100
                val offset = offsetParam ?: 0L
                val includeTotalParam = call.request.queryParameters["includeTotal"]
                val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

                if (limit <= 0 || limit > 1000 || offset < 0) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid pagination parameters")
                    )
                }
                if (includeTotalParam != null && includeTotalParam.toBooleanStrictOrNull() == null) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid includeTotal parameter")
                    )
                }

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val (levels, total) = geographyRepository.listAddressLevels(
                    database = companyDb,
                    tableName = tableName,
                    limit = limit,
                    offset = offset,
                    includeTotal = includeTotal,
                )
                call.respond(AddressLevelsListResponse(data = levels, total = total))
            }
        }
    }
}
