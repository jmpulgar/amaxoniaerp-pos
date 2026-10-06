package com.amaxonia.kiosk.ui.login

import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.testutil.json
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val testDispatcher = UnconfinedTestDispatcher()
    private val storage = KioskTokenStorage()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loginJson(companies: String) =
        """{"token":"identity","user":{"id":7,"username":"cajero1","role":"CAJERO"},"companies":[$companies],"countryCode":"PA"}"""

    private val companyJson =
        """{"success":true,"token":"company-token","currentCompany":{"id":3,"name":"Momi Café","adminDb":"momi_admin",""" +
            """"accountingDb":"","payrollDb":"","rif":"R-3"},"countryCode":"PA","schemaType":"TYPE_A"}"""

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<LoginViewModel, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
        return LoginViewModel(client, storage, defaultServerUrl = "http://localhost:8080/", defaultCountryCode = "PA") to engine
    }

    private fun LoginViewModel.fill() {
        onUsernameChanged("cajero1")
        onPasswordChanged("secreta")
    }

    @Test
    fun `starts with the build defaults`() {
        val (vm, _) = viewModel { json("{}") }

        val state = vm.uiState.value
        assertEquals("PA", state.countryCode)
        assertEquals("http://localhost:8080/", state.serverUrl)
        assertFalse(state.showAdvanced)
        assertEquals(LoginStep.CREDENTIALS, state.step)
    }

    @Test
    fun `blank credentials are rejected without calling the server`() {
        val (vm, engine) = viewModel { json("{}") }

        vm.submit()

        assertEquals(LoginError.MissingCredentials, vm.uiState.value.error)
        assertTrue(engine.requestHistory.isEmpty())
    }

    @Test
    fun `a single company is selected automatically and the session is saved`() =
        runTest(testDispatcher) {
            val (vm, engine) =
                viewModel { request ->
                    if (request.url.encodedPath.endsWith("auth/login")) {
                        json(loginJson("""{"id":3,"name":"Momi Café","rif":"R-3"}"""))
                    } else {
                        json(companyJson)
                    }
                }
            vm.onCountrySelected("VE")
            vm.fill()

            vm.submit()?.join()

            assertEquals(listOf("/auth/login", "/auth/company"), engine.requestHistory.map { it.url.encodedPath })
            assertEquals("VE", engine.requestHistory.first().headers["X-Country-Code"])
            assertEquals("Bearer identity", engine.requestHistory.last().headers["Authorization"])
            assertEquals(LoginEvent.LoggedIn, vm.events.first())
            assertTrue(storage.isLoggedIn())
            assertFalse(storage.hasCaja())
            assertEquals("company-token", storage.authToken)
            assertEquals("momi_admin", storage.companyDb)
            assertEquals("Momi Café", storage.companyName)
            assertEquals("cajero1", storage.username)
            assertEquals("PA", storage.countryCode)
            assertEquals("http://localhost:8080/", storage.serverUrl)
        }

    @Test
    fun `several companies show the company step and selecting one completes the login`() =
        runTest(testDispatcher) {
            val (vm, _) =
                viewModel { request ->
                    if (request.url.encodedPath.endsWith("auth/login")) {
                        json(loginJson("""{"id":2,"name":"Otra"},{"id":3,"name":"Momi Café"}"""))
                    } else {
                        json(companyJson)
                    }
                }
            vm.fill()

            vm.submit()?.join()

            val state = vm.uiState.value
            assertEquals(LoginStep.COMPANY, state.step)
            assertEquals(listOf(2, 3), state.companies.map { it.id })
            assertFalse(storage.isLoggedIn())

            vm.selectCompany(state.companies[1])?.join()

            assertTrue(storage.isLoggedIn())
            assertEquals("3", storage.companyId)
        }

    @Test
    fun `wrong credentials show the POS message`() =
        runTest(testDispatcher) {
            val (vm, _) = viewModel { json("""{"error":"x"}""", HttpStatusCode.Unauthorized) }
            vm.fill()

            vm.submit()?.join()

            val state = vm.uiState.value
            assertEquals(LoginError.InvalidCredentials, state.error)
            assertFalse(state.isLoading)
            assertFalse(storage.isLoggedIn())
        }

    @Test
    fun `unreachable server is a connectivity error`() =
        runTest(testDispatcher) {
            val (vm, _) = viewModel { throw java.net.ConnectException("refused") }
            vm.fill()

            vm.submit()?.join()

            assertEquals(LoginError.Connectivity, vm.uiState.value.error)
        }

    @Test
    fun `a user without companies cannot log in`() =
        runTest(testDispatcher) {
            val (vm, _) = viewModel { json(loginJson("")) }
            vm.fill()

            vm.submit()?.join()

            assertEquals(LoginError.NoCompanies, vm.uiState.value.error)
            assertNull(storage.authToken)
        }

    @Test
    fun `blank server URL opens the advanced options`() {
        val (vm, _) = viewModel { json("{}") }
        vm.fill()
        vm.onServerUrlChanged(" ")

        vm.submit()

        assertEquals(LoginError.MissingServerUrl, vm.uiState.value.error)
        assertTrue(vm.uiState.value.showAdvanced)
    }
}
