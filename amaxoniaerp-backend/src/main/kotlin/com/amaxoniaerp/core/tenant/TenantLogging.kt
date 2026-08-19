package com.amaxoniaerp.core.tenant

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

/**
 * Contexto de tenant seguro para logging (MDC): sólo el país del token. Nunca
 * expone `admin_db`, nombre de empresa ni claims sensibles en los logs.
 */
fun ApplicationCall.tenantLogContext(): Map<String, String> {
    val country =
        principal<JWTPrincipal>()
            ?.payload
            ?.getClaim("country_code")
            ?.asString()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
    return mapOf("country" to country)
}
