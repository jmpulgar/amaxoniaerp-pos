package com.amaxonia.kiosk.ui.cajasetup

import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.testSession
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
class CajaSetupViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val storage = KioskTokenStorage().apply { saveSession(testSession()) }

    private val cajasJson =
        """[{"idCaja":"c-1","codCaja":"001","descripcion":"Caja Kiosco","estatus":1,"idSucursal":1,""" +
            """"serieCaja":"A","sucursalNombre":"Centro"},""" +
            """{"idCaja":"c-2","codCaja":"002","descripcion":"Caja inactiva","estatus":0,"idSucursal":1,"serieCaja":"B"},""" +
            """{"idCaja":"c-3","codCaja":"003","descripcion":"Caja Terraza","estatus":1,"idSucursal":2,"serieCaja":"C"}]"""

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(
        previousCajaInvalid: Boolean = false,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): CajaSetupViewModel {
        val engine = MockEngine { request -> handler(request) }
        val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
        return CajaSetupViewModel(client, storage, previousCajaInvalid).also { it.loadJob?.join() }
    }

    @Test
    fun `lists only active cajas and defaults the prefix to K1`() =
        runTest(testDispatcher) {
            val vm = viewModel { json(cajasJson) }

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals(listOf("c-1", "c-3"), state.cajas.map { it.idCaja })
            assertNull(state.selectedCajaId)
            assertEquals("K1", state.prefix)
            assertEquals("Compañía Prueba", state.companyName)
            assertEquals("cajero1", state.username)
        }

    @Test
    fun `a single active caja is preselected`() =
        runTest(testDispatcher) {
            val vm = viewModel { json("""[{"idCaja":"only","descripcion":"Única","estatus":1}]""") }

            assertEquals("only", vm.uiState.value.selectedCajaId)
        }

    @Test
    fun `prefix is uppercased, filtered to A-Z 0-9 and capped at 5`() =
        runTest(testDispatcher) {
            val vm = viewModel { json(cajasJson) }

            vm.onPrefixChanged("k-2 ñ_terraza")

            assertEquals("K2TER", vm.uiState.value.prefix)
            vm.onPrefixChanged("")
            assertFalse(vm.uiState.value.isPrefixValid)
        }

    @Test
    fun `start persists caja and prefix and emits Started`() =
        runTest(testDispatcher) {
            val vm = viewModel { json(cajasJson) }
            vm.onCajaSelected("c-3")
            vm.onPrefixChanged("k2")
            vm.start()

            assertEquals(CajaSetupEvent.Started, vm.events.first())
            assertTrue(storage.hasCaja())
            assertEquals("c-3", storage.cajaId)
            assertEquals("Caja Terraza", storage.cajaName)
            assertEquals("K2", storage.prefix)
        }

    @Test
    fun `start without a caja shows validation and stores nothing`() =
        runTest(testDispatcher) {
            val vm = viewModel { json(cajasJson) }

            vm.start()

            assertTrue(vm.uiState.value.showValidation)
            assertFalse(storage.hasCaja())
        }

    @Test
    fun `load errors are shown and retry reloads`() =
        runTest(testDispatcher) {
            var fail = true
            val vm =
                viewModel {
                    if (fail) json("""{"error":"Boom"}""", HttpStatusCode.InternalServerError) else json(cajasJson)
                }

            assertEquals(CajaLoadError.Server("Boom"), vm.uiState.value.loadError)

            fail = false
            vm.loadCajas().join()

            assertNull(vm.uiState.value.loadError)
            assertEquals(2, vm.uiState.value.cajas.size)
        }

    @Test
    fun `401 on api cajas logs the kiosk out`() =
        runTest(testDispatcher) {
            viewModel { json("""{"error":"Token inválido"}""", HttpStatusCode.Unauthorized) }

            assertFalse(storage.isLoggedIn())
        }

    @Test
    fun `logout clears the session and emits LoggedOut`() =
        runTest(testDispatcher) {
            val vm = viewModel(previousCajaInvalid = true) { json(cajasJson) }
            assertTrue(vm.uiState.value.previousCajaInvalid)
            vm.logout()

            assertEquals(CajaSetupEvent.LoggedOut, vm.events.first())
            assertFalse(storage.isLoggedIn())
        }
}
