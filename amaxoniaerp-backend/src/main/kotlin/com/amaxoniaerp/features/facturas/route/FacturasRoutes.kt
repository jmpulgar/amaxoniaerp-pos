package com.amaxoniaerp.features.facturas.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.facturas.data.FacturasFilter
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

                if (limit <= 0 || limit > 1000 || offset < 0) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid pagination parameters")
                    )
                }

                val filter =
                    call.request.queryParameters.toFacturasFilter().getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (error.message ?: "Invalid invoice filters")),
                        )
                    }

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val (facturas, total) = facturasRepository.listFacturas(
                    database = companyDb,
                    countryCode = countryCode,
                    limit = limit,
                    offset = offset,
                    filter = filter,
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

                val filter =
                    call.request.queryParameters.toFacturasFilter().getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (error.message ?: "Invalid invoice filters")),
                        )
                    }

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val resumen = facturasRepository.getResumen(companyDb, countryCode, filter)
                call.respond(resumen)
            }

            get("/by-id-factura/{idFactura}") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Invalid token"),
                        )
                if (principal.payload.getClaim("token_type").asString() != "company") {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Company token required"),
                    )
                }
                val adminDb =
                    principal.getAdminDb()?.takeIf(String::isNotBlank)
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Database not found"),
                        )
                val countryCode =
                    principal.getCountryCode()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Country code not found"),
                        )
                val idFactura =
                    call.parameters["idFactura"]?.takeIf(String::isNotBlank)
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing idFactura"),
                        )
                val database = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val factura = facturasRepository.findByCorrelationId(database, countryCode, idFactura)
                if (factura == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Factura no encontrada"))
                } else {
                    call.respond(factura)
                }
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

                val facturaId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing factura ID")
                    )

                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                // El repositorio valida internamente los países soportados (PA/VE) y
                // omite los campos fiscales propios de Panamá cuando corresponde.
                // No bloquear el país aquí: si la configuración del POS permite el
                // driver/payload, debe aceptarse a lo largo de toda la cadena.
                val payload =
                    try {
                        facturasRepository.getPrintPayload(
                            database = companyDb,
                            countryCode = countryCode,
                            facturaId = facturaId,
                            companyNameFallback = adminDb,
                        )
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Payload de impresión no disponible")),
                        )
                    }

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

private fun Parameters.toFacturasFilter(): Result<FacturasFilter> = runCatching {
    val fechaInicio = this["fecha_inicio"]?.let(::parseFacturasDate)
    val fechaFin = this["fecha_fin"]?.let(::parseFacturasDate)
    val sucursalValue = this["sucursal_id"]?.takeIf(String::isNotBlank)
    val sucursalId = sucursalValue?.toIntOrNull()
        ?: if (sucursalValue != null) {
            throw IllegalArgumentException("Invalid sucursal_id")
        } else {
            null
        }
    val estatusList = this["estatus"]
        ?.takeIf(String::isNotBlank)
        ?.split(",")
        ?.mapNotNull { it.trim().toIntOrNull() }

    FacturasFilter(
        search = this["search"],
        usuario = this["usuario"],
        sucursalId = sucursalId,
        fechaInicio = fechaInicio,
        fechaFin = fechaFin,
        estatusList = estatusList,
    )
}

private fun parseFacturasDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrElse {
        throw IllegalArgumentException("Invalid date format, expected yyyy-MM-dd")
    }
