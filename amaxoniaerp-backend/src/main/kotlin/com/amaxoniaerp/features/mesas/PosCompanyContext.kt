package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.tenant.requireUserId
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.domain.CajaScopeResult
import com.amaxoniaerp.features.mesas.domain.CajaSucursalScope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.Database

/**
 * Helpers comunes de contexto POS para los routes de áreas/mesas y sesiones de mesa. Los
 * dos routings los reutilizan en lugar de duplicarlos, ya que las reglas de autenticación,
 * acceso a caja y derivación de sucursal son idénticas.
 */
internal data class PosCompanyContext(
    val countryCode: String,
    val adminDb: String,
    val userId: Int,
)

/**
 * Regla compartida con el resto de `/api/pos`: token de empresa, `admin_db`/`country_code`
 * tomados del JWT firmado, nunca del cliente. Delega en el seam canónico de tenant
 * y añade el claim `user_id`.
 */
internal suspend fun ApplicationCall.resolvePosContext(): PosCompanyContext? = run {
    val ctx = resolveCompanyRequestContext() ?: return@run null
    val userId = ctx.requireUserId(this) ?: return@run null
    PosCompanyContext(countryCode = ctx.countryCode, adminDb = ctx.adminDb, userId = userId)
}

/** Lee `cajaId` del query string y responde 400 si no viene. */
internal suspend fun ApplicationCall.requireCajaId(): String? =
    request.queryParameters["cajaId"]?.takeIf { it.isNotBlank() }
        ?: run {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "El parámetro cajaId es requerido"))
            null
        }

/**
 * Valida que el usuario tenga acceso a la caja activa y deriva su sucursal. Las respuestas
 * de error (404/403/409) se entregan directamente al cliente con el cuerpo apropiado.
 */
internal suspend fun ApplicationCall.resolveScopeOrRespond(
    mesasRepository: MesasRepository,
    database: Database,
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
