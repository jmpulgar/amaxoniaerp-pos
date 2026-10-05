package com.amaxonia.erp.domain.usecase

import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.LoginCredentials
import com.amaxonia.erp.domain.model.LoginError
import com.amaxonia.erp.domain.model.LoginResult
import com.amaxonia.erp.domain.model.ServerCountries
import com.amaxonia.erp.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticateUserUseCaseTest {
    private class FakeAuthRepository(
        var loginResult: Result<AuthSession> =
            Result.success(
                AuthSession(
                    token = "fake-token",
                    user = AuthUser(id = 1, username = "testuser", role = "admin"),
                    companies = emptyList(),
                ),
            ),
    ) : AuthRepository {
        var lastLoginUsername: String? = null
        var lastLoginPassword: String? = null
        var lastCountryCode: String? = null

        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> {
            lastLoginUsername = username
            lastLoginPassword = password
            lastCountryCode = countryCode
            return loginResult
        }

        override suspend fun logout() {}

        override suspend fun getActiveSession(): AuthSession? = null

        override suspend fun selectCompany(companyId: Int): Result<com.amaxonia.erp.domain.model.CompanySession> =
            Result.success(
                com.amaxonia.erp.domain.model.CompanySession(
                    token = "fake-company-token",
                    company = com.amaxonia.erp.domain.model.SelectedCompany(id = companyId, name = "Test Company"),
                    user = AuthUser(id = 1, username = "testuser", role = "admin"),
                ),
            )

        override suspend fun getActiveCompanySession(): com.amaxonia.erp.domain.model.CompanySession? = null
    }

    @Test
    fun `invoke with blank username returns MissingCredentials error`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(repository)

            val result =
                useCase(
                    LoginCredentials(
                        username = "  ",
                        password = "secretPassword",
                        country = ServerCountries.PANAMA,
                    ),
                )

            assertTrue(result is LoginResult.Failure)
            assertEquals(LoginError.MissingCredentials, (result as LoginResult.Failure).error)
        }

    @Test
    fun `invoke with blank password returns MissingCredentials error`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(repository)

            val result =
                useCase(
                    LoginCredentials(
                        username = "cashier",
                        password = "",
                        country = ServerCountries.PANAMA,
                    ),
                )

            assertTrue(result is LoginResult.Failure)
            assertEquals(LoginError.MissingCredentials, (result as LoginResult.Failure).error)
        }

    @Test
    fun `invoke with valid credentials calls repository with trimmed username and country code`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(repository)

            val result =
                useCase(
                    LoginCredentials(
                        username = "  admin  ",
                        password = "password123",
                        country = ServerCountries.PANAMA,
                    ),
                )

            assertEquals("admin", repository.lastLoginUsername)
            assertEquals("password123", repository.lastLoginPassword)
            assertEquals("PA", repository.lastCountryCode)
            assertTrue(result is LoginResult.Success)
            assertEquals("fake-token", (result as LoginResult.Success).session.token)
        }

    @Test
    fun `invoke when repository fails with UnauthorizedException maps to Unauthorized error`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(UnauthorizedException()))
            val useCase = AuthenticateUserUseCase(repository)

            val result =
                useCase(
                    LoginCredentials(
                        username = "user",
                        password = "wrong",
                        country = ServerCountries.VENEZUELA,
                    ),
                )

            assertTrue(result is LoginResult.Failure)
            assertEquals(LoginError.Unauthorized, (result as LoginResult.Failure).error)
            assertEquals("VE", repository.lastCountryCode)
        }

    @Test
    fun `invoke when repository fails with ConnectivityException maps to Connectivity error`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(ConnectivityException()))
            val useCase = AuthenticateUserUseCase(repository)

            val result =
                useCase(
                    LoginCredentials(
                        username = "user",
                        password = "pass",
                        country = ServerCountries.PANAMA,
                    ),
                )

            assertTrue(result is LoginResult.Failure)
            assertEquals(LoginError.Connectivity, (result as LoginResult.Failure).error)
        }
}
