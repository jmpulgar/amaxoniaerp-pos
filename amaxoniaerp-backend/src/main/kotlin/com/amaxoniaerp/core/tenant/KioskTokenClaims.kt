package com.amaxoniaerp.core.tenant

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

data class KioskTokenClaims(
    val deviceId: String,
    val countryCode: String,
    val companyDb: String,
    val role: String?,
    val tokenType: String?,
)

fun ApplicationCall.extractKioskTokenClaims(): KioskTokenClaims? {
    val principal = principal<JWTPrincipal>() ?: return null
    val role = principal.payload.getClaim("role")?.asString()
    val tokenType = principal.payload.getClaim("token_type")?.asString()
    val deviceId = principal.payload.getClaim("device_id")?.asString()
    val countryCode = principal.payload.getClaim("country_code")?.asString()?.uppercase()
    val companyDb = principal.payload.getClaim("admin_db")?.asString()
        ?: principal.payload.getClaim("company_db")?.asString()

    return KioskTokenClaims(
        deviceId = deviceId ?: "",
        countryCode = countryCode ?: "",
        companyDb = companyDb ?: "",
        role = role,
        tokenType = tokenType,
    )
}

fun ApplicationCall.hasKioskPrincipal(): Boolean = principal<JWTPrincipal>() != null
