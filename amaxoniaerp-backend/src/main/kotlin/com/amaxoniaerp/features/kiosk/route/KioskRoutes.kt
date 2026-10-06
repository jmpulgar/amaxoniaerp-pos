package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.kiosk.application.KioskAuthenticationException
import com.amaxoniaerp.features.kiosk.application.KioskRateLimitException
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.domain.KioskPairingRequest
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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

        authenticate {
            /**
             * Configuración del kiosco con soporte de ETag (304 Not Modified).
             */
            get("/config") {
                val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@get
                val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)

                kioskService.getConfig(kioskContext)
                    .onSuccess { (config, etag) ->
                        call.response.headers.append(HttpHeaders.ETag, etag)
                        if (ifNoneMatch != null && (ifNoneMatch == etag || ifNoneMatch == etag.trim('"') || ifNoneMatch == "W/$etag")) {
                            call.respond(HttpStatusCode.NotModified)
                        } else {
                            call.respond(HttpStatusCode.OK, config)
                        }
                    }
                    .onFailure { error ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Error al obtener configuración"))
                        )
                    }
            }

            /**
             * Catálogo del kiosco filtrando departamentos con visible_pos = 1, precios nivel A y modificadores.
             */
            get("/catalog") {
                val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@get
                val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)

                kioskService.getCatalog(kioskContext)
                    .onSuccess { (catalog, etag) ->
                        call.response.headers.append(HttpHeaders.ETag, etag)
                        if (ifNoneMatch != null && (ifNoneMatch == etag || ifNoneMatch == etag.trim('"') || ifNoneMatch == "W/$etag")) {
                            call.respond(HttpStatusCode.NotModified)
                        } else {
                            call.respond(HttpStatusCode.OK, catalog)
                        }
                    }
                    .onFailure { error ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Error al obtener catálogo"))
                        )
                    }
            }

            /**
             * Cotización de pedido del kiosco con validación de modificadores, cálculo en Money y numeración diaria.
             */
            post("/orders/quote") {
                val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post
                val idempotencyKey = call.request.header("Idempotency-Key")?.trim()
                if (idempotencyKey.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Header Idempotency-Key es requerido"))
                    return@post
                }

                val request = try {
                    call.receive<KioskQuoteRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
                    return@post
                }

                kioskService.createQuote(kioskContext, idempotencyKey, request)
                    .onSuccess { response ->
                        call.respond(HttpStatusCode.OK, response)
                    }
                    .onFailure { error ->
                        when (error) {
                            is IllegalArgumentException -> call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Solicitud inválida")),
                            )
                            is IllegalStateException -> call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to (error.message ?: "Conflicto en estado del pedido")),
                            )
                            else -> call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Error interno")),
                            )
                        }
                    }
            }

            /**
             * Procesamiento de pago de pedido con apertura/cierre automático de caja y facturación fiscal.
             */
            post("/orders/{id}/pay") {
                val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post
                val orderId = call.parameters["id"]?.trim()
                if (orderId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id de pedido requerido"))
                    return@post
                }

                val request = try {
                    call.receive<KioskPaymentRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
                    return@post
                }

                kioskService.payOrder(kioskContext, orderId, request)
                    .onSuccess { response ->
                        if (response.status == "PAID_PENDING_INVOICE") {
                            call.respond(HttpStatusCode.Accepted, response)
                        } else {
                            call.respond(HttpStatusCode.OK, response)
                        }
                    }
                    .onFailure { error ->
                        when (error) {
                            is IllegalArgumentException -> call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Solicitud inválida")),
                            )
                            is IllegalStateException -> call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to (error.message ?: "Conflicto en estado del pedido")),
                            )
                            else -> call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Error interno")),
                            )
                        }
                    }
            }

            kioskYappyRoutes(kioskService)

            /**
             * Desbloqueo de modo kiosco con clave de administración (verificada en servidor con BCrypt).
             */
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
