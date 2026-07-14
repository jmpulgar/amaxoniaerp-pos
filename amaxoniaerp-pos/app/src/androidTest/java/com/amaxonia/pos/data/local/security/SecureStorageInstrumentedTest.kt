package com.amaxonia.pos.data.local.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {
    @Test
    fun storesCiphertextUsingAndroidKeystoreAndCanRemoveIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidKeystoreSecureKeyValueStore(context)
        val key = "instrumented_test_secret"
        val plaintext = "never-store-this-as-plaintext"

        store.writeString(key, plaintext)

        assertEquals(plaintext, store.readString(key))
        val raw =
            context
                .getSharedPreferences(
                    AndroidKeystoreSecureKeyValueStore.SECURE_PREFERENCES_NAME,
                    0,
                ).getString(key, null)
                .orEmpty()
        assertFalse(raw.contains(plaintext))

        store.remove(key)
        assertNull(store.readString(key))
    }
}
