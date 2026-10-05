package com.amaxoniaerp.features.caja

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.tenant.requireCompanyDbHeader
import com.amaxoniaerp.core.tenant.requireUserId
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaCodigoResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGetResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaResumenResponse
import com.amaxoniaerp.features.caja.domain.CajaStatusResponse
import com.amaxoniaerp.features.caja.domain.SaveCajaRequest
import com.amaxoniaerp.features.caja.domain.SaveCajaResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private const val ERR_QUERY_SEQUENCE = "No se pudo consultar la secuencia"
private const val ERR_CALCULATE_SEQUENCE = "No se pudo calcular secuencia"
private const val ERR_OPEN_CAJA = "No se pudo abrir la caja"
private const val ERR_CLOSE_CAJA = "No se pudo cerrar la caja"
private const val ERR_INVALID_PAYLOAD = "Payload inválido"

/**
 * Mensaje público estable para fallos de caja: expone sólo los mensajes de
 * negocio (`IllegalStateException` lanzados por el repositorio con `error(...)`);
 * cualquier falla interna (SQL, red, etc.) se queda en el log y se responde el
 * fallback estable.
 */
private fun Throwable.publicMessage(fallback: String): String =
    (this as? IllegalStateException)?.message?.takeIf { it.isNotBlank() } ?: fallback

fun Route.cajaRouting(
    cajaRepository: CajaRepository,
    cajaSession: CajaSessionWorkflow,
) {
    val handlers = CajaHandlers(cajaRepository, cajaSession)

    route("/api/cajas") {
        authenticate {
            get { handlers.listar(call) }
            post { handlers.crear(call) }
            get("/{id}") { handlers.detalle(call) }
            put("/{id}") { handlers.actualizar(call) }
            post("/open") { handlers.abrir(call) }
            get("/{id}/status") { handlers.status(call) }
            get("/{id}/secuencia") { handlers.resumenSecuencia(call) }
            get("/secuencia") { handlers.datosSecuencia(call) }
            get("/secuencia/codigo") { handlers.codigoSecuencia(call) }
            post("/close") { handlers.cerrar(call) }
        }
    }
}

/**
 * Handlers de los endpoints de caja. Validan contexto/parámetros, ejecutan la
 * operación del repositorio y mapean el resultado a HTTP.
 */
