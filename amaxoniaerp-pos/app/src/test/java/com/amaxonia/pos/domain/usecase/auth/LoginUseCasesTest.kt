package com.amaxonia.pos.domain.usecase.auth

import com.amaxonia.pos.domain.error.AuthenticationConnectivityException
import com.amaxonia.pos.domain.error.UnauthorizedException
import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.AuthUser
import com.amaxonia.pos.domain.model.CompanySession
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.CountrySelectionStore
import com.amaxonia.pos.domain.repository.ServerEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class LoginUseCasesTest {
    @Test
    fun `blank credentials do not call repository`() =
        runTest {
            val auth = RecordingAuthRepository(Result.success(SESSION))
            val result = AuthenticateUserUseCase(auth, MemoryCountryStore())(credentials(username = " "))

            assertEquals(LoginResult.Failure(LoginError.MissingCredentials), result)
            assertEquals(0, auth.loginCalls)
        }

    @Test
    fun `successful login preserves boundary values and selected country`() =
        runTest {
            val auth = RecordingAuthRepository(Result.success(SESSION))
            val store = MemoryCountryStore()
            val panama = ServerCountries.AVAILABLE.single { it.code == "PA" }

            val result = AuthenticateUserUseCase(auth, store)(credentials(username = " operator ", country = panama))

            assertEquals(LoginResult.Success(SESSION), result)
            assertEquals(Triple("operator", "secret", "PA"), auth.lastCredentials)
            assertEquals(panama, store.country)
        }

    @Test
    fun `authentication failures become typed domain errors`() =
        runTest {
            val unauthorized = authenticateFailure(UnauthorizedException("private"))
            val connectivity = authenticateFailure(AuthenticationConnectivityException(IOException("offline")))

            assertEquals(LoginResult.Failure(LoginError.Unauthorized), unauthorized)
            assertEquals(LoginResult.Failure(LoginError.Connectivity), connectivity)
        }

    @Test
    fun `restored country is applied to server environment`() =
        runTest {
            val panama = ServerCountries.AVAILABLE.single { it.code == "PA" }
            val environment = RecordingServerEnvironment()
            val useCase = ConfigureLoginCountryUseCase(environment, MemoryCountryStore(panama))

            assertEquals(panama, useCase.restore(ServerCountries.AVAILABLE.first()))
            assertEquals(listOf(panama), environment.selections)
        }

    private suspend fun authenticateFailure(error: Throwable): LoginResult =
        AuthenticateUserUseCase(
            RecordingAuthRepository(Result.failure(error)),
            MemoryCountryStore(),
        )(credentials())

    private fun credentials(
        username: String = "operator",
        country: ServerCountry = ServerCountries.AVAILABLE.first(),
    ) = LoginCredentials(username, "secret", country)

    private class RecordingAuthRepository(
        private val result: Result<AuthSession>,
    ) : AuthRepository {
        var loginCalls = 0
        var lastCredentials: Triple<String, String, String>? = null

        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> {
            loginCalls += 1
            lastCredentials = Triple(username, password, countryCode)
            return result
        }

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> = Result.failure(AssertionError("not used"))

        override suspend fun logout() = Unit
    }

    private class MemoryCountryStore(
        var country: ServerCountry? = null,
    ) : CountrySelectionStore {
        override suspend fun readSelectedCountry(): ServerCountry? = country

        override suspend fun saveSelectedCountry(country: ServerCountry) {
            this.country = country
        }
    }

    private class RecordingServerEnvironment : ServerEnvironment {
        val selections = mutableListOf<ServerCountry>()

        override fun selectCountry(country: ServerCountry) {
            selections += country
        }
    }

    private companion object {
        val SESSION = AuthSession("token", AuthUser(1, "operator", "cashier"), emptyList())
    }
}
