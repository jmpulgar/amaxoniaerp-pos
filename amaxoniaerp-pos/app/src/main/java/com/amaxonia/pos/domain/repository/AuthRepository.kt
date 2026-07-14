package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.CompanySession

interface AuthRepository {
    /**
     * Inicia sesión en el servidor correspondiente al país seleccionado.
     *
     * @param username Nombre de usuario
     * @param password Contraseña
     * @param countryCode Código del país (VE, PA, RD, CO) para enviar al backend
     * @return Resultado con la sesión de autenticación
     */
    suspend fun login(
        username: String,
        password: String,
        countryCode: String,
    ): Result<AuthSession>

    suspend fun selectCompany(companyId: Int): Result<CompanySession>

    suspend fun logout()
}
