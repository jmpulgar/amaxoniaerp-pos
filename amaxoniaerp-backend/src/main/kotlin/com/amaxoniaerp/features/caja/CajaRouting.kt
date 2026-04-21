package com.amaxoniaerp.features.caja

import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
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

fun Route.cajaRouting(cajaRepository: CajaRepository) {
    val log = LoggerFactory.getLogger("CajaRouting")

    route("/api/cajas") {
        authenticate {
            get {
                val ctx = call.resolveCajaCompanyContext() ?: return@get
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("user_id").asInt()
                    ?: run {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido: falta user_id"))
                        return@get
                    }

                try {
                    val cajas = cajaRepository.getCajas(ctx.countryCode, ctx.companyDb, userId)
                    call.respond(HttpStatusCode.OK, cajas)
                } catch (e: Exception) {
                    log.error("Error getting cajas. companyDb={}", ctx.companyDb, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/open") {
                val ctx = call.resolveCajaCompanyContext() ?: return@post
                val principal = call.principal<JWTPrincipal>()!!
                val username = principal.payload.getClaim("username").asString().orEmpty().ifBlank { "Unknown" }

                try {
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
                } catch (e: Exception) {
                    log.error("Error opening caja. companyDb={} user={}", ctx.companyDb, username, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/{id}/status") {
                val ctx = call.resolveCajaCompanyContext() ?: return@get

                val idCaja = call.parameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                    return@get
                }

                try {
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
                } catch (e: Exception) {
                    log.error("Error checking caja status. companyDb={} idCaja={}", ctx.companyDb, idCaja, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/{id}/secuencia") {
                val ctx = call.resolveCajaCompanyContext() ?: return@get

                val idCaja = call.parameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                    return@get
                }

                try {
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
                } catch (e: Exception) {
                    log.error("Error getting caja sequence summary. companyDb={} idCaja={}", ctx.companyDb, idCaja, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/secuencia") {
                val ctx = call.resolveCajaCompanyContext() ?: return@get

                val id = call.request.queryParameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                    return@get
                }

                val verify = call.request.queryParameters["by.verificar_facturas_temporales"]
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                    ?: false

                cajaRepository.getCajaSecuenciaData(ctx.countryCode, ctx.companyDb, id, verify).fold(
                    onSuccess = { data ->
                        call.respond(HttpStatusCode.OK, CajaSecuenciaGetResponse(success = true, data = data))
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            CajaSecuenciaGetResponse(success = false, error = error.message ?: "No se pudo consultar la secuencia"),
                        )
                    },
                )
            }

            get("/secuencia/codigo") {
                val ctx = call.resolveCajaCompanyContext() ?: return@get

                val idCaja = call.request.queryParameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                    return@get
                }

                cajaRepository.getNextSecuenciaCodigo(ctx.countryCode, ctx.companyDb, idCaja).fold(
                    onSuccess = { codigo ->
                        call.respond(HttpStatusCode.OK, CajaSecuenciaCodigoResponse(codigo = codigo))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (error.message ?: "No se pudo calcular secuencia")))
                    },
                )
            }

            post("/close") {
                val ctx = call.resolveCajaCompanyContext() ?: return@post

                val request = runCatching { call.receive<CajaCierreSaveRequest>() }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "Payload inválido",
                            error = e.message,
                        ),
                    )
                    return@post
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
    }
}

private data class CajaCompanyContext(
    val countryCode: String,
    val companyDb: String,
)

/**
 * Misma regla que notas de crédito / ventas POS: token de empresa, `Company-DB` = `admin_db`, `country_code` en JWT.
 */
private suspend fun ApplicationCall.resolveCajaCompanyContext(): CajaCompanyContext? {
    val principal = principal<JWTPrincipal>()
        ?: run {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
            return null
        }

    if (principal.payload.getClaim("token_type").asString() != "company") {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
        return null
    }

    val companyDbHeader = request.headers["Company-DB"]
    if (companyDbHeader.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
        return null
    }

    val adminDb = principal.getAdminDb()
        ?: run {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
            return null
        }

    if (!companyDbHeader.equals(adminDb, ignoreCase = true)) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Company-DB no coincide con la empresa autenticada"))
        return null
    }

    val countryCode = principal.getCountryCode()
        ?: run {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
            return null
        }

    return CajaCompanyContext(countryCode = countryCode, companyDb = companyDbHeader)
}
