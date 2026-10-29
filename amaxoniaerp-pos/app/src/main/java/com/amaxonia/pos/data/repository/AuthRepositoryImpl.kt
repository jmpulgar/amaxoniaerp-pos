package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.clearAuthSession
import com.amaxonia.pos.data.local.db.OfflineCompanyDao
import com.amaxonia.pos.data.local.db.OfflineCompanyEntity
import com.amaxonia.pos.data.local.db.OfflineUserDao
import com.amaxonia.pos.data.local.db.OfflineUserEntity
import com.amaxonia.pos.data.local.readAuthSnapshot
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.saveAuthSnapshot
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.local.toSnapshot
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.authHeaders
import com.amaxonia.pos.data.remote.dto.SelectCompanyRequest
import com.amaxonia.pos.data.sync.AuthDirectoryResponseDto
import com.amaxonia.pos.data.sync.OfflineSyncSettingsStore
import com.amaxonia.pos.domain.error.AuthenticationConnectivityException
import com.amaxonia.pos.domain.error.UnauthorizedException
import com.amaxonia.pos.domain.error.UnexpectedAuthenticationException
import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.AuthUser
import com.amaxonia.pos.domain.model.CompanySession
import com.amaxonia.pos.domain.model.CompanySummary
import com.amaxonia.pos.domain.repository.AuthRepository
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.security.MessageDigest

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val offlineUserDao: OfflineUserDao? = null,
    private val offlineCompanyDao: OfflineCompanyDao? = null,
    private val offlineSyncSettingsStore: OfflineSyncSettingsStore? = null,
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
        }.recoverCatching { error ->
            val authException = error.toAuthenticationException()
            val isNetworkOrServerError =
                authException is AuthenticationConnectivityException ||
                    authException is UnexpectedAuthenticationException
            if (isNetworkOrServerError && canAttemptOfflineLogin(countryCode)) {
                loginOffline(username, password, countryCode)
            } else if (authException is AuthenticationConnectivityException) {
                throw UnexpectedAuthenticationException(
                    "Sin conexión a internet y no hay usuarios sincronizados en este dispositivo. Debe iniciar sesión online la primera vez.",
                    authException,
                )
            } else {
                throw authException
            }
        }

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
            val countryCode = response.countryCode ?: response.currentCompany.countryCode ?: localStore.currentCountryCode()
            val sessionSnapshot =
                CompanySessionSnapshot(
                    token = response.token,
                    company = response.currentCompany.toSnapshot(countryCode),
                )
            localStore.saveCompanySession(sessionSnapshot)
            syncAuthDirectorySilently(sessionSnapshot.token)
            response.toDomain(isOffline = false)
        }.recoverCatching { error ->
            val cached = localStore.readCompanySession()
            if (cached != null && cached.company.id == companyId) {
                cached.toDomain(isOffline = true)
            } else {
                val offlineCompany = offlineCompanyDao?.getById(companyId)
                if (offlineCompany != null) {
                    val companySnapshot =
                        CompanySessionSnapshot(
                            token = "offline_company_${offlineCompany.id}_${System.currentTimeMillis()}",
                            company =
                                CompanyDetailsSnapshot(
                                    id = offlineCompany.id,
                                    name = offlineCompany.name,
                                    adminDb = offlineCompany.adminDb,
                                    accountingDb = offlineCompany.accountingDb,
                                    payrollDb = offlineCompany.payrollDb,
                                    rif = offlineCompany.rif,
                                    countryCode = offlineCompany.countryCode.ifBlank { localStore.currentCountryCode() },
                                ),
                        )
                    localStore.saveCompanySession(companySnapshot)
                    companySnapshot.toDomain(isOffline = true)
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun canAttemptOfflineLogin(countryCode: String): Boolean {
        val userCount = offlineUserDao?.countActiveUsers(countryCode) ?: 0
        return userCount > 0
    }

    private suspend fun syncAuthDirectorySilently(token: String) {
        val userDao = offlineUserDao ?: return
        val companyDao = offlineCompanyDao ?: return
        runCatching {
            val directory =
                apiService.client
                    .get("api/sync/v1/auth-directory") {
                        authHeaders(token)
                    }.body<AuthDirectoryResponseDto>()
            val now = System.currentTimeMillis()
            val users =
                directory.users.map { u ->
                    OfflineUserEntity(
                        id = u.id,
                        username = u.username,
                        passwordHash = u.passwordHash,
                        role = u.role,
                        levelId = u.levelId,
                        companyCodes = u.companyCodes,
                        countryCode = directory.countryCode,
                        status = "A",
                        updatedAt = now,
                    )
                }
            val companies =
                directory.companies.map { c ->
                    OfflineCompanyEntity(
                        id = c.id,
                        name = c.name,
                        adminDb = c.adminDb,
                        accountingDb = c.accountingDb,
                        payrollDb = c.payrollDb,
                        rif = c.rif,
                        countryCode = c.countryCode.ifBlank { directory.countryCode },
                        updatedAt = now,
                    )
                }
            userDao.upsertAll(users)
            companyDao.upsertAll(companies)
        }
    }

    private suspend fun loginOffline(
        username: String,
        password: String,
        countryCode: String,
    ): AuthSession {
        val userDao = offlineUserDao ?: throw AuthenticationConnectivityException(IllegalStateException("OfflineUserDao no disponible"))
        val user =
            userDao.findByUsernameAndCountry(username.trim(), countryCode)
                ?: throw UnauthorizedException("Usuario o contraseña incorrectos")

        if (user.status != "A") {
            throw UnauthorizedException("Usuario inactivo")
        }

        val inputHash = md5Hash(password)
        if (!inputHash.equals(user.passwordHash, ignoreCase = true)) {
            throw UnauthorizedException("Usuario o contraseña incorrectos")
        }

        val companyDao = offlineCompanyDao
        val companies = companyDao?.getByIds(user.companyCodes).orEmpty()
        val session =
            AuthSession(
                token = "offline_session_${user.id}_${System.currentTimeMillis()}",
                user =
                    AuthUser(
                        id = user.id,
                        username = user.username,
                        role = user.role,
                    ),
                companies = companies.map { CompanySummary(id = it.id, name = it.name) },
                isOffline = true,
            )

        localStore.saveAuthSnapshot(
            com.amaxonia.pos.data.local.AuthSnapshot(
                token = session.token,
                user =
                    com.amaxonia.pos.data.local.AuthUserSnapshot(
                        id = user.id,
                        username = user.username,
                        role = user.role,
                    ),
                companies =
                    companies.map {
                        com.amaxonia.pos.data.local.CompanySnapshot(
                            id = it.id,
                            name = it.name,
                            rif = it.rif,
                        )
                    },
            ),
        )
        return session
    }

    override suspend fun logout() {
        localStore.clearAuthSession()
    }
}

private fun md5Hash(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun Throwable.toAuthenticationException(): Throwable =
    when (this) {
        is CancellationException,
        is UnauthorizedException,
        -> this
        is SocketTimeoutException,
        is IOException,
        -> AuthenticationConnectivityException(this)
        else -> UnexpectedAuthenticationException(message ?: "No se pudo iniciar sesión", this)
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
