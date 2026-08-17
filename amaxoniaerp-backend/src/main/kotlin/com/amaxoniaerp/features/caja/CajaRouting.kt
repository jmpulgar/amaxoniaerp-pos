package com.amaxoniaerp.features.caja

import com.amaxoniaerp.core.tenant.getAdminDb
import com.amaxoniaerp.core.tenant.getCountryCode
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaCodigoResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGetResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaResumenResponse
import com.amaxoniaerp.features.caja.domain.CajaStatusResponse
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
import org.slf4j.LoggerFactory

private const val ERR_QUERY_SEQUENCE = "No se pudo consultar la secuencia"
private const val ERR_CALCULATE_SEQUENCE = "No se pudo calcular secuencia"

fun Route.cajaRouting(cajaRepository: CajaRepository) {
    val handlers = CajaHandlers(cajaRepository)

    route("/api/cajas") {
        authenticate {
            get { handlers.listar(call) }
            post("/open") { handlers.abrir(call) }
            get("/{id}/status") { handlers.status(call) }
            get("/{id}/secuencia") { handlers.resumenSecuencia(call) }
            get("/secuencia") { handlers.datosSecuencia(call) }
            get("/secuencia/codigo") { handlers.codigoSecuencia(call) }
            post("/close") { handlers.cerrar(call) }
        }
    }
}

internal data class CajaCompanyContext(
    val countryCode: String,
    val companyDb: String,
)

/**
 * Handlers de los endpoints de caja. Validan contexto/parámetros, ejecutan la
 * operación del repositorio y mapean el resultado a HTTP.
 */
internal class CajaHandlers(
    private val cajaRepository: CajaRepository,
) {
    private val log = LoggerFactory.getLogger("CajaRouting")

    suspend fun listar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCajaCompanyContext() ?: return@run
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("user_id").asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido: falta user_id"))
                return@run
            }

            val cajas = cajaRepository.getCajas(ctx.countryCode, ctx.companyDb, userId)
            log.debug("Cajas listadas. companyDb={} userId={}", ctx.companyDb, userId)
            call.respond(HttpStatusCode.OK, cajas)
        }

    suspend fun abrir(call: ApplicationCall) =
        run {
            val ctx = call.resolveCajaCompanyContext() ?: return@run
            val principal = call.principal<JWTPrincipal>()!!
            val username =
                principal.payload
                    .getClaim("username")
                    .asString()
                    .orEmpty()
                    .ifBlank { "Unknown" }

            val request = call.receive<AperturaRequest>()
            val result = cajaRepository.openCaja(ctx.countryCode, ctx.companyDb, request, username)

            result.fold(
                onSuccess = { cajaSecuencia ->
                    call.respond(
                        HttpStatusCode.OK,
                        CajaStatusResponse(isOpen = true, cajaSecuencia = cajaSecuencia),
                    )
                },
                onFailure = { error ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                },
            )
        }

    suspend fun status(call: ApplicationCall) =
        run {
            val ctx = call.resolveCajaCompanyContext() ?: return@run

            val idCaja = call.parameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                return@run
            }

            val cajaSecuencia = cajaRepository.getCajaStatus(ctx.countryCode, ctx.companyDb, idCaja)
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
            val ctx = call.resolveCajaCompanyContext() ?: return@run

            val idCaja = call.parameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                return@run
            }

            val summary = cajaRepository.getCajaSequenceSummary(ctx.countryCode, ctx.companyDb, idCaja)
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
            val ctx = call.resolveCajaCompanyContext() ?: return@run

            val id = call.request.queryParameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                return@run
            }

            val verify =
                call.request.queryParameters["by.verificar_facturas_temporales"]
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                    ?: false

            cajaRepository.getCajaSecuenciaData(ctx.countryCode, ctx.companyDb, id, verify).fold(
                onSuccess = { data ->
                    call.respond(HttpStatusCode.OK, CajaSecuenciaGetResponse(success = true, data = data))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaSecuenciaGetResponse(success = false, error = error.message ?: ERR_QUERY_SEQUENCE),
                    )
                },
            )
        }

    suspend fun codigoSecuencia(call: ApplicationCall) =
        run {
            val ctx = call.resolveCajaCompanyContext() ?: return@run

            val idCaja = call.request.queryParameters["id"]
            if (idCaja.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                return@run
            }

            cajaRepository.getNextSecuenciaCodigo(ctx.countryCode, ctx.companyDb, idCaja).fold(
                onSuccess = { codigo ->
                    call.respond(HttpStatusCode.OK, CajaSecuenciaCodigoResponse(codigo = codigo))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (error.message ?: ERR_CALCULATE_SEQUENCE)),
                    )
                },
            )
        }

    suspend fun cerrar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCajaCompanyContext() ?: return@run

            val request =
                runCatching { call.receive<CajaCierreSaveRequest>() }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "Payload inválido",
                            error = e.message,
                        ),
                    )
                    return@run
                }

            cajaRepository.saveCajaCierre(ctx.countryCode, ctx.companyDb, request).fold(
                onSuccess = { response ->
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "No se pudo cerrar la caja",
                            error = error.message,
                            id = request.id,
                        ),
                    )
                },
            )
        }
}

/**
 * Misma regla que notas de crédito / ventas POS: token de empresa, `Company-DB` = `admin_db`, `country_code` en JWT.
 */
internal suspend fun ApplicationCall.resolveCajaCompanyContext(): CajaCompanyContext? =
    run {
        val principal = principal<JWTPrincipal>()
        if (principal == null) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
            return@run null
        }
        if (principal.payload.getClaim("token_type").asString() != "company") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
            return@run null
        }
        val companyDbHeader = request.headers["Company-DB"]
        if (companyDbHeader.isNullOrBlank()) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
            return@run null
        }
        val adminDb = principal.getAdminDb()
        if (adminDb == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
            return@run null
        }
        if (!companyDbHeader.equals(adminDb, ignoreCase = true)) {
            respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "Company-DB no coincide con la empresa autenticada"),
            )
            return@run null
        }
        val countryCode = principal.getCountryCode()
        if (countryCode == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
            return@run null
        }
        CajaCompanyContext(countryCode = countryCode, companyDb = companyDbHeader)
    }
