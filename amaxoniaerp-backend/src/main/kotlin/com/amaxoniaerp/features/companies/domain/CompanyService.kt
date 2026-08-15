package com.amaxoniaerp.features.companies.domain

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.data.AuthRepository
import com.amaxoniaerp.features.auth.domain.AuthenticationException
import com.amaxoniaerp.features.auth.domain.AuthorizationException
import com.amaxoniaerp.features.auth.domain.NotFoundException
import com.amaxoniaerp.features.companies.data.CompanyRepository
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

/**
 * Servicio de selección de empresa Multi-Tenant.
 */
class CompanyService(
    private val jwtConfig: JwtConfig,
) {
    /**
     * Selecciona una empresa y genera token de sesión.
     */
    suspend fun selectCompany(
        userId: Int,
        companyId: Int,
        countryCode: String,
    ): CompanySelectResponse {
        // Obtener BD de configuración del país
        val configDatabase = DatabaseManager.getConfigDatabase(countryCode)

        // Crear repositorios
        val authRepository = AuthRepository(configDatabase)
        val companyRepository = CompanyRepository(configDatabase)

        // Cargar usuario
        val user =
            authRepository.loadUserById(userId)
                ?: throw AuthenticationException("Token inválido o usuario no encontrado")

        // Validar acceso a empresa
        val companyCodes = parseCompanyCodes(user.companyCodesRaw)
        if (!companyCodes.contains(companyId)) {
            throw AuthorizationException("Usuario no tiene acceso a esta empresa")
        }

        // Cargar configuración de la empresa
        val company =
            companyRepository.loadCompanyConfig(companyId)
                ?: throw NotFoundException("Empresa no encontrada")
        val rif = companyRepository.loadCompanyRifByAdminDb(company.adminDb, countryCode)

        if (!company.admisActivo) {
            throw AuthorizationException("Empresa no disponible para POS")
        }

        // Crear token de empresa
        val token = createCompanyToken(user, company, countryCode)

        return CompanySelectResponse(
            success = true,
            token = token,
            currentCompany =
                CompanyDetailResponse(
                    id = company.id,
                    name = company.name,
                    adminDb = company.adminDb,
                    accountingDb = company.accountingDb,
                    payrollDb = company.payrollDb,
                    rif = rif,
                ),
            countryCode = countryCode,
            schemaType = getSchemaTypeForCountry(countryCode),
        )
    }

    private fun createCompanyToken(
        user: com.amaxoniaerp.features.auth.domain.UserRecord,
        company: CompanyConfig,
        countryCode: String,
    ): String {
        val builder =
            JWT
                .create()
                .withIssuer(jwtConfig.domain)
                .withAudience(jwtConfig.audience)
                .withClaim("token_type", "company")
                .withClaim("user_id", user.id)
                .withClaim("username", user.username)
                .withClaim("role", user.role ?: "")
                .withClaim("level_id", user.levelId ?: 0)
                .withClaim("company_id", company.id)
                .withClaim("admin_db", company.adminDb ?: "")
                .withClaim("country_code", countryCode)
                .withClaim("schema_type", getSchemaTypeForCountry(countryCode))

        if (!company.accountingDb.isNullOrBlank()) {
            builder.withClaim("accounting_db", company.accountingDb)
        }
        if (!company.payrollDb.isNullOrBlank()) {
            builder.withClaim("payroll_db", company.payrollDb)
        }

        return builder.sign(Algorithm.HMAC256(jwtConfig.secret))
    }

    private fun getSchemaTypeForCountry(countryCode: String): String =
        when (countryCode.uppercase()) {
            "VE" -> "TYPE_B"
            else -> "TYPE_A" // PA
        }
}
