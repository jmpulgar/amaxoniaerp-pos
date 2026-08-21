package com.amaxonia.pos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Invariantes de la sesion persistida (TASK-082): snapshots seguros, migracion
 * del legacy en prefs, tolerancia a datos corruptos y limpieza al cerrar sesion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalStoreAuthSessionTest {
    private lateinit var store: LocalStore
    private lateinit var secure: FakeSecureStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        secure = FakeSecureStore()
        store = LocalStore(context, secure)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking { store.clearAuthSession() }
    }

    @Test
    fun `save y read de sesion de empresa via almacen seguro`() =
        runTest {
            val session = testSession()

            store.saveCompanySession(session)
            val read = store.readCompanySession()

            assertEquals(session, read)
            assertNotNull(secure.values[LocalStore.SECURE_COMPANY_SESSION])
            assertNull(store.dataStore.data.first()[store.companySessionKey])
        }

    @Test
    fun `snapshot legacy en prefs migra al almacen seguro y desaparece del legacy`() =
        runTest {
            val session = testSession()
            val legacyJson = AppJson.encodeToString(session)
            store.dataStore.edit { prefs -> prefs[store.companySessionKey] = legacyJson }

            val read = store.readCompanySession()

            assertEquals(session, read)
            assertEquals(legacyJson, secure.values[LocalStore.SECURE_COMPANY_SESSION])
            assertNull(store.dataStore.data.first()[store.companySessionKey])
        }

    @Test
    fun `una sesion corrupta en el almacen seguro devuelve null sin lanzar`() =
        runTest {
            secure.values[LocalStore.SECURE_COMPANY_SESSION] = "{no-json"

            assertNull(store.readCompanySession())
        }

    @Test
    fun `un almacen seguro ilegible devuelve null sin lanzar`() =
        runTest {
            secure.failReads = true

            assertNull(store.readCompanySession())
        }

    @Test
    fun `clearAuthSession limpia sesion y configuracion de salon cacheada`() =
        runTest {
            store.saveCompanySession(testSession())
            store.cacheAreas(cajaId = "caja-1", sucursalId = 3, areas = listOf(AREA))
            assertNotNull(store.readCachedAreas("caja-1"))

            store.clearAuthSession()

            assertNull(store.readCompanySession())
            assertNull(store.readCachedAreas("caja-1"))
        }

    @Test
    fun `el tenant canonico deriva de la empresa de la sesion`() =
        runTest {
            store.saveCompanySession(testSession())

            assertEquals(SaleTenant.idFor(7), store.currentTenantId())
            val tenant = store.currentTenant()
            assertNotNull(tenant)
            assertEquals("Empresa Test", tenant?.label)
            assertEquals("db_admin", tenant?.adminDb)
        }

    private fun testSession() =
        CompanySessionSnapshot(
            token = "token-1",
            company =
                CompanyDetailsSnapshot(
                    id = 7,
                    name = "Empresa Test",
                    adminDb = "db_admin",
                    accountingDb = "db_contable",
                    payrollDb = "db_nomina",
                ),
        )

    private class FakeSecureStore : SecureKeyValueStore {
        val values = mutableMapOf<String, String>()
        var failReads: Boolean = false

        override fun readString(key: String): String? {
            if (failReads) error("keystore no disponible")
            return values[key]
        }

        override fun writeString(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private companion object {
        val AREA =
            com.amaxonia.pos.domain.model.mesas.Area(
                id = 1,
                nombre = "Salon principal",
                orden = 1,
                cantidadMesasActivas = 2,
            )
    }
}
