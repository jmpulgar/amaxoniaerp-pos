package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.toSnapshot
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.dto.SelectCompanyRequest
import com.amaxonia.pos.domain.error.AuthenticationConnectivityException
import com.amaxonia.pos.domain.error.UnauthorizedException
import com.amaxonia.pos.domain.error.UnexpectedAuthenticationException
import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.AuthUser
import com.amaxonia.pos.domain.model.CompanySession
import com.amaxonia.pos.domain.model.CompanySummary
import com.amaxonia.pos.domain.repository.AuthRepository
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import java.io.IOException

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
                    request =
                        com.amaxonia.pos.data.remote.dto.LoginRequest(
                            username = username,
                            password = password,
                        ),
                    countryCode = countryCode,
                )
            localStore.saveAuthSnapshot(response.toSnapshot())
            response.toDomain(isOffline = false)
        }.recoverCatching { error -> throw error.toAuthenticationException() }

    override suspend fun selectCompany(companyId: Int): Result<CompanySession> {
        val authSnapshot =
            localStore.readAuthSnapshot()
                ?: return Result.failure(IllegalStateException("No hay sesion iniciada"))
        return runCatching {
            val response =
                apiService.selectCompany(
                    token = authSnapshot.token,
                    request = SelectCompanyRequest(companyId),
                )
            val sessionSnapshot =
                CompanySessionSnapshot(
                    token = response.token,
                    company = response.currentCompany.toSnapshot(),
                )
            localStore.saveCompanySession(sessionSnapshot)
            response.toDomain(isOffline = false)
        }.recoverCatching { error ->
            val cached = localStore.readCompanySession()
            if (cached != null && cached.company.id == companyId) {
                cached.toDomain(isOffline = true)
            } else {
                throw error
            }
        }
    }

    override suspend fun logout() {
        localStore.clearAuthSession()
    }
}

private fun Throwable.toAuthenticationException(): Throwable =
    when (this) {
        is CancellationException,
        is UnauthorizedException,
        -> this
        is SocketTimeoutException,
        is IOException,
        -> AuthenticationConnectivityException(this)
        else -> UnexpectedAuthenticationException(message ?: "No se pudo iniciar sesiÃ³n", this)
    }

private fun com.amaxonia.pos.data.remote.dto.LoginResponse.toDomain(isOffline: Boolean): AuthSession =
    AuthSession(
        token = token,
        user =
            AuthUser(
                id = user.id,
                username = user.username,
                role = user.role,
            ),
        companies = companies.map { CompanySummary(id = it.id, name = it.name) },
        isOffline = isOffline,
    )

private fun com.amaxonia.pos.data.remote.dto.SelectCompanyResponse.toDomain(isOffline: Boolean): CompanySession =
    CompanySession(
        token = token,
        company = currentCompany.toSelectedCompany(),
        isOffline = isOffline,
    )

private fun CompanySessionSnapshot.toDomain(isOffline: Boolean): CompanySession =
    CompanySession(
        token = token,
        company =
            com.amaxonia.pos.domain.model.SelectedCompany(
                id = company.id,
                name = company.name,
                adminDb = company.adminDb,
                accountingDb = company.accountingDb,
                payrollDb = company.payrollDb,
                rif = company.rif,
            ),
        isOffline = isOffline,
    )
