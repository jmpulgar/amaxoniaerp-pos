package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
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
    val handlers = SesionMesaHandlers(mesasRepository, sesionMesaRepository)

    authenticate {
        route("/api/pos/areas/{areaId}/mesas") {
            /**
             * Estados operativos derivados de todas las mesas activas del área.
             */
            get("estados") { handlers.estados(call) }

            route("{mesaId}/sesiones") {
                /**
                 * Abre una sesión operativa. El cuerpo define `cantidad_personas`.
                 */
                post { handlers.abrir(call) }

                /**
                 * Sesión activa actual de la mesa: `200` con `sesion=null` si no hay ninguna.
                 */
                get("activa") { handlers.activa(call) }

                route("{sesionId}") {
                    /**
                     * Cierra la sesión normalmente. Rechazado si tiene operaciones asociadas.
                     */
                    post("cerrar") { handlers.mutarCierre(call, cerrar = true) }

                    /**
                     * Anula la sesión. Solo si no tiene operaciones.
                     */
                    post("cancelar") { handlers.mutarCierre(call, cerrar = false) }
                }
            }
        }
    }
}

/**
 * Handlers de los endpoints de sesión de mesa. Validan contexto/parámetros, ejecutan
 * la operación del repositorio y mapean el resultado a HTTP.
 */
internal class SesionMesaHandlers(
    private val mesasRepository: MesasRepository,
    private val sesionMesaRepository: SesionMesaRepository,
) {
    private val log = LoggerFactory.getLogger("SesionMesaRouting")

    suspend fun estados(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run
        val areaId = call.requireAreaId() ?: return@run

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run

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
        log.debug("Estados de mesas respondidos. adminDb={} areaId={}", ctx.adminDb, areaId)
    }

    suspend fun abrir(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run
        val areaId = call.requireAreaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run

        val body =
            runCatching { call.receive<AbrirSesionRequest>() }.getOrElse { e ->
                if (e is Error) throw e
                log.debug("Cuerpo inválido al abrir sesión: {}", e.message)
                AbrirSesionRequest()
            }
        if (body.cantidadPersonas <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "La cantidad de personas debe ser mayor que cero"),
            )
            return@run
        }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run

        val result =
            sesionMesaRepository.abrir(
                database,
                com.amaxoniaerp.features.mesas.data.AbrirSesionScope(
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    usuarioId = ctx.userId,
                    cantidadPersonas = body.cantidadPersonas,
                ),
            )
        respondAbrir(call, result)
    }

    suspend fun activa(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run
        call.requireAreaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run

        val result = sesionMesaRepository.sesionActiva(database, mesaId)
        val sesion = (result as? SesionMesaResult.Found)?.sesion
        call.respond(HttpStatusCode.OK, SesionActivaResponse(success = true, sesion = sesion))
        log.debug("Sesión activa respondida. adminDb={} mesaId={}", ctx.adminDb, mesaId)
    }

    suspend fun mutarCierre(
        call: ApplicationCall,
        cerrar: Boolean,
    ) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run
        call.requireAreaId() ?: return@run
        call.requireMesaId() ?: return@run
        val sesionId = call.requireSesionId() ?: return@run

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run
        val result =
            if (cerrar) {
                sesionMesaRepository.cerrar(database, sesionId)
            } else {
                sesionMesaRepository.cancelar(database, sesionId)
            }
        respondMutacion(call, result)
    }

    private suspend fun respondAbrir(
        call: ApplicationCall,
        result: SesionMesaResult,
    ) {
        when (result) {
            is SesionMesaResult.Opened ->
                call.respond(HttpStatusCode.Created, AbrirSesionResponse(true, sesion = result.sesion))
            SesionMesaResult.SesionYaAbierta ->
                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_ALREADY_OPEN))
            SesionMesaResult.AreaNoPerteneceSucursal ->
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Área no encontrada en la sucursal de la caja"),
                )
            SesionMesaResult.MesaNoPerteneceArea ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_TABLE_AREA))
            SesionMesaResult.MesaInactiva ->
                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_TABLE_INACTIVE))
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
        result: SesionMesaResult,
    ) {
        when (result) {
            is SesionMesaResult.Closed ->
                call.respond(HttpStatusCode.OK, SesionMutacionResponse(true, sesion = result.sesion))
            is SesionMesaResult.Cancelled ->
                call.respond(HttpStatusCode.OK, SesionMutacionResponse(true, sesion = result.sesion))
            SesionMesaResult.SesionNoEncontrada ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_FOUND))
            SesionMesaResult.SesionYaFinalizada ->
                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_CLOSED))
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
}

// ============================================================
// Helpers de extracción de parámetros compartidos con Mesas
// ============================================================

internal suspend fun ApplicationCall.requireAreaId(): Int? = run {
    val v = parameters["areaId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de área es inválido"))
        return@run null
    }
    v
}

internal suspend fun ApplicationCall.requireMesaId(): Int? = run {
    val v = parameters["mesaId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de mesa es inválido"))
        return@run null
    }
    v
}

internal suspend fun ApplicationCall.requireSesionId(): Int? = run {
    val v = parameters["sesionId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de sesión es inválido"))
        return@run null
    }
    v
}
