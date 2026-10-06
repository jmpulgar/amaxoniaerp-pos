package com.amaxonia.kiosk.ui.attract

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
class AttractViewModelTest {
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
    fun `loadConfig updates state with config media and brand color`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val jsonConfig =
                """
                {
                    "version": 1,
                    "brandColor": "#FF5722",
                    "logoUrl": "http://10.0.2.2:8080/logo.png",
                    "media": [
                        {"id": 1, "type": "VIDEO", "url": "http://10.0.2.2:8080/video1.mp4", "durationSec": 15, "order": 1},
                        {"id": 2, "type": "IMAGE", "url": "http://10.0.2.2:8080/img1.jpg", "durationSec": 8, "order": 2}
                    ],
                    "diningModes": ["COMER_AQUI", "PARA_LLEVAR"],
                    "dispatch": "RETIRO_MOSTRADOR",
                    "defaultCustomerId": "CF",
                    "currency": {"base": "USD", "secondary": "VED", "rate": "36.50"},
                    "country": "PA"
                }
                """.trimIndent()

            val engine =
                MockEngine {
                    respond(
                        content = jsonConfig,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ETag to listOf("W/\"cfg-123\""),
                            ),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isOffline)
            assertEquals("#FF5722", state.brandColor)
            assertEquals(2, state.mediaList.size)
            assertEquals("VIDEO", state.currentMedia?.type)
        }

    @Test
    fun `advanceToNextMedia cycles through media list`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val jsonConfig =
                """
                {
                    "version": 1,
                    "media": [
                        {"id": 1, "type": "VIDEO", "url": "video1.mp4", "durationSec": 15, "order": 1},
                        {"id": 2, "type": "IMAGE", "url": "img1.jpg", "durationSec": 8, "order": 2}
                    ],
                    "diningModes": ["COMER_AQUI"],
                    "dispatch": "RETIRO_MOSTRADOR",
                    "defaultCustomerId": "CF",
                    "currency": {"base": "USD"},
                    "country": "PA"
                }
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(
                        content = jsonConfig,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            assertEquals(0, viewModel.uiState.value.currentMediaIndex)
            viewModel.advanceToNextMedia()
            assertEquals(1, viewModel.uiState.value.currentMediaIndex)
            viewModel.advanceToNextMedia()
            assertEquals(0, viewModel.uiState.value.currentMediaIndex)
        }

    @Test
    fun `loadConfig on server failure sets isOffline when mediaList is empty`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isOffline)
            assertTrue(state.mediaList.isEmpty())
        }

    @Test
    fun `onSecretTap opens admin dialog after 5 taps`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val emptyConfigJson =
                """
                {
                    "version": 1,
                    "media": [],
                    "diningModes": [],
                    "dispatch": "RETIRO_MOSTRADOR",
                    "defaultCustomerId": "CF",
                    "currency": {"base": "USD"},
                    "country": "PA"
                }
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(
                        content = emptyConfigJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            assertFalse(viewModel.uiState.value.isAdminDialogOpen)
            repeat(4) { viewModel.onSecretTap() }
            assertFalse(viewModel.uiState.value.isAdminDialogOpen)
            viewModel.onSecretTap() // 5th tap
            assertTrue(viewModel.uiState.value.isAdminDialogOpen)

            viewModel.dismissAdminDialog()
            assertFalse(viewModel.uiState.value.isAdminDialogOpen)
        }

    @Test
    fun `submitAdminUnlock calls unlock API and invokes callback on success`() =
        runTest(testDispatcher) {
            val emptyConfigJson =
                """
                {
                    "version": 1,
                    "media": [],
                    "diningModes": [],
                    "dispatch": "RETIRO_MOSTRADOR",
                    "defaultCustomerId": "CF",
                    "currency": {"base": "USD"},
                    "country": "PA"
                }
                """.trimIndent()
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.contains("unlock")) {
                        respond(content = "", status = HttpStatusCode.NoContent)
                    } else {
                        respond(
                            content = emptyConfigJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            viewModel.onSecretTap()
            viewModel.onAdminPasswordChanged("admin123")

            var unlocked = false
            val job = viewModel.submitAdminUnlock(onUnlocked = { unlocked = true })
            job.join()

            assertTrue(unlocked)
            assertFalse(viewModel.uiState.value.isAdminDialogOpen)
            assertNull(viewModel.uiState.value.adminErrorMessage)
        }

    @Test
    fun `503 shows the server reason on the out-of-service banner`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val message = "El kiosco no está habilitado en esta empresa (falta migración)"
            val engine =
                MockEngine {
                    respond(
                        content = """{"error":"$message"}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val viewModel = AttractViewModel(client, storage)
            viewModel.loadConfigJob?.join()

            val state = viewModel.uiState.value
            assertTrue(state.isOffline)
            assertEquals(message, state.outOfServiceMessage)
        }
}
