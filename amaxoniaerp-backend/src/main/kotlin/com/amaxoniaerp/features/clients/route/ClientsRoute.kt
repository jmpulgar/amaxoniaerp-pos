package com.amaxoniaerp.features.clients.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.clients.data.ClientsRepository
import com.amaxoniaerp.features.clients.domain.ClientsListResponse
import com.amaxoniaerp.features.clients.domain.CreateClientRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.clientsRoutes(clientsRepository: ClientsRepository) {
    authenticate {
        route("/clients") {
            get {
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

                val adminDb = principal.payload.getClaim("admin_db").asString()
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
                            mapOf("error" to "Falta country_code en token"),
                        )

                val limitParam = call.request.queryParameters["limit"]?.toIntOrNull()
                val offsetParam = call.request.queryParameters["offset"]?.toLongOrNull()
                val limit = limitParam ?: 100
                val offset = offsetParam ?: 0L
                val search = call.request.queryParameters["search"]
                val includeTotalParam = call.request.queryParameters["includeTotal"]
                val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

                if (limit <= 0 || limit > 1000 || offset < 0) {
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
                val (clients, total) = clientsRepository.listClients(companyDb, limit, offset, search, includeTotal)
                call.respond(ClientsListResponse(data = clients, total = total))
            }

            get("/default") {
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

                val adminDb = principal.payload.getClaim("admin_db").asString()
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
                            mapOf("error" to "Falta country_code en token"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val defaultClient =
                    clientsRepository.getDefaultClient(companyDb, countryCode)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Default client not configured"),
                        )

                call.respond(defaultClient)
            }

            get("/{id}") {
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

                val adminDb = principal.payload.getClaim("admin_db").asString()
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
                            mapOf("error" to "Falta country_code en token"),
                        )

                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid client id"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val client =
                    clientsRepository.getClientById(companyDb, id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Client not found"),
                        )

                call.respond(client)
            }

            get("/{id}/sucursales") {
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

                val adminDb = principal.payload.getClaim("admin_db").asString()
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
                            mapOf("error" to "Falta country_code en token"),
                        )

                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid client id"),
                        )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val sucursales = clientsRepository.listClientSucursales(companyDb, countryCode, id)
                call.respond(sucursales)
            }

            post {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Invalid or missing token"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required"),
                    )
                }

                val adminDb = principal.payload.getClaim("admin_db").asString()
                if (adminDb.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Company database not found in token"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val request = call.receive<CreateClientRequest>()
                if (request.identification.isBlank() || request.name.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "RUC and Name are required"),
                    )
                }

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val client = clientsRepository.createClient(companyDb, countryCode, request)
                call.respond(HttpStatusCode.Created, client)
            }

            put("/{id}") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@put call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Invalid or missing token"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required"),
                    )
                }

                val adminDb = principal.payload.getClaim("admin_db").asString()
                if (adminDb.isNullOrBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Company database not found in token"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val id =
                    call.parameters["id"]
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid client id"),
                        )

                val request = call.receive<CreateClientRequest>()
                if (request.identification.isBlank() || request.name.isBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "RUC and Name are required"),
                    )
                }

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val client =
                    clientsRepository.updateClient(companyDb, id, request)
                        ?: return@put call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Client not found"),
                        )

                call.respond(client)
            }
        }
    }
}
