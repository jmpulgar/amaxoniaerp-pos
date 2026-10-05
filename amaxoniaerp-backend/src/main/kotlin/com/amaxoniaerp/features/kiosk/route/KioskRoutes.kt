package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.kiosk.application.KioskAuthenticationException
import com.amaxoniaerp.features.kiosk.application.KioskRateLimitException
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.domain.KioskPairingRequest
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.kioskRoutes(kioskService: KioskService) {
    route("/api/v1/kiosk") {
        /**
         * Emparejamiento inicial de un dispositivo kiosco con código de un solo uso.
         */
        post("/pairing") {
            val request = try {
                call.receive<KioskPairingRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
                return@post
            }

            kioskService.pairDevice(request)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    when (error) {
                        is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (error.message ?: "Solicitud inválida")))
                        is KioskAuthenticationException -> call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (error.message ?: "No autorizado")))
                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (error.message ?: "Error interno")))
                    }
                }
        }

        /**
         * Desbloqueo de modo kiosco con clave de administración (verificada en servidor con BCrypt).
         */
        authenticate {
            post("/unlock") {
                val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post

                val request = try {
                    call.receive<KioskUnlockRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
                    return@post
                }

                kioskService.unlock(kioskContext, request)
                    .onSuccess {
                        call.respond(HttpStatusCode.NoContent)
                    }
                    .onFailure { error ->
                        when (error) {
                            is KioskRateLimitException -> call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to (error.message ?: "Límite de intentos excedido")))
                            is KioskAuthenticationException -> call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (error.message ?: "No autorizado")))
                            is IllegalStateException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (error.message ?: "Error de configuración")))
                            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (error.message ?: "Error interno")))
                        }
                    }
            }
        }
    }
}
