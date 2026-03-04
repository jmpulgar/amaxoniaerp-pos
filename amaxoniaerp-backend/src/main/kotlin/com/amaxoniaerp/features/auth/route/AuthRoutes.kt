package com.amaxoniaerp.features.auth.route

import com.amaxoniaerp.features.auth.domain.AuthService
import com.amaxoniaerp.features.auth.domain.AuthenticationException
import com.amaxoniaerp.features.auth.domain.AuthorizationException
import com.amaxoniaerp.features.auth.domain.LoginRequest
import com.amaxoniaerp.features.auth.domain.NotFoundException
import com.amaxoniaerp.features.companies.domain.CompanySelectRequest
import com.amaxoniaerp.features.companies.domain.CompanyService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Rutas de autenticación Multi-Tenant con arquitectura Two-Tier.
 *
 * Endpoints:
 * - POST /auth/login: Requiere header X-Country-Code
 * - POST /auth/company: Requiere JWT identity token con claim country_code
 */
fun Route.authRoutes(authService: AuthService, companyService: CompanyService) {
    route("/auth") {
        /**
         * Login. Solo Venezuela (VE) y Panamá (PA).
         * Header: X-Country-Code: VE | PA
         * Body: { "username": "...", "password": "..." }
         */
        post("/login") {
            val request = call.receive<LoginRequest>()

            val countryCode = call.request.headers["X-Country-Code"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta header X-Country-Code. Valores válidos: VE, PA")
                )

            val validCountries = setOf("VE", "PA")
            if (countryCode.uppercase() !in validCountries) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "País no soportado: $countryCode. Use: VE, PA")
                )
            }

            try {
                val response = authService.login(
                    username = request.username,
                    password = request.password,
                    countryCode = countryCode.uppercase()
                )
                call.respond(response)
            } catch (ex: AuthenticationException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to (ex.message ?: "Credenciales inválidas"))
                )
            }
        }

        /**
         * Selección de empresa.
         *
         * Requiere: JWT identity token
         * Extrae country_code del JWT
         */
        authenticate {
            post("/company") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Token inválido o no proporcionado")
                    )

                val userId = principal.payload.getClaim("user_id").asInt()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Token inválido: falta user_id")
                    )

                // Extraer país del JWT (agregado en login)
                val countryCode = principal.payload.getClaim("country_code")?.asString() ?: "VE"

                val request = call.receive<CompanySelectRequest>()

                try {
                    val response = companyService.selectCompany(
                        userId = userId,
                        companyId = request.companyId,
                        countryCode = countryCode
                    )
                    call.respond(response)
                } catch (ex: AuthenticationException) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to (ex.message ?: "Token inválido"))
                    )
                } catch (ex: AuthorizationException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to (ex.message ?: "Acceso denegado"))
                    )
                } catch (ex: NotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to (ex.message ?: "No encontrado"))
                    )
                }
            }
        }
    }
}

// Extensiones útiles para extraer claims del JWT

fun JWTPrincipal.getCountryCode(): String? {
    return payload.getClaim("country_code")?.asString()
}

fun JWTPrincipal.getSchemaType(): String? {
    return payload.getClaim("schema_type")?.asString()
}

fun JWTPrincipal.getAdminDb(): String? {
    return payload.getClaim("admin_db")?.asString()
}
