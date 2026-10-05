package com.amaxonia.erp.ui.login

import app.cash.turbine.test
import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.CompanySummary
import com.amaxonia.erp.domain.model.SelectedCompany
import com.amaxonia.erp.domain.model.ServerCountries
import com.amaxonia.erp.domain.model.ServerCountry
import com.amaxonia.erp.domain.repository.AuthRepository
import com.amaxonia.erp.domain.repository.CountrySelectionStore
import com.amaxonia.erp.domain.usecase.AuthenticateUserUseCase
import com.amaxonia.erp.domain.usecase.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeCountrySelectionStore(
        var savedCountry: ServerCountry? = ServerCountries.PANAMA,
    ) : CountrySelectionStore {
        override suspend fun saveSelectedCountry(country: ServerCountry) {
            savedCountry = country
        }

        override suspend fun readSelectedCountry(): ServerCountry? = savedCountry
    }

    private class FakeAuthRepository(
        var result: Result<AuthSession> =
            Result.success(
                AuthSession(
                    token = "token-xyz",
                    user = AuthUser(id = 10, username = "posuser", role = "cashier"),
                    companies = listOf(CompanySummary(id = 1, name = "Empresa Unica")),
                ),
            ),
    ) : AuthRepository {
        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ): Result<AuthSession> = result

        override suspend fun logout() {}

        override suspend fun getActiveSession(): AuthSession? = null

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> =
            Result.success(
                CompanySession(
                    token = "company-token-123",
                    company = SelectedCompany(id = companyId, name = "Empresa Unica"),
                    user = AuthUser(id = 10, username = "posuser", role = "cashier"),
                ),
            )

        override suspend fun getActiveCompanySession(): CompanySession? = null
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has Panama as default country when store is empty`() =
        runTest {
            val store = FakeCountrySelectionStore(savedCountry = null)
            val authRepo = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ServerCountries.PANAMA, viewModel.state.value.selectedCountry)
            assertEquals("", viewModel.state.value.username)
            assertEquals("", viewModel.state.value.password)
            assertFalse(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `initial state always enforces Panama as country and persists to store`() =
        runTest {
            val store = FakeCountrySelectionStore(savedCountry = ServerCountries.VENEZUELA)
            val authRepo = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ServerCountries.PANAMA, viewModel.state.value.selectedCountry)
            assertEquals(ServerCountries.PANAMA, store.savedCountry)
        }

    @Test
    fun `UsernameChanged action updates username and clears error message`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            viewModel.onAction(LoginUiAction.UsernameChanged("cashier_01"))

            assertEquals("cashier_01", viewModel.state.value.username)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `PasswordChanged action updates password and clears error message`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            viewModel.onAction(LoginUiAction.PasswordChanged("secret123"))

            assertEquals("secret123", viewModel.state.value.password)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `TogglePasswordVisibility toggles isPasswordVisible state`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository()
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            assertFalse(viewModel.state.value.isPasswordVisible)
            viewModel.onAction(LoginUiAction.TogglePasswordVisibility)
            assertTrue(viewModel.state.value.isPasswordVisible)
            viewModel.onAction(LoginUiAction.TogglePasswordVisibility)
            assertFalse(viewModel.state.value.isPasswordVisible)
        }

    @Test
    fun `Submit with single company auto-selects and emits CompanySessionReady effect`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository(
                Result.success(
                    AuthSession(
                        token = "token-xyz",
                        user = AuthUser(id = 10, username = "posuser", role = "cashier"),
                        companies = listOf(CompanySummary(id = 1, name = "Empresa Unica")),
                    ),
                ),
            )
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            viewModel.onAction(LoginUiAction.UsernameChanged("admin"))
            viewModel.onAction(LoginUiAction.PasswordChanged("password"))

            viewModel.effects.test {
                viewModel.onAction(LoginUiAction.Submit)
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is LoginUiEffect.CompanySessionReady)
                assertEquals("company-token-123", (effect as LoginUiEffect.CompanySessionReady).companySession.token)
                assertFalse(viewModel.state.value.isLoading)
            }
        }

    @Test
    fun `Submit with multiple companies emits RequiresCompanySelection effect`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository(
                Result.success(
                    AuthSession(
                        token = "token-xyz",
                        user = AuthUser(id = 10, username = "posuser", role = "cashier"),
                        companies = listOf(
                            CompanySummary(id = 1, name = "Empresa Uno"),
                            CompanySummary(id = 2, name = "Empresa Dos"),
                        ),
                    ),
                ),
            )
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            viewModel.onAction(LoginUiAction.UsernameChanged("admin"))
            viewModel.onAction(LoginUiAction.PasswordChanged("password"))

            viewModel.effects.test {
                viewModel.onAction(LoginUiAction.Submit)
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is LoginUiEffect.RequiresCompanySelection)
                assertEquals(2, (effect as LoginUiEffect.RequiresCompanySelection).session.companies.size)
                assertFalse(viewModel.state.value.isLoading)
            }
        }

    @Test
    fun `Submit with invalid credentials sets errorMessage on state`() =
        runTest {
            val store = FakeCountrySelectionStore()
            val authRepo = FakeAuthRepository(Result.failure(UnauthorizedException()))
            val useCase = AuthenticateUserUseCase(authRepo)
            val viewModel = LoginViewModel(useCase, store, authRepo)

            viewModel.onAction(LoginUiAction.UsernameChanged("admin"))
            viewModel.onAction(LoginUiAction.PasswordChanged("wrongpassword"))

            viewModel.onAction(LoginUiAction.Submit)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Usuario o contraseña incorrectos.", viewModel.state.value.errorMessage)
        }
}
