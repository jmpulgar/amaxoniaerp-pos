package com.amaxonia.pos.ui.login

import app.cash.turbine.test
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
import com.amaxonia.pos.domain.usecase.auth.AuthenticateUserUseCase
import com.amaxonia.pos.domain.usecase.auth.ConfigureLoginCountryUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectingCountryUpdatesStateAndServerEnvironmentWithoutGlobalContainer() {
        val environment = FakeServerEnvironment()
        val viewModel = viewModel(environment = environment)
        val panama = ServerCountries.AVAILABLE.single { it.code == "PA" }

        viewModel.onAction(LoginUiAction.CountryChanged(panama))

        assertEquals(panama, viewModel.state.value.selectedCountry)
        assertEquals(listOf(panama), environment.selections)
    }

    @Test
    fun savedCountryIsRestoredAndApplied() =
        runTest(mainDispatcherRule.dispatcher) {
            val panama = ServerCountries.AVAILABLE.single { it.code == "PA" }
            val environment = FakeServerEnvironment()
            val viewModel = viewModel(environment = environment, store = FakeCountryStore(panama))

            viewModel.onAction(LoginUiAction.LoadSavedCountry)
            advanceUntilIdle()

            assertEquals(panama, viewModel.state.value.selectedCountry)
            assertEquals(panama, environment.selections.single())
        }

    @Test
    fun blankCredentialsFailBeforeRepositoryCall() =
        runTest(mainDispatcherRule.dispatcher) {
            var loginCalls = 0
            val auth =
                object : AuthRepository {
                    override suspend fun login(
                        username: String,
                        password: String,
                        countryCode: String,
                    ): Result<AuthSession> {
                        loginCalls += 1
                        return Result.failure(AssertionError("must not be called"))
                    }

                    override suspend fun selectCompany(companyId: Int): Result<CompanySession> =
                        Result.failure(AssertionError("must not be called"))

                    override suspend fun logout() = Unit
                }
            val viewModel = viewModel(auth = auth)
            viewModel.effects.test {
                viewModel.onAction(LoginUiAction.Submit)
                advanceUntilIdle()
                expectNoEvents()
            }

            assertEquals(0, loginCalls)
            assertTrue(
                viewModel.state.value.errorMessage
                    .orEmpty()
                    .contains("obligatorios"),
            )
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun connectivityFailureIsPresentedWithoutInfrastructureTypesInViewModel() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel(auth = ResultAuthRepository(AuthenticationConnectivityException(IOException("offline"))))
            viewModel.onAction(LoginUiAction.UsernameChanged("operator"))
            viewModel.onAction(LoginUiAction.PasswordChanged("secret"))

            viewModel.onAction(LoginUiAction.Submit)
            advanceUntilIdle()

            assertEquals(
                "No se pudo conectar al servidor. Compruebe que el backend esté en ejecución y la URL en ApiConfig.",
                viewModel.state.value.errorMessage,
            )
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun unauthorizedFailureKeepsExistingUserMessage() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel(auth = ResultAuthRepository(UnauthorizedException("unauthorized")))
            viewModel.onAction(LoginUiAction.UsernameChanged("operator"))
            viewModel.onAction(LoginUiAction.PasswordChanged("wrong"))

            viewModel.onAction(LoginUiAction.Submit)
            advanceUntilIdle()

            assertEquals("Usuario o contraseña incorrectos", viewModel.state.value.errorMessage)
        }

    @Test
    fun successfulLoginEmitsSingleNavigationEffect() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = AuthSession("token", AuthUser(1, "operator", "cashier"), emptyList())
            val viewModel = viewModel(auth = SuccessfulAuthRepository(session))

            viewModel.effects.test {
                viewModel.onAction(LoginUiAction.UsernameChanged("operator"))
                viewModel.onAction(LoginUiAction.PasswordChanged("secret"))
                viewModel.onAction(LoginUiAction.Submit)
                advanceUntilIdle()

                assertEquals(LoginUiEffect.LoginSucceeded, awaitItem())
                expectNoEvents()
            }
            assertFalse(viewModel.state.value.isLoading)
        }

    private fun viewModel(
        auth: AuthRepository = FailingAuthRepository,
        environment: FakeServerEnvironment = FakeServerEnvironment(),
        store: CountrySelectionStore = FakeCountryStore(null),
    ): LoginViewModel =
        LoginViewModel(
            AuthenticateUserUseCase(auth, store),
            ConfigureLoginCountryUseCase(environment, store),
        )

    private object FailingAuthRepository : AuthRepository {
        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> = Result.failure(IllegalStateException("offline"))

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> = Result.failure(IllegalStateException("offline"))

        override suspend fun logout() = Unit
    }

    private class ResultAuthRepository(
        private val failure: Throwable,
    ) : AuthRepository {
        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> = Result.failure(failure)

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> = Result.failure(failure)

        override suspend fun logout() = Unit
    }

    private class SuccessfulAuthRepository(
        private val session: AuthSession,
    ) : AuthRepository {
        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> = Result.success(session)

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> = Result.failure(AssertionError("must not be called"))

        override suspend fun logout() = Unit
    }

    private class FakeServerEnvironment : ServerEnvironment {
        val selections = mutableListOf<ServerCountry>()

        override fun selectCountry(country: ServerCountry) {
            selections += country
        }
    }

    private class FakeCountryStore(
        private var country: ServerCountry?,
    ) : CountrySelectionStore {
        override suspend fun readSelectedCountry(): ServerCountry? = country

        override suspend fun saveSelectedCountry(country: ServerCountry) {
            this.country = country
        }
    }
}
