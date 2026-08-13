package com.amaxoniaerp.features.creditnotes.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate

fun Route.creditNoteRoutes(creditNoteService: CreditNoteService) {
    authenticate {
        route("/api/pos/notas-credito") {
            get {
                val resolved = resolveCompanyDatabase(call) ?: return@get
                val (database, principal) = resolved
                val countryCode = principal.getCountryCode()!!
                try {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    val search = call.request.queryParameters["search"]
                    val fechaInicio = call.request.queryParameters["fecha_inicio"]?.let(::parseDateOrBadRequest)
                    val fechaFin = call.request.queryParameters["fecha_fin"]?.let(::parseDateOrBadRequest)

                    if (limit <= 0 || limit > 200 || offset < 0) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Parámetros de paginación inválidos")
                        )
                    }

                    call.respond(
                        creditNoteService.list(
                            database = database,
                            countryCode = countryCode,
                            limit = limit,
                            offset = offset,
                            search = search,
                            fechaInicio = fechaInicio,
                            fechaFin = fechaFin,
                        )
                    )
                } catch (e: CreditNoteValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Solicitud inválida")))
                }
            }

            get("/facturas") {
                val resolved = resolveCompanyDatabase(call) ?: return@get
                val (database, _) = resolved
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    val search = call.request.queryParameters["search"]

                    if (limit <= 0 || limit > 200 || offset < 0) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Parámetros de paginación inválidos")
                        )
                    }

                    call.respond(
                        creditNoteService.listEligibleInvoices(
                            database = database,
                            countryCode = resolved.second.getCountryCode()!!,
                            limit = limit,
                            offset = offset,
                            search = search,
                        )
                    )
            }

            get("/facturas/{id}") {
                val resolved = resolveCompanyDatabase(call) ?: return@get
                val (database, principal) = resolved
                val invoiceId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Factura requerida")
                    )

                val detail = creditNoteService.getInvoiceDetail(
                    database,
                    invoiceId,
                    principal.getCountryCode()!!,
                )
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Factura no encontrada")
                    )

                call.respond(detail)
            }

            get("/{id}") {
                val resolved = resolveCompanyDatabase(call) ?: return@get
                val (database, principal) = resolved
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Nota de crédito requerida")
                    )

                val countryCode = principal.getCountryCode()!!
                val detail = creditNoteService.getDetail(database, id, countryCode)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Nota de crédito no encontrada")
                    )

                call.respond(detail)
            }

            post {
                val resolved = resolveCompanyDatabase(call) ?: return@post
                val (database, principal) = resolved
                val request = call.receive<CreateCreditNoteRequest>()
                val username = principal.payload.getClaim("username").asString().orEmpty().ifBlank { "POS" }
                val countryCode = principal.getCountryCode()!!

                try {
                    val response = creditNoteService.create(
                        database = database,
                        countryCode = countryCode,
                        request = request,
                        username = username,
                        companyDb = call.request.headers["Company-DB"],
                    )
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: CreditNoteValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Solicitud inválida")))
                } catch (e: CreditNoteNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Registro no encontrado")))
                }
            }

            post("/{id}/confirmacion-fiscal") {
                val resolved = resolveCompanyDatabase(call) ?: return@post
                val (database, principal) = resolved
                val countryCode = principal.getCountryCode()!!
                val id = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Nota de crédito requerida")
                    )
                val request = call.receive<ConfirmCreditNoteFiscalRequest>()

                try {
                    val response = creditNoteService.confirmFiscal(database, countryCode, id, request)
                    call.respond(response)
                } catch (e: CreditNoteValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Solicitud inválida")))
                } catch (e: CreditNoteNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Registro no encontrado")))
                }
            }
        }
    }
}

private suspend fun resolveCompanyDatabase(call: ApplicationCall): Pair<org.jetbrains.exposed.sql.Database, JWTPrincipal>? {
    val principal = call.principal<JWTPrincipal>()
        ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
            return null
        }

    val tokenType = principal.payload.getClaim("token_type").asString()
    if (tokenType != "company") {
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
        return null
    }

    val companyDbHeader = call.request.headers["Company-DB"]
    val adminDb = principal.getAdminDb()
        ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
            return null
        }

    if (companyDbHeader.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
        return null
    }
    if (!companyDbHeader.equals(adminDb, ignoreCase = true)) {
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Company-DB no coincide con la empresa autenticada"))
        return null
    }

    val countryCode = principal.getCountryCode()
        ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
            return null
        }
    val database = DatabaseManager.connectToCompanyDb(countryCode, companyDbHeader)
    return database to principal
}

private fun parseDateOrBadRequest(value: String): LocalDate {
    return runCatching { LocalDate.parse(value) }
        .getOrElse { throw CreditNoteValidationException("Fecha inválida, usa formato yyyy-MM-dd") }
}
