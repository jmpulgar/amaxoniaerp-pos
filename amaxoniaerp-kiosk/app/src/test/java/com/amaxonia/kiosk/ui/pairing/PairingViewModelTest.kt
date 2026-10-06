package com.amaxonia.kiosk.ui.pairing

import com.amaxonia.kiosk.BuildConfig
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() {
        val storage = KioskTokenStorage(context = null)
        val engine = MockEngine { respond("") }
        val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
        val viewModel = PairingViewModel(client)

        val state = viewModel.uiState.value
        assertEquals("PA", state.countryCode)
        assertEquals(BuildConfig.DEFAULT_SERVER_URL, state.serverUrl)
        assertEquals("", state.companyDb)
        assertEquals("", state.pairingCode)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `validation fails on missing company db`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine = MockEngine { respond("") }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = PairingViewModel(client)

            viewModel.onPairingCodeChanged("12345678")
            viewModel.submitPairing()

            assertEquals("La base de datos de la empresa es requerida", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `validation fails on short pairing code`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine = MockEngine { respond("") }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = PairingViewModel(client)

            viewModel.onCompanyDbChanged("amaxonia_db")
            viewModel.onPairingCodeChanged("1234")
            viewModel.submitPairing()

            assertEquals("El código de emparejamiento debe tener 8 caracteres", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `successful pairing emits event and clears loading`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val jsonSuccess =
                """
                {
                    "deviceId": "dev-001",
                    "deviceToken": "jwt-tok-123",
                    "deviceName": "Kiosco Principal",
                    "prefix": "K1"
                }
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(
                        content = jsonSuccess,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = PairingViewModel(client)

            viewModel.onCompanyDbChanged("amaxonia_db")
            viewModel.onPairingCodeChanged("ABCD1234")

            var receivedEvent: PairingEvent? = null
            val collectJob =
                launch(testDispatcher) {
                    receivedEvent = viewModel.events.first()
                }

            val job = viewModel.submitPairing()
            job.join()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertNotNull(receivedEvent)
            assertTrue(receivedEvent is PairingEvent.Success)
            val success = receivedEvent as PairingEvent.Success
            assertEquals("Kiosco Principal", success.deviceName)
            assertEquals("K1", success.prefix)

            collectJob.cancel()
        }

    @Test
    fun `failed pairing sets error message`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"Código inválido"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = PairingViewModel(client)

            viewModel.onCompanyDbChanged("amaxonia_db")
            viewModel.onPairingCodeChanged("99999999")

            val job = viewModel.submitPairing()
            job.join()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            assertTrue(state.errorMessage!!.contains("400"))
        }
}
