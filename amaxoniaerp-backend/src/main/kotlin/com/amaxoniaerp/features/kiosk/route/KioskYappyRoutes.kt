package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.kiosk.application.KioskNotEnabledException
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.KioskYappyNotConfiguredException
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyUpstreamException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val yappyRoutesLogger = LoggerFactory.getLogger("com.amaxoniaerp.features.kiosk.route.KioskYappyRoutes")

/**
 * Cobro con QR Yappy de un pedido de kiosco. Debe registrarse dentro de `authenticate`
 * (token de empresa + headers X-Kiosk-Caja / X-Kiosk-Prefix).
 *
 * - POST   /orders/{id}/yappy                  genera el QR del total cotizado.
 * - GET    /orders/{id}/yappy/{transactionId}  consulta el estado normalizado.
 * - DELETE /orders/{id}/yappy/{transactionId}  anula best-effort; siempre 204.
 */
internal fun Route.kioskYappyRoutes(kioskService: KioskService) {
    post("/orders/{id}/yappy") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@post
        val orderId = call.requiredPathParameter("id", "id de pedido requerido") ?: return@post

        kioskService
            .createYappyQr(kioskContext, orderId)
            .onSuccess { response -> call.respond(HttpStatusCode.OK, response) }
            .onFailure { error -> call.respondYappyError(error) }
    }

    get("/orders/{id}/yappy/{transactionId}") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@get
        val orderId = call.requiredPathParameter("id", "id de pedido requerido") ?: return@get
        val transactionId = call.requiredPathParameter("transactionId", "transactionId requerido") ?: return@get

        kioskService
            .getYappyStatus(kioskContext, orderId, transactionId)
            .onSuccess { response -> call.respond(HttpStatusCode.OK, response) }
            .onFailure { error -> call.respondYappyError(error) }
    }

    delete("/orders/{id}/yappy/{transactionId}") {
        val kioskContext = call.resolveKioskRequestContext(kioskService) ?: return@delete
        val orderId = call.parameters["id"]?.trim().orEmpty()
        val transactionId = call.parameters["transactionId"]?.trim().orEmpty()

        kioskService
            .cancelYappy(kioskContext, orderId, transactionId)
            .onFailure { error ->
                yappyRoutesLogger.warn(
                    "[YAPPY] Anulación best-effort falló para pedido {} transactionId={}: {}",
                    orderId,
                    transactionId,
                    error.javaClass.simpleName,
                )
            }
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun ApplicationCall.requiredPathParameter(
    name: String,
    errorMessage: String,
): String? {
    val value = parameters[name]?.trim()
    if (value.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
        return null
    }
    return value
}

private suspend fun ApplicationCall.respondYappyError(error: Throwable) {
    val (status, message) =
        when (error) {
            is IllegalArgumentException -> HttpStatusCode.BadRequest to (error.message ?: "Solicitud inválida")
            is IllegalStateException -> HttpStatusCode.Conflict to (error.message ?: "Conflicto en estado del pedido")
            is KioskYappyNotConfiguredException -> HttpStatusCode.ServiceUnavailable to (error.message ?: "Yappy no configurado")
            is KioskNotEnabledException -> HttpStatusCode.ServiceUnavailable to (error.message ?: "Kiosco no habilitado")
            is YappyUpstreamException -> HttpStatusCode.BadGateway to (error.message ?: "Error al comunicarse con Yappy")
            else -> {
                yappyRoutesLogger.error("[YAPPY] Error inesperado", error)
                HttpStatusCode.InternalServerError to "Error interno"
            }
        }
    respond(status, mapOf("error" to message))
}
