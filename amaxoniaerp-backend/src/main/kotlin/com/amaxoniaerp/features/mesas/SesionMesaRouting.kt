package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.mesas.data.AbrirSesionScope
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.domain.AbrirSesionRequest
import com.amaxoniaerp.features.mesas.domain.AbrirSesionResponse
import com.amaxoniaerp.features.mesas.domain.MesasEstadosListResponse
import com.amaxoniaerp.features.mesas.domain.SesionActivaResponse
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import com.amaxoniaerp.features.mesas.domain.SesionMutacionResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private const val ERR_UNEXPECTED = "Respuesta inesperada"
private const val ERR_SESSION_ALREADY_OPEN = "La mesa ya tiene una sesión activa"
private const val ERR_TABLE_AREA = "Mesa no encontrada en el área"
private const val ERR_TABLE_INACTIVE = "La mesa no está activa"
private const val ERR_SESSION_NOT_FOUND = "Sesión no encontrada"
private const val ERR_SESSION_CLOSED = "La sesión ya no está abierta"

/**
 * Sesiones operativas de mesa para el POS.
 *
 * Endpoints:
 * - `GET  /api/pos/areas/{areaId}/mesas/estados?cajaId=`                                   estados derivados.
 * - `POST /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones?cajaId=`                          abrir sesión.
 * - `GET  /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/activa?cajaId=`                   sesión activa.
 * - `POST /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/cerrar?cajaId=`        cerrar sesión.
 * - `POST /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/cancelar?cajaId=`      cancelar sesión.
 *
 * Cada endpoint valida:
 * - usuario autenticado con token `company`;
 * - cajaId presente y asignada al usuario (vía [MesasRepository.resolveCajaScope]);
 * - sucursal derivada en servidor desde la caja (nunca se acepta desde el cliente);
 * - el areaId pertenece a la sucursal de esa caja;
 * - cada parámetro numérico es positivo.
 *
 * Las validaciones de mesa/área/sesión activa se hacen dentro del repositorio en una sola
 * transacción para evitar race conditions entre cajas concurrentes.
 */
