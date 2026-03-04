package com.amaxoniaerp.features.auth.domain

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.data.AuthRepository
import com.amaxoniaerp.features.companies.data.CompanyRepository
import com.amaxoniaerp.features.companies.domain.CompanyResponse
import com.amaxoniaerp.features.companies.domain.parseCompanyCodes
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

/**
 * Servicio de autenticación Multi-Tenant.
 */
class AuthService(
    private val jwtConfig: com.amaxoniaerp.JwtConfig,
) {
    /**
     * Autentica un usuario contra la BD de configuración del país.
     */
    suspend fun login(username: String, password: String, countryCode: String): LoginResponse {
        // Obtener BD de configuración del país
        val configDatabase = DatabaseManager.getConfigDatabase(countryCode)

        // Crear repositorios
        val authRepository = AuthRepository(configDatabase)
        val companyRepository = CompanyRepository(configDatabase)

        // Autenticar usuario
        val user = authRepository.authenticate(username, password)
            ?: throw AuthenticationException("Credenciales inválidas")

        // Cargar empresas disponibles
        val companyCodes = parseCompanyCodes(user.companyCodesRaw)
        val companies = if (companyCodes.isEmpty()) {
            emptyList()
        } else {
            companyRepository.loadCompanies(companyCodes)
        }

        // Crear token con metadata del país
        val token = createIdentityToken(user, countryCode)

        return LoginResponse(
            token = token,
            user = UserResponse(
                id = user.id,
                username = user.username,
                role = user.role,
            ),
            companies = companies,
            countryCode = countryCode,
            schemaType = getSchemaTypeForCountry(countryCode)
        )
    }

    private fun createIdentityToken(user: UserRecord, countryCode: String): String {
        return JWT.create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "identity")
            .withClaim("user_id", user.id)
            .withClaim("username", user.username)
            .withClaim("role", user.role ?: "")
            .withClaim("level_id", user.levelId ?: 0)
            .withClaim("country_code", countryCode)
            .withClaim("schema_type", getSchemaTypeForCountry(countryCode))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }

    private fun getSchemaTypeForCountry(countryCode: String): String = when (countryCode.uppercase()) {
        "VE" -> "TYPE_B"
        else -> "TYPE_A" // PA
    }
}
