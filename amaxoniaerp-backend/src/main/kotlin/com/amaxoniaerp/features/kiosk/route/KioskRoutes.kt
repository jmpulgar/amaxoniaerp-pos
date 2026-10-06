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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

private val kioskRoutesLogger = LoggerFactory.getLogger("com.amaxoniaerp.features.kiosk.route.KioskRoutes")

fun Route.kioskRoutes(kioskService: KioskService) {
    route("/api/v1/kiosk") {
        kioskPairingRoute(kioskService)

        authenticate {
            kioskConfigRoute(kioskService)
            kioskCatalogRoute(kioskService)
            kioskQuoteRoute(kioskService)
            kioskPaymentRoute(kioskService)
            kioskYappyRoutes(kioskService)
            kioskUnlockRoute(kioskService)
        }
    }
}

/**
 * Emparejamiento inicial de un dispositivo kiosco con código de un solo uso.
 */
private fun Route.kioskPairingRoute(kioskService: KioskService) {
    post("/pairing") {
        val request = call.receiveOrBadRequest<KioskPairingRequest>() ?: return@post

        kioskService
            .pairDevice(request)
            .onSuccess { response ->
                call.respond(HttpStatusCode.OK, response)
            }.onFailure { error ->
                when (error) {
                    is IllegalArgumentException ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to (error.message ?: "Solicitud inválida"),
                            ),
                        )
                    is KioskAuthenticationException ->
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf(
                                "error" to (error.message ?: "No autorizado"),
                            ),
                        )
                    else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (error.message ?: "Error interno")))
                }
            }
    }
}

/**
 * Configuración del kiosco con soporte de ETag (304 Not Modified).
 */
private fun Route.kioskConfigRoute(kioskService: KioskService) {
    get("/config") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@get
        val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)

        kioskService
            .getConfig(kioskContext)
            .onSuccess { (config, etag) ->
                call.respondWithEtag(ifNoneMatch, etag, config)
            }.onFailure { error ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (error.message ?: "Error al obtener configuración")),
                )
            }
    }
}

/**
 * Catálogo del kiosco filtrando departamentos con visible_pos = 1, precios nivel A y modificadores.
 */
private fun Route.kioskCatalogRoute(kioskService: KioskService) {
    get("/catalog") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@get
        val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)

        kioskService
            .getCatalog(kioskContext)
            .onSuccess { (catalog, etag) ->
                call.respondWithEtag(ifNoneMatch, etag, catalog)
            }.onFailure { error ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (error.message ?: "Error al obtener catálogo")),
                )
            }
    }
}

/**
 * Cotización de pedido del kiosco con validación de modificadores, cálculo en Money y numeración diaria.
 */
private fun Route.kioskQuoteRoute(kioskService: KioskService) {
    post("/orders/quote") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post
        val idempotencyKey = call.request.header("Idempotency-Key")?.trim()
        if (idempotencyKey.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Header Idempotency-Key es requerido"))
            return@post
        }

        val request = call.receiveOrBadRequest<KioskQuoteRequest>() ?: return@post

        kioskService
            .createQuote(kioskContext, idempotencyKey, request)
            .onSuccess { response ->
                call.respond(HttpStatusCode.OK, response)
            }.onFailure { error ->
                call.respondOrderError(error)
            }
    }
}

/**
 * Procesamiento de pago de pedido con apertura/cierre automático de caja y facturación fiscal.
 */
private fun Route.kioskPaymentRoute(kioskService: KioskService) {
    post("/orders/{id}/pay") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post
        val orderId = call.parameters["id"]?.trim()
        if (orderId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id de pedido requerido"))
            return@post
        }

        val request = call.receiveOrBadRequest<KioskPaymentRequest>() ?: return@post

        kioskService
            .payOrder(kioskContext, orderId, request)
            .onSuccess { response ->
                if (response.status == "PAID_PENDING_INVOICE") {
                    call.respond(HttpStatusCode.Accepted, response)
                } else {
                    call.respond(HttpStatusCode.OK, response)
                }
            }.onFailure { error ->
                call.respondOrderError(error)
            }
    }
}

/**
 * Desbloqueo de modo kiosco con clave de administración (verificada en servidor con BCrypt).
 */
private fun Route.kioskUnlockRoute(kioskService: KioskService) {
    post("/unlock") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post

        val request = call.receiveOrBadRequest<KioskUnlockRequest>() ?: return@post

        kioskService
            .unlock(kioskContext, request)
            .onSuccess {
                call.respond(HttpStatusCode.NoContent)
            }.onFailure { error ->
                when (error) {
                    is KioskRateLimitException ->
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            mapOf(
                                "error" to (error.message ?: "Límite de intentos excedido"),
                            ),
                        )
                    is KioskAuthenticationException ->
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf(
                                "error" to (error.message ?: "No autorizado"),
                            ),
                        )
                    is IllegalStateException ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to (error.message ?: "Error de configuración"),
                            ),
                        )
                    else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (error.message ?: "Error interno")))
                }
            }
    }
}

/**
 * Lee el cuerpo como [T]; si es inválido responde 400 "Cuerpo de solicitud inválido" y devuelve null.
 */
private suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(): T? =
    try {
        receive<T>()
    } catch (e: BadRequestException) {
        respondInvalidBody(e)
    } catch (e: ContentTransformationException) {
        respondInvalidBody(e)
    } catch (e: SerializationException) {
        respondInvalidBody(e)
    } catch (e: IllegalArgumentException) {
        respondInvalidBody(e)
    }

private suspend fun ApplicationCall.respondInvalidBody(cause: Exception): Nothing? {
    kioskRoutesLogger.warn("Cuerpo de solicitud inválido en {}: {}", request.uri, cause.message)
    respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
    return null
}

private fun etagMatches(
    ifNoneMatch: String?,
    etag: String,
): Boolean = ifNoneMatch != null && ifNoneMatch in setOf(etag, etag.trim('"'), "W/$etag")

private suspend inline fun <reified T : Any> ApplicationCall.respondWithEtag(
    ifNoneMatch: String?,
    etag: String,
    body: T,
) {
    response.headers.append(HttpHeaders.ETag, etag)
    if (etagMatches(ifNoneMatch, etag)) {
        respond(HttpStatusCode.NotModified)
    } else {
        respond(HttpStatusCode.OK, body)
    }
}

private suspend fun ApplicationCall.respondOrderError(error: Throwable) {
    when (error) {
        is IllegalArgumentException ->
            respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (error.message ?: "Solicitud inválida")),
            )
        is IllegalStateException ->
            respond(
                HttpStatusCode.Conflict,
                mapOf("error" to (error.message ?: "Conflicto en estado del pedido")),
            )
        else ->
            respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (error.message ?: "Error interno")),
            )
    }
}