fun Route.sesionMesaRouting(
    mesasRepository: MesasRepository,
    sesionMesaRepository: SesionMesaRepository,
) {
    val log = LoggerFactory.getLogger("SesionMesaRouting")

    authenticate {
        route("/api/pos/areas/{areaId}/mesas") {
            /**
             * Estados operativos derivados de todas las mesas activas del área.
             */
            get("estados") {
                val ctx = call.resolvePosContext() ?: return@get
                val cajaId = call.requireCajaId() ?: return@get
                val areaId = call.requireAreaId() ?: return@get

                try {
                    val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                    val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@get

                    val result = sesionMesaRepository.listarEstados(database, scope.sucursalId, areaId)
                    when (result) {
                        is SesionMesaResult.States ->
                            call.respond(
                                HttpStatusCode.OK,
                                MesasEstadosListResponse(success = true, areaId = areaId, data = result.estados),
                            )

                        SesionMesaResult.AreaNoPerteneceSucursal ->
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Área no encontrada en la sucursal de la caja"),
                            )

                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_UNEXPECTED))
                    }
                } catch (e: Exception) {
                    log.error("Error listando estados. adminDb={} cajaId={} areaId={}", ctx.adminDb, cajaId, areaId, e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "No se pudieron consultar los estados de las mesas"),
                    )
                }
            }

            route("{mesaId}/sesiones") {
                /**
                 * Abre una sesión operativa. El cuerpo define `cantidad_personas`.
                 */
                post {
                    val ctx = call.resolvePosContext() ?: return@post
                    val cajaId = call.requireCajaId() ?: return@post
                    val areaId = call.requireAreaId() ?: return@post
                    val mesaId = call.requireMesaId() ?: return@post

                    val body =
                        try {
                            call.receive<AbrirSesionRequest>()
                        } catch (e: Exception) {
                            log.debug("Cuerpo inválido al abrir sesión: {}", e.message)
                            AbrirSesionRequest()
                        }
                    if (body.cantidadPersonas <= 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "La cantidad de personas debe ser mayor que cero"),
                        )
                    }

                    try {
                        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                        val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@post

                        val result =
                            sesionMesaRepository.abrir(
                                database,
                                AbrirSesionScope(
                                    cajaId = cajaId,
                                    areaId = areaId,
                                    mesaId = mesaId,
                                    usuarioId = ctx.userId,
                                    cantidadPersonas = body.cantidadPersonas,
                                ),
                            )
                        respondAbrir(call, log, result)
                    } catch (e: Exception) {
                        log.error(
                            "Error abriendo sesión. adminDb={} cajaId={} areaId={} mesaId={}",
                            ctx.adminDb,
                            cajaId,
                            areaId,
                            mesaId,
                            e,
                        )
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "No se pudo abrir la sesión de mesa"),
                        )
                    }
                }

                /**
                 * Sesión activa actual de la mesa: `200` con `sesion=null` si no hay ninguna.
                 */
                get("activa") {
                    val ctx = call.resolvePosContext() ?: return@get
                    val cajaId = call.requireCajaId() ?: return@get
                    call.requireAreaId() ?: return@get
                    val mesaId = call.requireMesaId() ?: return@get

                    try {
                        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                        val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@get

                        val result = sesionMesaRepository.sesionActiva(database, mesaId)
                        val sesion = (result as? SesionMesaResult.Found)?.sesion
                        call.respond(HttpStatusCode.OK, SesionActivaResponse(success = true, sesion = sesion))
                    } catch (e: Exception) {
                        log.error(
                            "Error consultando sesión activa. adminDb={} cajaId={} mesaId={}",
                            ctx.adminDb,
                            cajaId,
                            mesaId,
                            e,
                        )
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "No se pudo consultar la sesión activa"),
                        )
                    }
                }

                route("{sesionId}") {
                    /**
                     * Cierra la sesión normalmente. Rechazado si tiene operaciones asociadas.
                     */
                    post("cerrar") {
                        val ctx = call.resolvePosContext() ?: return@post
                        val cajaId = call.requireCajaId() ?: return@post
                        call.requireAreaId() ?: return@post
                        call.requireMesaId() ?: return@post
                        val sesionId = call.requireSesionId() ?: return@post

                        try {
                            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                            call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@post
                            val result = sesionMesaRepository.cerrar(database, sesionId)
                            respondMutacion(call, log, result)
                        } catch (e: Exception) {
                            log.error(
                                "Error cerrando sesión. adminDb={} cajaId={} sesionId={}",
                                ctx.adminDb,
                                cajaId,
                                sesionId,
                                e,
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo cerrar la sesión de mesa"),
                            )
                        }
                    }

                    /**
                     * Anula la sesión. Solo si no tiene operaciones.
                     */
                    post("cancelar") {
                        val ctx = call.resolvePosContext() ?: return@post
                        val cajaId = call.requireCajaId() ?: return@post
                        call.requireAreaId() ?: return@post
                        call.requireMesaId() ?: return@post
                        val sesionId = call.requireSesionId() ?: return@post

                        try {
                            val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                            call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@post
                            val result = sesionMesaRepository.cancelar(database, sesionId)
                            respondMutacion(call, log, result)
                        } catch (e: Exception) {
                            log.error(
                                "Error cancelando sesión. adminDb={} cajaId={} sesionId={}",
                                ctx.adminDb,
                                cajaId,
                                sesionId,
                                e,
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo cancelar la sesión de mesa"),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Helpers de respuesta por tipo de resultado
// ============================================================

private suspend fun respondAbrir(
    call: ApplicationCall,
    log: org.slf4j.Logger,
    result: SesionMesaResult,
) {
    when (result) {
        is SesionMesaResult.Opened -> call.respond(HttpStatusCode.Created, AbrirSesionResponse(true, sesion = result.sesion))
        SesionMesaResult.SesionYaAbierta -> call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_ALREADY_OPEN))
        SesionMesaResult.AreaNoPerteneceSucursal ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Área no encontrada en la sucursal de la caja"),
            )
        SesionMesaResult.MesaNoPerteneceArea -> call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_TABLE_AREA))
        SesionMesaResult.MesaInactiva -> call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_TABLE_INACTIVE))
        SesionMesaResult.CantidadPersonasInvalida ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "La cantidad de personas es inválida"))
        else -> {
            log.warn("Respuesta no Abierta al abrir sesión: {}", result)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo abrir la sesión"))
        }
    }
}

private suspend fun respondMutacion(
    call: ApplicationCall,
    log: org.slf4j.Logger,
    result: SesionMesaResult,
) {
    when (result) {
        is SesionMesaResult.Closed -> call.respond(HttpStatusCode.OK, SesionMutacionResponse(true, sesion = result.sesion))
        is SesionMesaResult.Cancelled -> call.respond(HttpStatusCode.OK, SesionMutacionResponse(true, sesion = result.sesion))
        SesionMesaResult.SesionNoEncontrada -> call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_FOUND))
        SesionMesaResult.SesionYaFinalizada -> call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_CLOSED))
        SesionMesaResult.SesionConOperaciones ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "La sesión tiene operaciones asociadas y no se puede cerrar"),
            )
        else -> {
            log.warn("Respuesta no esperada al mutar sesión: {}", result)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo modificar la sesión"))
        }
    }
}

// ============================================================
// Helpers de extracción de parámetros compartidos con Mesas
// ============================================================

internal suspend fun ApplicationCall.requireAreaId(): Int? {
    val v = parameters["areaId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de área es inválido"))
        return null
    }
    return v
}

internal suspend fun ApplicationCall.requireMesaId(): Int? {
    val v = parameters["mesaId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de mesa es inválido"))
        return null
    }
    return v
}

internal suspend fun ApplicationCall.requireSesionId(): Int? {
    val v = parameters["sesionId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de sesión es inválido"))
        return null
    }
    return v
}
