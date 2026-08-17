package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.domain.AreasListResponse
import com.amaxoniaerp.features.mesas.domain.MesasListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private const val AREA_NOT_FOUND = "Área no encontrada"

/**
 * Áreas y mesas para el POS móvil (solo lectura).
 *
 * - `GET /api/pos/areas?cajaId={id}`
 * - `GET /api/pos/areas/{areaId}/mesas?cajaId={id}`
 *
 * La sucursal se deriva en servidor desde la caja; el cliente no puede indicarla.
 */
fun Route.mesasRouting(mesasRepository: MesasRepository) {
    val handlers = MesasHandlers(mesasRepository)

    authenticate {
        route("/api/pos/areas") {
            get { handlers.listarAreas(call) }
            get("/{areaId}/mesas") { handlers.listarMesas(call) }
        }
    }
}

/**
 * Handlers de los endpoints de áreas y mesas (solo lectura).
 */
internal class MesasHandlers(
    private val mesasRepository: MesasRepository,
) {
    private val log = LoggerFactory.getLogger("MesasRouting")

    suspend fun listarAreas(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run
        val areas = mesasRepository.listAreas(database, scope.sucursalId)
        call.respond(
            HttpStatusCode.OK,
            AreasListResponse(success = true, sucursalId = scope.sucursalId, data = areas),
        )
    }

    suspend fun listarMesas(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        val cajaId = call.requireCajaId() ?: return@run

        val areaId = call.parameters["areaId"]?.toIntOrNull()
        if (areaId == null || areaId <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "El identificador de área es inválido"),
            )
            return@run
        }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val scope = call.resolveScopeOrRespond(mesasRepository, database, ctx, cajaId) ?: return@run
        val plan = mesasRepository.listMesas(database, scope.sucursalId, areaId)

        if (plan == null) {
            log.warn(
                "Área fuera de la sucursal activa. adminDb={} cajaId={} sucursalId={} areaId={}",
                ctx.adminDb,
                cajaId,
                scope.sucursalId,
                areaId,
            )
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to AREA_NOT_FOUND),
            )
            return@run
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
    }
}

// resolvePosContext, requireCajaId y resolveScopeOrRespond viven en PosRoutingCommon.kt
// y son `internal` para compartirlos con SesionMesaRouting sin duplicar las reglas
// de autenticación, derivación de sucursal y acceso a caja.
