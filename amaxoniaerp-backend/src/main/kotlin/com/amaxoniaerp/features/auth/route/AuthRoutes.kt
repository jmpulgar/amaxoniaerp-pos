package com.amaxoniaerp.features.auth.route

import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.auth.domain.LoginRequest
import com.amaxoniaerp.features.companies.domain.CompanySelectRequest
import com.amaxoniaerp.features.companies.domain.CompanyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val VALID_COUNTRIES = setOf("VE", "PA")

/**
 * Rutas de autenticación Multi-Tenant con arquitectura Two-Tier.
 *
 * Endpoints:
 * - POST /auth/login: Requiere header X-Country-Code
 * - POST /auth/company: Requiere JWT identity token con claim country_code
 */
fun Route.authRoutes(
    authService: AuthService,
    companyService: CompanyService,
) {
    route("/auth") {
        /**
         * Login. Solo Venezuela (VE) y Panamá (PA).
         * Header: X-Country-Code: VE | PA
         * Body: { "username": "...", "password": "..." }
         */
        post("/login") { login(call, authService) }

        /**
         * Selección de empresa.
         *
         * Requiere: JWT identity token
         * Extrae country_code del JWT
         */
        authenticate {
            post("/company") { selectCompany(call, companyService) }
        }
    }
}

private suspend fun login(
    call: ApplicationCall,
    authService: AuthService,
) = run {
    val request = call.receive<LoginRequest>()

    val countryCode = call.request.headers["X-Country-Code"]
    if (countryCode == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Falta header X-Country-Code. Valores válidos: VE, PA"),
        )
        return@run
    }

    if (countryCode.uppercase() !in VALID_COUNTRIES) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "País no soportado: $countryCode. Use: VE, PA"),
        )
        return@run
    }

    val response =
        authService.login(
            username = request.username,
            password = request.password,
            countryCode = countryCode.uppercase(),
        )
    call.respond(response)
}

private suspend fun selectCompany(
    call: ApplicationCall,
    companyService: CompanyService,
) = run {
    val principal = call.principal<JWTPrincipal>()
    if (principal == null) {
        call.respond(
            HttpStatusCode.Unauthorized,
            mapOf("error" to "Token inválido o no proporcionado"),
        )
        return@run
    }

    val userId = principal.payload.getClaim("user_id").asInt()
    if (userId == null) {
        call.respond(
            HttpStatusCode.Unauthorized,
            mapOf("error" to "Token inválido: falta user_id"),
        )
        return@run
    }

    // Extraer país del JWT (agregado en login)
    val countryCode = principal.payload.getClaim("country_code")?.asString() ?: "VE"

    val request = call.receive<CompanySelectRequest>()

    val response =
        companyService.selectCompany(
            userId = userId,
            companyId = request.companyId,
            countryCode = countryCode,
        )
    call.respond(response)
}

// Las extensiones de claims del JWT (getCountryCode/getSchemaType/getAdminDb)
// viven en `com.amaxoniaerp.core.tenant` para que el seam de tenant no dependa
// de las features.
