package com.amaxoniaerp.core.tenant

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

/** Claim `country_code` del JWT. */
fun JWTPrincipal.getCountryCode(): String? = payload.getClaim("country_code")?.asString()

/** Claim `schema_type` del JWT. */
fun JWTPrincipal.getSchemaType(): String? = payload.getClaim("schema_type")?.asString()

/** Claim `admin_db` del JWT. */
fun JWTPrincipal.getAdminDb(): String? = payload.getClaim("admin_db")?.asString()

private const val ERR_TOKEN_INVALID = "Token inválido"
private const val ERR_COMPANY_TOKEN_REQUIRED = "Se requiere token de empresa"
private const val ERR_COUNTRY_MISSING = "Falta country_code en token"
private const val ERR_ADMIN_DB_MISSING = "Falta admin_db en token"
private const val ERR_USER_ID_MISSING = "Token inválido: falta user_id"
private const val ERR_COMPANY_DB_HEADER_MISSING = "Company-DB header is missing"
private const val ERR_COMPANY_DB_MISMATCH = "Company-DB no coincide con la empresa autenticada"

/**
 * Seam canónico de resolución de tenant/empresa por request.
 *
 * Regla compartida por todo `/api`: usuario autenticado con token `company`,
 * `country_code` y `admin_db` tomados del JWT firmado, nunca del cliente.
 * Las features NO deben duplicar esta resolución: extienden este contexto
 * con los claims/headers adicionales que necesiten.
 */
data class CompanyRequestContext(
    val countryCode: String,
    val adminDb: String,
    val principal: JWTPrincipal,
)

/**
 * Resuelve el contexto de empresa desde el JWT de la petición. Responde
 * 401/403/400 directamente cuando falta algún dato y devuelve `null`.
 */
suspend fun ApplicationCall.resolveCompanyRequestContext(): CompanyRequestContext? =
    run {
        val principal = principal<JWTPrincipal>()
        if (principal == null) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to ERR_TOKEN_INVALID))
            return@run null
        }
        if (principal.payload.getClaim("token_type").asString() != "company") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to ERR_COMPANY_TOKEN_REQUIRED))
            return@run null
        }
        val countryCode = principal.getCountryCode()
        if (countryCode == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_COUNTRY_MISSING))
            return@run null
        }
        val adminDb = principal.getAdminDb()
        if (adminDb == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_ADMIN_DB_MISSING))
            return@run null
        }
        CompanyRequestContext(countryCode = countryCode, adminDb = adminDb, principal = principal)
    }

/** Requiere el claim `user_id` del token de empresa. */
suspend fun CompanyRequestContext.requireUserId(call: ApplicationCall): Int? =
    run {
        val userId = principal.payload.getClaim("user_id").asInt()
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to ERR_USER_ID_MISSING))
            return@run null
        }
        userId
    }

/**
 * Requiere el header `Company-DB` y valida que coincida con el `admin_db`
 * autenticado (defensa ante confusiones de tenant).
 */
suspend fun CompanyRequestContext.requireCompanyDbHeader(call: ApplicationCall): String? =
    run {
        val companyDbHeader = call.request.headers["Company-DB"]
        if (companyDbHeader.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_COMPANY_DB_HEADER_MISSING))
            return@run null
        }
        if (!companyDbHeader.equals(adminDb, ignoreCase = true)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to ERR_COMPANY_DB_MISMATCH))
            return@run null
        }
        companyDbHeader
    }
