package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.AuthSession

interface AuthRepository {
    suspend fun login(
        username: String,
        password: String,
        countryCode: String,
    ): Result<AuthSession>

    suspend fun selectCompany(companyId: Int): Result<com.amaxonia.erp.domain.model.CompanySession>

    suspend fun getActiveCompanySession(): com.amaxonia.erp.domain.model.CompanySession?

    suspend fun logout()

    suspend fun getActiveSession(): AuthSession?
}
