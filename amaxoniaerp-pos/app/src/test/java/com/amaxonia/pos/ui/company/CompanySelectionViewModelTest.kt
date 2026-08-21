package com.amaxonia.pos.ui.company

import com.amaxonia.pos.domain.model.AuthSession
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.domain.model.CompanySession
import com.amaxonia.pos.domain.model.SelectedCompany
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.domain.repository.CompanyRepository
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanySelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val companies =
        listOf(
            Company(id = "1", name = "Empresa Uno", ruc = "R-1", address = ""),
            Company(id = "2", name = "Empresa Dos", ruc = "R-2", address = ""),
        )

    @Test
    fun `al iniciar carga las empresas y queda listo para seleccionar`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = CompanySelectionViewModel(repo(), auth())

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(companies, viewModel.state.value.companies)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `fallo de carga expone el mensaje del error o el fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val withMessage = CompanySelectionViewModel(repo(error = IllegalStateException("sin sesión")), auth())
            advanceUntilIdle()
            assertEquals("sin sesión", withMessage.state.value.error)

            val withoutMessage = CompanySelectionViewModel(repo(error = IllegalStateException()), auth())
            advanceUntilIdle()
            assertEquals("Error al cargar empresas", withoutMessage.state.value.error)
        }

    @Test
    fun `retry vuelve a consultar las empresas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val companyRepo = CountingCompanyRepository()
            val viewModel = CompanySelectionViewModel(companyRepo, auth())
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, companyRepo.loadCalls)
        }

    @Test
    fun `id de empresa no numerico expone error y no llama al repositorio`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = RecordingAuthRepository()
            val viewModel = CompanySelectionViewModel(repo(), auth)
            advanceUntilIdle()

            viewModel.selectCompany(Company(id = "abc", name = "X", ruc = "", address = "")) { }

            advanceUntilIdle()

            assertEquals("Id de empresa invalido", viewModel.state.value.error)
            assertEquals(0, auth.selectCalls)
        }

    @Test
    fun `seleccion exitosa invoca el callback una unica vez y limpia el error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = RecordingAuthRepository()
            val viewModel = CompanySelectionViewModel(repo(error = IllegalStateException("previo")), auth)
            advanceUntilIdle()

            var callbacks = 0
            viewModel.selectCompany(companies.first()) { callbacks++ }
            advanceUntilIdle()

            assertEquals(1, callbacks)
            assertEquals(1, auth.selectCalls)
            assertEquals(1, auth.selectIds.single())
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `fallo de seleccion expone el mensaje y no invoca el callback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth =
                RecordingAuthRepository(
                    result = Result.failure(IllegalStateException("empresa inactiva")),
                )
            val viewModel = CompanySelectionViewModel(repo(), auth)
            advanceUntilIdle()

            var callbacks = 0
            viewModel.selectCompany(companies.first()) { callbacks++ }
            advanceUntilIdle()

            assertEquals("empresa inactiva", viewModel.state.value.error)
            assertEquals(0, callbacks)
        }

    private fun repo(error: IllegalStateException? = null): CompanyRepository =
        object : CompanyRepository {
            override suspend fun getAllCompanies(): Result<List<Company>> =
                if (error != null) Result.failure(error) else Result.success(companies)

            override suspend fun getCompanyById(id: String): Result<Company> = Result.failure(AssertionError("must not be called"))
        }

    private fun auth() = RecordingAuthRepository()

    private class CountingCompanyRepository : CompanyRepository {
        var loadCalls = 0

        override suspend fun getAllCompanies(): Result<List<Company>> {
            loadCalls++
            return Result.success(emptyList())
        }

        override suspend fun getCompanyById(id: String): Result<Company> = Result.failure(AssertionError("must not be called"))
    }

    private class RecordingAuthRepository(
        private val result: Result<CompanySession> =
            Result.success(
                CompanySession(
                    token = "token",
                    company = SelectedCompany(id = 1, name = "Empresa Uno", adminDb = "db", accountingDb = "c", payrollDb = "n"),
                ),
            ),
    ) : AuthRepository {
        var selectCalls = 0
            private set
        val selectIds = mutableListOf<Int>()

        override suspend fun login(
            username: String,
            password: String,
            countryCode: String,
        ) = Result.failure<AuthSession>(AssertionError("must not be called"))

        override suspend fun selectCompany(companyId: Int): Result<CompanySession> {
            selectCalls++
            selectIds += companyId
            return result
        }

        override suspend fun logout() = Unit
    }
}