internal class CajaHandlers(
    private val cajaRepository: CajaRepository,
    private val cajaSession: CajaSessionWorkflow,
) {
    private val log = LoggerFactory.getLogger("CajaRouting")

    suspend fun listar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run
            val userId = ctx.requireUserId(call) ?: return@run
            val all = call.request.queryParameters["all"]?.toBooleanStrictOrNull() == true

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val cajas = cajaRepository.getCajas(database, ctx.countryCode, userId, all)
            log.debug("Cajas listadas. companyDb={} userId={} all={}", companyDb, userId, all)
            call.respond(HttpStatusCode.OK, cajas)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID de caja requerido"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val caja = cajaRepository.getCajaById(database, ctx.countryCode, id)
            if (caja == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Caja no encontrada"))
                return@run
            }
            call.respond(HttpStatusCode.OK, caja)
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run
            val request =
                runCatching { call.receive<SaveCajaRequest>() }.getOrElse {
                    log.warn("Payload de caja inválido", it)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_PAYLOAD))
                    return@run
                }

            if (request.caja.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El nombre de la caja es requerido"))
                return@run
            }
            if (request.serieCaja.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "La serie de la caja es requerida"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val created = cajaRepository.createCaja(database, ctx.countryCode, request)
            log.info("Caja creada con éxito. companyDb={} idCaja={}", companyDb, created.idCaja)
            call.respond(HttpStatusCode.Created, SaveCajaResponse(success = true, data = created))
        }

    suspend fun actualizar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID de caja requerido"))
                return@run
            }

            val request =
                runCatching { call.receive<SaveCajaRequest>() }.getOrElse {
                    log.warn("Payload de caja inválido", it)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_PAYLOAD))
                    return@run
                }

            if (request.caja.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El nombre de la caja es requerido"))
                return@run
            }
            if (request.serieCaja.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "La serie de la caja es requerida"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val updated = cajaRepository.updateCaja(database, ctx.countryCode, id, request)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Caja no encontrada"))
                return@run
            }
            log.info("Caja actualizada con éxito. companyDb={} idCaja={}", companyDb, id)
            call.respond(HttpStatusCode.OK, SaveCajaResponse(success = true, data = updated))
        }

    suspend fun abrir(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run
            val username =
                ctx.principal.payload
                    .getClaim("username")
                    .asString()
                    .orEmpty()
                    .ifBlank { "Unknown" }

            val request = call.receive<AperturaRequest>()
            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val result = cajaSession.open(database, ctx.countryCode, companyDb, request, username)

            result.fold(
                onSuccess = { cajaSecuencia ->
                    call.respond(
                        HttpStatusCode.OK,
                        CajaStatusResponse(isOpen = true, cajaSecuencia = cajaSecuencia),
                    )
                },
                onFailure = { error ->
                    log.warn("No se pudo abrir la caja. companyDb={} idCaja={}", companyDb, request.idCaja, error)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to error.publicMessage(ERR_OPEN_CAJA)),
                    )
                },
            )
        }

    suspend fun status(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run

            val idCaja = call.parameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val cajaSecuencia = cajaSession.status(database, companyDb, idCaja)
            if (cajaSecuencia != null) {
                call.respond(
                    HttpStatusCode.OK,
                    CajaStatusResponse(isOpen = true, cajaSecuencia = cajaSecuencia),
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    CajaStatusResponse(isOpen = false, cajaSecuencia = null),
                )
            }
        }

    suspend fun resumenSecuencia(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run

            val idCaja = call.parameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            val summary = cajaRepository.getCajaSequenceSummary(database, ctx.countryCode, companyDb, idCaja)
            if (summary == null) {
                call.respond(
                    HttpStatusCode.OK,
                    CajaSecuenciaResumenResponse(
                        isOpen = false,
                        summary = null,
                        error = "No hay una secuencia de caja abierta",
                    ),
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    CajaSecuenciaResumenResponse(
                        isOpen = true,
                        summary = summary,
                    ),
                )
            }
        }

    suspend fun datosSecuencia(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run

            val id = call.request.queryParameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                return@run
            }

            val verify =
                call.request.queryParameters["by.verificar_facturas_temporales"]
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                    ?: false

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            cajaRepository.getCajaSecuenciaData(database, ctx.countryCode, id, verify).fold(
                onSuccess = { data ->
                    call.respond(HttpStatusCode.OK, CajaSecuenciaGetResponse(success = true, data = data))
                },
                onFailure = { error ->
                    log.warn("No se pudo consultar la secuencia. id={}", id, error)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaSecuenciaGetResponse(success = false, error = error.publicMessage(ERR_QUERY_SEQUENCE)),
                    )
                },
            )
        }

    suspend fun codigoSecuencia(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run

            val idCaja = call.request.queryParameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                return@run
            }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            cajaRepository.getNextSecuenciaCodigo(database, idCaja).fold(
                onSuccess = { codigo ->
                    call.respond(HttpStatusCode.OK, CajaSecuenciaCodigoResponse(codigo = codigo))
                },
                onFailure = { error ->
                    log.warn("No se pudo calcular secuencia. idCaja={}", idCaja, error)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to error.publicMessage(ERR_CALCULATE_SEQUENCE)),
                    )
                },
            )
        }

    suspend fun cerrar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val companyDb = ctx.requireCompanyDbHeader(call) ?: return@run

            val request =
                runCatching { call.receive<CajaCierreSaveRequest>() }.getOrElse { e ->
                    log.warn("Payload inválido al cerrar caja.", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "Payload inválido",
                            error = e.publicMessage(ERR_INVALID_PAYLOAD),
                        ),
                    )
                    return@run
                }

            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, companyDb)
            cajaSession.close(database, ctx.countryCode, request).fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    log.warn("No se pudo cerrar la caja. id={}", request.id, error)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "No se pudo cerrar la caja",
                            error = error.publicMessage(ERR_CLOSE_CAJA),
                            id = request.id,
                        ),
                    )
                },
            )
        }
}
