package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.dto.LoginRequest
import com.amaxonia.erp.data.remote.dto.LoginResponse
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.CompanySummary
import com.amaxonia.erp.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : AuthRepository {
    override suspend fun login(
        username: String,
        password: String,
        countryCode: String,
    ): Result<AuthSession> =
        runCatching {
            val response =
                apiService.login(
                    request = LoginRequest(username = username, password = password),
                    countryCode = countryCode,
                )
            localStore.saveAuthSession(response)
            response.toDomain()
        }

    override suspend fun selectCompany(companyId: Int): Result<com.amaxonia.erp.domain.model.CompanySession> =
        runCatching {
            val authSnapshot = localStore.readAuthSession()
                ?: error("No hay sesión de usuario activa")
            val response = apiService.selectCompany(
                token = authSnapshot.token,
                request = com.amaxonia.erp.data.remote.dto.SelectCompanyRequest(companyId = companyId),
            )
            val session = com.amaxonia.erp.domain.model.CompanySession(
                token = response.token,
                company = com.amaxonia.erp.domain.model.SelectedCompany(
                    id = response.currentCompany.id,
                    name = response.currentCompany.name,
                    adminDb = response.currentCompany.adminDb,
                    accountingDb = response.currentCompany.accountingDb,
                    payrollDb = response.currentCompany.payrollDb,
                    rif = response.currentCompany.rif,
                    countryCode = response.countryCode ?: response.currentCompany.countryCode,
                ),
                user = AuthUser(
                    id = authSnapshot.user.id,
                    username = authSnapshot.user.username,
                    role = authSnapshot.user.role,
                ),
            )
            localStore.saveCompanySession(session)
            session
        }

    override suspend fun getActiveCompanySession(): com.amaxonia.erp.domain.model.CompanySession? =
        localStore.readCompanySession()

    override suspend fun logout() {
        localStore.clearAuthSession()
    }

    override suspend fun getActiveSession(): AuthSession? = localStore.readAuthSession()?.toDomain()
}

fun LoginResponse.toDomain(): AuthSession =
    AuthSession(
        token = token,
        user = AuthUser(id = user.id, username = user.username, role = user.role),
        companies = companies.map { CompanySummary(id = it.id, name = it.name, rif = it.rif) },
    )
