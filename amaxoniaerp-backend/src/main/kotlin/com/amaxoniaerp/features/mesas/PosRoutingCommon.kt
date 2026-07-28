package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.domain.CajaScopeResult
import com.amaxoniaerp.features.mesas.domain.CajaSucursalScope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
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
 * tomados del JWT firmado, nunca del cliente.
 */
internal suspend fun ApplicationCall.resolvePosContext(): PosCompanyContext? {
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
