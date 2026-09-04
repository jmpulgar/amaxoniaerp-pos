package com.amaxoniaerp.features.facturas.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.facturas.data.FacturasFilter
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.facturas.domain.FacturasListResponse
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000

fun Route.facturasRoutes(
    facturasRepository: FacturasRepository,
    panamaInvoiceProcessor: PanamaInvoiceProcessor,
) {
    val handlers = FacturasHandlers(facturasRepository, panamaInvoiceProcessor)

    authenticate {
        route("/facturas") {
            get { handlers.listar(call) }
            get("/resumen") { handlers.resumen(call) }
            get("/by-id-factura/{idFactura}") { handlers.porIdFactura(call) }
            get("/{id}/detalle") { handlers.detalle(call) }
            get("/{id}/print-payload") { handlers.printPayload(call) }
            get("/{id}/pdf") { handlers.descargarPdf(call) }
            patch("/{id}/confirmacion-fiscal") { handlers.confirmarFiscal(call) }
            post("/{id}/enviar-correo") { handlers.enviarCorreo(call) }
        }
    }
}

/**
 * Handlers de los endpoints de facturas. Resuelven tenant por el seam canónico,
 * delegan en el repositorio/processor y mapean los resultados a HTTP.
 */
internal class FacturasHandlers(
    private val facturasRepository: FacturasRepository,
    private val panamaInvoiceProcessor: PanamaInvoiceProcessor,
) {
    suspend fun listar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

            if (limit <= 0 || limit > MAX_PAGE_LIMIT || offset < 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid pagination parameters"))
                return@run
            }

            val filter =
                call.request.queryParameters.toFacturasFilter().getOrElse { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to (error.message ?: "Invalid invoice filters"),
                        ),
                    )
                    return@run
                }

            val companyDb = ctx.connectDatabase()
            val (facturas, total) =
                facturasRepository.listFacturas(
                    database = companyDb,
                    countryCode = ctx.countryCode,
                    limit = limit,
                    offset = offset,
                    filter = filter,
                )

            call.respond(FacturasListResponse(data = facturas, total = total))
        }

    suspend fun resumen(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val filter =
                call.request.queryParameters.toFacturasFilter().getOrElse { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to (error.message ?: "Invalid invoice filters"),
                        ),
                    )
                    return@run
                }

            val companyDb = ctx.connectDatabase()
            val resumen = facturasRepository.getResumen(companyDb, ctx.countryCode, filter)
            call.respond(resumen)
        }

    suspend fun porIdFactura(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val idFactura = call.requireParameter("idFactura", "Missing idFactura") ?: return@run

            val database = ctx.connectDatabase()
            val factura = facturasRepository.findByCorrelationId(database, ctx.countryCode, idFactura)
            call.respondFactura(factura)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val facturaId = call.requireParameter("id", "Missing factura ID") ?: return@run

            val companyDb = ctx.connectDatabase()
            val detalle = facturasRepository.getFacturaDetalle(companyDb, ctx.countryCode, facturaId)
            call.respondFactura(detalle)
        }

    suspend fun printPayload(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val facturaId = call.requireParameter("id", "Missing factura ID") ?: return@run

            val companyDb = ctx.connectDatabase()
            // El repositorio valida internamente los países soportados (PA/VE) y
            // omite los campos fiscales propios de Panamá cuando corresponde.
            // No bloquear el país aquí: si la configuración del POS permite el
            // driver/payload, debe aceptarse a lo largo de toda la cadena.
            val payload =
                try {
                    facturasRepository.getPrintPayload(
                        database = companyDb,
                        countryCode = ctx.countryCode,
                        facturaId = facturaId,
                        companyNameFallback = ctx.adminDb,
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Payload de impresión no disponible")),
                    )
                    return@run
                }

            call.respondFactura(payload)
        }

    suspend fun confirmarFiscal(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val facturaId = call.requireParameter("id", "Missing factura ID") ?: return@run

            val request = call.receive<ConfirmFacturaFiscalRequest>()

            val companyDb = ctx.connectDatabase()
            try {
                val response = facturasRepository.confirmFiscal(companyDb, ctx.countryCode, facturaId, request)
                call.respond(response)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Factura no encontrada")))
            }
        }

    suspend fun enviarCorreo(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            if (!ctx.countryCode.equals("PA", ignoreCase = true)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "El envío por correo FEL solo está disponible para Panamá"),
                )
                return@run
            }

            val facturaId = call.requireParameter("id", "Missing factura ID") ?: return@run

            val companyDb = ctx.connectDatabase()
            panamaInvoiceProcessor.resendInvoiceEmail(companyDb, facturaId).fold(
                onSuccess = { call.respond(it) },
                onFailure = { throwable ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (throwable.message ?: "No se pudo enviar el correo")),
                    )
                },
            )
        }

    suspend fun descargarPdf(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val facturaId = call.requireParameter("id", "Missing factura ID") ?: return@run

            if (!ctx.countryCode.equals("PA", ignoreCase = true)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "La descarga de PDF FEL solo está disponible para Panamá"),
                )
                return@run
            }

            val companyDb = ctx.connectDatabase()
            panamaInvoiceProcessor.downloadInvoicePdf(companyDb, facturaId).fold(
                onSuccess = { bytes ->
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, "factura-$facturaId.pdf")
                            .toString(),
                    )
                    call.respondBytes(bytes, ContentType.Application.Pdf)
                },
                onFailure = { throwable ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (throwable.message ?: "No se pudo descargar el PDF de la factura")),
                    )
                },
            )
        }

    private suspend inline fun <reified T> ApplicationCall.respondFactura(value: T?) {
        if (value == null) {
            respond(HttpStatusCode.NotFound, mapOf("error" to "Factura no encontrada"))
        } else {
            respond(value)
        }
    }

    private suspend fun ApplicationCall.requireParameter(
        name: String,
        errorMessage: String,
    ): String? =
        run {
            val value = parameters[name]?.takeIf(String::isNotBlank)
            if (value == null) {
                respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
                return@run null
            }
            value
        }
}

private fun Parameters.toFacturasFilter(): Result<FacturasFilter> =
    runCatching {
        val fechaInicio = this["fecha_inicio"]?.takeIf(String::isNotBlank)?.let(::parseFacturasDate)
        val fechaFin = this["fecha_fin"]?.takeIf(String::isNotBlank)?.let(::parseFacturasDate)
        val cajaId =
            this["caja_id"]?.takeIf(String::isNotBlank)
                ?: this["id_caja"]?.takeIf(String::isNotBlank)
                ?: this["cajaId"]?.takeIf(String::isNotBlank)

        if (fechaInicio != null && fechaFin != null) {
            if (fechaFin.isBefore(fechaInicio)) {
                throw IllegalArgumentException("La fecha final debe ser mayor o igual a la fecha inicial")
            }
            if (ChronoUnit.DAYS.between(fechaInicio, fechaFin) > 31) {
                throw IllegalArgumentException("El rango de consulta no puede superar 1 mes")
            }
        }

        FacturasFilter(
            search = this["search"]?.takeIf(String::isNotBlank),
            usuario = this["usuario"]?.takeIf(String::isNotBlank),
            cajaId = cajaId,
            fechaInicio = fechaInicio,
            fechaFin = fechaFin,
        )
    }

private fun parseFacturasDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrElse {
        throw IllegalArgumentException("Invalid date format, expected yyyy-MM-dd")
    }
