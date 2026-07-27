package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.domain.AreasListResponse
import com.amaxoniaerp.features.mesas.domain.CajaScopeResult
import com.amaxoniaerp.features.mesas.domain.CajaSucursalScope
import com.amaxoniaerp.features.mesas.domain.MesasListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

/**
 * Áreas y mesas para el POS móvil (solo lectura).
 *
 * - `GET /api/pos/areas?cajaId={id}`
 * - `GET /api/pos/areas/{areaId}/mesas?cajaId={id}`
 *
 * La sucursal se deriva en servidor desde la caja; el cliente no puede indicarla.
 */
fun Route.mesasRouting(mesasRepository: MesasRepository) {
    val log = LoggerFactory.getLogger("MesasRouting")

    authenticate {
        route("/api/pos/areas") {
            get {
                val ctx = call.resolvePosContext() ?: return@get
                val cajaId = call.requireCajaId() ?: return@get

                try {
                    val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                    val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@get
                    val areas = mesasRepository.listAreas(database, scope.sucursalId)
                    call.respond(
                        HttpStatusCode.OK,
                        AreasListResponse(success = true, sucursalId = scope.sucursalId, data = areas),
                    )
                } catch (e: Exception) {
                    log.error("Error listando áreas. adminDb={} cajaId={}", ctx.adminDb, cajaId, e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "No se pudieron consultar las áreas"),
                    )
                }
            }

            get("/{areaId}/mesas") {
                val ctx = call.resolvePosContext() ?: return@get
                val cajaId = call.requireCajaId() ?: return@get

                val areaId = call.parameters["areaId"]?.toIntOrNull()
                if (areaId == null || areaId <= 0) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El identificador de área es inválido"),
                    )
                }

                try {
                    val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                    val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@get
                    val plan = mesasRepository.listMesas(database, scope.sucursalId, areaId)

                    if (plan == null) {
                        log.warn(
                            "Área fuera de la sucursal activa. adminDb={} cajaId={} sucursalId={} areaId={}",
                            ctx.adminDb,
                            cajaId,
                            scope.sucursalId,
                            areaId,
                        )
                        return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to AREA_NOT_FOUND),
                        )
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        MesasListResponse(
                            success = true,
                            areaId = areaId,
                            lienzo = plan.lienzo,
                            imagenUrl = plan.imagenUrl,
                            data = plan.mesas,
                        ),
                    )
                } catch (e: Exception) {
                    log.error(
                        "Error listando mesas. adminDb={} cajaId={} areaId={}",
                        ctx.adminDb,
                        cajaId,
                        areaId,
                        e,
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "No se pudieron consultar las mesas"),
                    )
                }
            }
        }
    }
}

private const val AREA_NOT_FOUND = "Área no encontrada"

private data class PosCompanyContext(
    val countryCode: String,
    val adminDb: String,
    val userId: Int,
)

/**
 * Misma regla que el resto de `/api/pos`: token de empresa y `admin_db`/`country_code` tomados
 * del JWT firmado, nunca del cliente.
 */
private suspend fun ApplicationCall.resolvePosContext(): PosCompanyContext? {
    val principal =
        principal<JWTPrincipal>()
            ?: run {
                respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido"))
                return null
            }

    if (principal.payload.getClaim("token_type").asString() != "company") {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere token de empresa"))
        return null
    }

    val countryCode =
        principal.getCountryCode()
            ?: run {
                respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta country_code en token"))
                return null
            }

    val adminDb =
        principal.getAdminDb()?.takeIf { it.isNotBlank() }
            ?: run {
                respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta admin_db en token"))
                return null
            }

    val userId =
        principal.payload.getClaim("user_id").asInt()
            ?: run {
                respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido: falta user_id"))
                return null
            }

    return PosCompanyContext(countryCode = countryCode, adminDb = adminDb, userId = userId)
}

private suspend fun ApplicationCall.requireCajaId(): String? =
    request.queryParameters["cajaId"]?.takeIf { it.isNotBlank() }
        ?: run {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro cajaId es requerido"))
            null
        }

private suspend fun ApplicationCall.resolveScopeOrRespond(
    mesasRepository: MesasRepository,
    database: org.jetbrains.exposed.sql.Database,
    ctx: PosCompanyContext,
    cajaId: String,
): CajaSucursalScope? =
    when (val result = mesasRepository.resolveCajaScope(database, ctx.userId, cajaId)) {
        is CajaScopeResult.Allowed -> result.scope
        CajaScopeResult.CajaNotFound -> {
            respond(HttpStatusCode.NotFound, mapOf("error" to "Caja no encontrada"))
            null
        }
        CajaScopeResult.AccessDenied -> {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "La caja no pertenece al usuario"))
            null
        }
        CajaScopeResult.SucursalNotAssigned -> {
            respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "La caja activa no tiene una sucursal asignada"),
            )
            null
        }
    }
