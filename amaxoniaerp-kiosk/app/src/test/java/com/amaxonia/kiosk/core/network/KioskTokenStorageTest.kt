package com.amaxonia.kiosk.core.network

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException

class KioskTokenStorageTest {
    /** Minimal in-memory SharedPreferences whose reads can be made to fail like a corrupted keystore. */
    private class FlakyPrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        var failReads = false

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? {
            if (failReads) throw SecurityException("Could not decrypt value", AEADBadTagException("tag mismatch"))
            return data[key] as String? ?: defValue
        }

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ) = defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ) = defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ) = defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ) = defValue

        override fun contains(key: String?) = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val ops = mutableListOf<() -> Unit>()

            override fun putString(
                key: String,
                value: String?,
            ) = apply { ops += { data[key] = value } }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ) = this

            override fun putInt(
                key: String?,
                value: Int,
            ) = this

            override fun putLong(
                key: String?,
                value: Long,
            ) = this

            override fun putFloat(
                key: String?,
                value: Float,
            ) = this

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ) = this

            override fun remove(key: String) = apply { ops += { data.remove(key) } }

            override fun clear() = apply { ops += { data.clear() } }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                ops.forEach { it() }
                ops.clear()
            }
        }
    }

    private class FakeFactory(
        var createFailures: Int = 0,
        var alwaysFail: Boolean = false,
    ) : SecurePrefsFactory {
        var creates = 0
        var destroys = 0
        var current = FlakyPrefs()

        override fun create(): SharedPreferences {
            creates++
            if (alwaysFail || createFailures-- > 0) throw KeyStoreException("Keystore corrupted after OTA")
            return current
        }

        override fun destroy() {
            destroys++
            current = FlakyPrefs()
        }
    }

    private val session =
        KioskSession(
            token = "token-1",
            userId = 7,
            username = "cajero1",
            companyId = 2,
            companyName = "Compañía Prueba",
            companyDb = "t_prueba",
            countryCode = "PA",
            serverUrl = "http://localhost:8080/",
        )

    @Test
    fun `opening is lazy - nothing touches the keystore until warmUp or first access`() {
        val factory = FakeFactory()
        val storage = KioskTokenStorage(prefsFactory = factory)

        assertEquals(0, factory.creates)
        assertFalse(storage.isReady.value)

        storage.warmUp()

        assertEquals(1, factory.creates)
        assertTrue(storage.isReady.value)
    }

    @Test
    fun `keystore failure on open deletes the store and recreates it`() {
        val factory = FakeFactory(createFailures = 1)
        val storage = KioskTokenStorage(prefsFactory = factory)

        storage.warmUp()
        storage.saveSession(session)

        assertEquals(1, factory.destroys)
        assertEquals(2, factory.creates)
        assertEquals("token-1", factory.current.data[KioskTokenStorage.KEY_AUTH_TOKEN])
        assertTrue(storage.isReady.value)
    }

    @Test
    fun `corrupted ciphertext on read recreates the store and returns logged out instead of crashing`() {
        val factory = FakeFactory()
        val storage = KioskTokenStorage(prefsFactory = factory)
        storage.saveSession(session)
        val fresh = KioskTokenStorage(prefsFactory = factory)
        factory.current.failReads = true

        assertNull(fresh.authToken)
        assertFalse(fresh.isLoggedIn())
        assertEquals(1, factory.destroys)
        assertTrue(factory.current.data.isEmpty())
    }

    @Test
    fun `unrecoverable keystore falls back to in-memory storage for this process`() {
        val factory = FakeFactory(alwaysFail = true)
        val storage = KioskTokenStorage(prefsFactory = factory)

        storage.warmUp()
        storage.saveSession(session)

        assertTrue(storage.isReady.value)
        assertEquals("token-1", storage.authToken)
        assertTrue(storage.isLoggedIn())
    }

    @Test
    fun `session and caja are persisted with the documented keys`() {
        val factory = FakeFactory()
        val storage = KioskTokenStorage(prefsFactory = factory)

        storage.saveSession(session)
        assertTrue(storage.isLoggedIn())
        assertFalse(storage.hasCaja())

        storage.saveCaja(cajaId = "c-1", cajaName = "Caja Kiosco", prefix = "K2")

        assertTrue(storage.hasCaja())
        val data = factory.current.data
        assertEquals("2", data[KioskTokenStorage.KEY_COMPANY_ID])
        assertEquals("t_prueba", data[KioskTokenStorage.KEY_COMPANY_DB])
        assertEquals("c-1", data[KioskTokenStorage.KEY_CAJA_ID])
        assertEquals("Caja Kiosco", data[KioskTokenStorage.KEY_CAJA_NAME])
        assertEquals("K2", data[KioskTokenStorage.KEY_PREFIX])
        assertEquals("http://localhost:8080/", data[KioskTokenStorage.KEY_SERVER_URL])
    }

    @Test
    fun `clearSession logs out but keeps server URL, country and prefix`() {
        val storage = KioskTokenStorage(prefsFactory = FakeFactory())
        storage.saveSession(session)
        storage.saveCaja(cajaId = "c-1", cajaName = "Caja Kiosco", prefix = "K2")

        storage.clearSession()

        assertFalse(storage.isLoggedIn())
        assertFalse(storage.hasCaja())
        assertNull(storage.cajaId)
        assertEquals("http://localhost:8080/", storage.serverUrl)
        assertEquals("PA", storage.countryCode)
        assertEquals("K2", storage.prefix)
    }

    @Test
    fun `clearCaja keeps the login`() {
        val storage = KioskTokenStorage(prefsFactory = FakeFactory())
        storage.saveSession(session)
        storage.saveCaja(cajaId = "c-1", cajaName = "Caja Kiosco", prefix = "K2")

        storage.clearCaja()

        assertTrue(storage.isLoggedIn())
        assertFalse(storage.hasCaja())
    }

    @Test
    fun `a 0_0_2 install with only pairing credentials is sent to login without crashing`() {
        val factory = FakeFactory()
        factory.current.data.putAll(
            mapOf(
                "kiosk_device_id" to "dev-1",
                "kiosk_device_token" to "legacy-token",
                "kiosk_device_name" to "Kiosco 1",
                "kiosk_prefix" to "K1",
                "kiosk_country_code" to "PA",
                "kiosk_company_db" to "demo_administrativo",
                "kiosk_server_url" to "https://api.listoerp.app/",
            ),
        )
        val storage = KioskTokenStorage(prefsFactory = factory)

        storage.warmUp()

        assertFalse(storage.isLoggedIn())
        assertFalse(storage.hasCaja())
        KioskTokenStorage.LEGACY_KEYS.forEach { assertNull(factory.current.data[it]) }
        assertNull(storage.companyDb)
        assertEquals("https://api.listoerp.app/", storage.serverUrl)
        assertEquals("K1", storage.prefix)
    }

    @Test
    fun `clear wipes everything`() {
        val storage = KioskTokenStorage(prefsFactory = FakeFactory())
        storage.saveSession(session)

        storage.clear()

        assertFalse(storage.isLoggedIn())
        assertNull(storage.serverUrl)
    }
}
