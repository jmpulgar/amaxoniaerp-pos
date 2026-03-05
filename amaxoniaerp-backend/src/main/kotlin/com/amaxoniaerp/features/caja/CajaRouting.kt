package com.amaxoniaerp.features.caja

import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaCodigoResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGetResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaResumenResponse
import com.amaxoniaerp.features.caja.domain.CajaStatusResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Route.cajaRouting(cajaRepository: CajaRepository) {
    val log = LoggerFactory.getLogger("CajaRouting")

    route("/api/cajas") {
        authenticate {
            get {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("user_id")?.asInt()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido: falta user_id"))
                    return@get
                }

                try {
                    val cajas = cajaRepository.getCajas(dbName, userId)
                    call.respond(HttpStatusCode.OK, cajas)
                } catch (e: Exception) {
                    log.error("Error getting cajas. companyDb={}", dbName, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/open") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@post
                }

                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString() ?: "Unknown"

                try {
                    val request = call.receive<AperturaRequest>()
                    val result = cajaRepository.openCaja(dbName, request, username)
                    
                    result.fold(
                        onSuccess = { cajaSecuencia ->
                            call.respond(
                                HttpStatusCode.OK,
                                CajaStatusResponse(isOpen = true, cajaSecuencia = cajaSecuencia)
                            )
                        },
                        onFailure = { error ->
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        }
                    )
                } catch (e: Exception) {
                    log.error("Error opening caja. companyDb={} user={}", dbName, username, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/{id}/status") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@get
                }

                val idCaja = call.parameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                    return@get
                }

                try {
                    val cajaSecuencia = cajaRepository.getCajaStatus(dbName, idCaja)
                    if (cajaSecuencia != null) {
                        call.respond(
                            HttpStatusCode.OK,
                            CajaStatusResponse(isOpen = true, cajaSecuencia = cajaSecuencia)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            CajaStatusResponse(isOpen = false, cajaSecuencia = null)
                        )
                    }
                } catch (e: Exception) {
                    log.error("Error checking caja status. companyDb={} idCaja={}", dbName, idCaja, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/{id}/secuencia") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@get
                }

                val idCaja = call.parameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja ID is missing"))
                    return@get
                }

                try {
                    val summary = cajaRepository.getCajaSequenceSummary(dbName, idCaja)
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
                    log.error("Error getting caja sequence summary. companyDb={} idCaja={}", dbName, idCaja, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/secuencia") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@get
                }

                val id = call.request.queryParameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                    return@get
                }

                val verify = call.request.queryParameters["by.verificar_facturas_temporales"]
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                    ?: false

                cajaRepository.getCajaSecuenciaData(dbName, id, verify).fold(
                    onSuccess = { data ->
                        call.respond(HttpStatusCode.OK, CajaSecuenciaGetResponse(success = true, data = data))
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            CajaSecuenciaGetResponse(success = false, error = error.message ?: "No se pudo consultar la secuencia")
                        )
                    }
                )
            }

            get("/secuencia/codigo") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@get
                }

                val idCaja = call.request.queryParameters["id"]
                if (idCaja.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro id es requerido"))
                    return@get
                }

                cajaRepository.getNextSecuenciaCodigo(dbName, idCaja).fold(
                    onSuccess = { codigo ->
                        call.respond(HttpStatusCode.OK, CajaSecuenciaCodigoResponse(codigo = codigo))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (error.message ?: "No se pudo calcular secuencia")))
                    }
                )
            }

            post("/close") {
                val dbName = call.request.headers["Company-DB"]
                if (dbName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Company-DB header is missing"))
                    return@post
                }

                val request = runCatching { call.receive<CajaCierreSaveRequest>() }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CajaCierreSaveResponse(
                            success = false,
                            message = "Payload inválido",
                            error = e.message
                        )
                    )
                    return@post
                }

                cajaRepository.saveCajaCierre(dbName, request).fold(
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
                            )
                        )
                    }
                )
            }
        }
    }
}
