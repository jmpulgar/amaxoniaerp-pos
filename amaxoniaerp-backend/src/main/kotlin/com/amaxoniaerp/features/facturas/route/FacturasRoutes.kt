package com.amaxoniaerp.features.facturas.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.facturas.domain.FacturasListResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Route.facturasRoutes(
    facturasRepository: FacturasRepository,
    panamaInvoiceProcessor: PanamaInvoiceProcessor,
) {
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

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

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

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val (facturas, total) = facturasRepository.listFacturas(
                    database = companyDb,
                    countryCode = countryCode,
                    limit = limit,
                    offset = offset,
                    search = search,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    estatusList = estatusList,
                )

                call.respond(FacturasListResponse(data = facturas, total = total))
            }

            get("/resumen") {
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

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val resumen = facturasRepository.getResumen(companyDb, countryCode)
                call.respond(resumen)
            }

            get("/{id}/detalle") {
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

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

                val facturaId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing factura ID")
                    )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val detalle = facturasRepository.getFacturaDetalle(companyDb, countryCode, facturaId)

                if (detalle == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Factura no encontrada"))
                } else {
                    call.respond(detalle)
                }
            }

            get("/{id}/print-payload") {
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

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

                if (!countryCode.equals("PA", ignoreCase = true)) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El payload de impresión SUNMI solo está disponible para Panamá")
                    )
                }

                val facturaId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing factura ID")
                    )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val payload = facturasRepository.getPrintPayload(
                    database = companyDb,
                    countryCode = countryCode,
                    facturaId = facturaId,
                    companyNameFallback = adminDb,
                )

                if (payload == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Factura no encontrada"))
                } else {
                    call.respond(payload)
                }
            }

            patch("/{id}/confirmacion-fiscal") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@patch call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid token")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@patch call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required")
                    )
                }

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

                val facturaId = call.parameters["id"]
                    ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing factura ID")
                    )

                val request = call.receive<ConfirmFacturaFiscalRequest>()

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                try {
                    val response = facturasRepository.confirmFiscal(companyDb, countryCode, facturaId, request)
                    call.respond(response)
                } catch (e: NoSuchElementException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Factura no encontrada")))
                }
            }

            post("/{id}/enviar-correo") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid token")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required")
                    )
                }

                val adminDb = principal.getAdminDb()
                if (adminDb.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Database not found")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Country code not found")
                    )

                if (!countryCode.equals("PA", ignoreCase = true)) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El envío por correo FEL solo está disponible para Panamá")
                    )
                }

                val facturaId = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing factura ID")
                    )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                panamaInvoiceProcessor.resendInvoiceEmail(companyDb, facturaId).fold(
                    onSuccess = { call.respond(it) },
                    onFailure = { throwable ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (throwable.message ?: "No se pudo enviar el correo"))
                        )
                    }
                )
            }
        }
    }
}
