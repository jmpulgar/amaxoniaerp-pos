package com.amaxoniaerp.features.caja

import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.AperturaRequest
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
        }
    }
}
