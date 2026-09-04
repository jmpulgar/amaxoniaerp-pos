package com.amaxoniaerp.features.creditnotes.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.requireCompanyDbHeader
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.data.CreditNoteListQuery
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import java.time.LocalDate

private const val DEFAULT_CREDIT_NOTE_PAGE_LIMIT = 50
private const val MAX_CREDIT_NOTE_PAGE_LIMIT = 200

private data class CreditNoteRequestScope(
    val database: Database,
    val countryCode: String,
    val companyDb: String,
    val username: String,
)

fun Route.creditNoteRoutes(creditNoteService: CreditNoteService) {
    val handlers = CreditNoteHandlers(creditNoteService)

    authenticate {
        route("/api/pos/notas-credito") {
            get { handlers.listar(call) }
            get("/facturas") { handlers.listarFacturasElegibles(call) }
            get("/facturas/{id}") { handlers.detalleFactura(call) }
            get("/{id}") { handlers.detalle(call) }
            post { handlers.crear(call) }
            post("/{id}/confirmacion-fiscal") { handlers.confirmarFiscal(call) }
        }
    }
}

/**
 * Handlers de los endpoints de notas de crédito. Resuelven tenant por el seam
 * canónico (+ header `Company-DB`), ejecutan la operación y mapean errores.
 */
internal class CreditNoteHandlers(
    private val creditNoteService: CreditNoteService,
) {
    suspend fun listar(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_CREDIT_NOTE_PAGE_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val search = call.request.queryParameters["search"]
            val fechaInicio = call.request.queryParameters["fecha_inicio"]?.let(::parseDateOrBadRequest)
            val fechaFin = call.request.queryParameters["fecha_fin"]?.let(::parseDateOrBadRequest)

            if (fechaInicio != null && fechaFin != null) {
                if (fechaFin.isBefore(fechaInicio)) {
                    throw CreditNoteValidationException("La fecha final debe ser mayor o igual a la fecha inicial")
                }
                if (java.time.temporal.ChronoUnit.DAYS
                        .between(fechaInicio, fechaFin) > 31
                ) {
                    throw CreditNoteValidationException("El rango de consulta no puede superar 1 mes")
                }
            }

            if (limit <= 0 || limit > MAX_CREDIT_NOTE_PAGE_LIMIT || offset < 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parámetros de paginación inválidos"))
                return@run
            }

            call.respond(
                creditNoteService.list(
                    database = scope.database,
                    countryCode = scope.countryCode,
                    query =
                        CreditNoteListQuery(
                            limit = limit,
                            offset = offset,
                            search = search,
                            fechaInicio = fechaInicio,
                            fechaFin = fechaFin,
                        ),
                ),
            )
        }

    suspend fun listarFacturasElegibles(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_CREDIT_NOTE_PAGE_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val search = call.request.queryParameters["search"]
            val fechaInicioParam = call.request.queryParameters["fecha_inicio"]
            val fechaFinParam = call.request.queryParameters["fecha_fin"]
            val fechaInicio = fechaInicioParam?.let(::parseDateOrBadRequest) ?: LocalDate.now()
            val fechaFin = fechaFinParam?.let(::parseDateOrBadRequest) ?: LocalDate.now()

            if (fechaFin.isBefore(fechaInicio)) {
                throw CreditNoteValidationException("La fecha final debe ser mayor o igual a la fecha inicial")
            }
            if (java.time.temporal.ChronoUnit.DAYS
                    .between(fechaInicio, fechaFin) > 31
            ) {
                throw CreditNoteValidationException("El rango de consulta no puede superar 1 mes")
            }

            if (limit <= 0 || limit > MAX_CREDIT_NOTE_PAGE_LIMIT || offset < 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parámetros de paginación inválidos"))
                return@run
            }

            call.respond(
                creditNoteService.listEligibleInvoices(
                    database = scope.database,
                    countryCode = scope.countryCode,
                    limit = limit,
                    offset = offset,
                    search = search,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                ),
            )
        }

    suspend fun detalleFactura(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val invoiceId = call.parameters["id"]
            if (invoiceId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Factura requerida"))
                return@run
            }

            val detail =
                creditNoteService.getInvoiceDetail(scope.database, invoiceId, scope.countryCode)
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Factura no encontrada"))
                return@run
            }

            call.respond(detail)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nota de crédito requerida"))
                return@run
            }

            val detail =
                creditNoteService.getDetail(scope.database, id, scope.countryCode)
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Nota de crédito no encontrada"))
                return@run
            }

            call.respond(detail)
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val request = call.receive<CreateCreditNoteRequest>()

            val response =
                creditNoteService.create(
                    database = scope.database,
                    countryCode = scope.countryCode,
                    request = request,
                    username = scope.username,
                    companyDb = scope.companyDb,
                )
            call.respond(HttpStatusCode.Created, response)
        }

    suspend fun confirmarFiscal(call: ApplicationCall) =
        run {
            val scope = resolveScope(call) ?: return@run
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nota de crédito requerida"))
                return@run
            }
            val request = call.receive<ConfirmCreditNoteFiscalRequest>()

            val response = creditNoteService.confirmFiscal(scope.database, scope.countryCode, id, request)
            call.respond(response)
        }

    /**
     * Misma regla que caja / ventas POS: token de empresa, `Company-DB` = `admin_db`,
     * `country_code` en JWT. Delega en el seam canónico y conecta la base de la empresa.
     */
    private suspend fun resolveScope(call: ApplicationCall): CreditNoteRequestScope? =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run null
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run null
            val database = ctx.connectDatabase(companyDb)
            val username =
                ctx.principal.payload
                    .getClaim("username")
                    .asString()
                    .orEmpty()
                    .ifBlank { "POS" }
            CreditNoteRequestScope(
                database = database,
                countryCode = ctx.countryCode,
                companyDb = companyDb,
                username = username,
            )
        }
}

private fun parseDateOrBadRequest(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }
        .getOrElse { throw CreditNoteValidationException("Fecha inválida, usa formato yyyy-MM-dd") }
