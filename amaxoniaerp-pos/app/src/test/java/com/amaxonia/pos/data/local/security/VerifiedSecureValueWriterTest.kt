package com.amaxonia.pos.data.local.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedSecureValueWriterTest {
    @Test
    fun acceptsOnlyValuesThatCanBeReadBackUnchanged() {
        val store = InMemorySecureStore()

        VerifiedSecureValueWriter(store).write("token", "value")

        assertEquals("value", store.readString("token"))
    }

    @Test
    fun rejectsAWriteThatCannotBeVerified() {
        val store = InMemorySecureStore(readOverride = "different")

        assertThrows(SecureStorageException::class.java) {
            VerifiedSecureValueWriter(store).write("token", "value")
        }
    }

    private class InMemorySecureStore(
        private val readOverride: String? = null,
    ) : SecureKeyValueStore {
        private val values = mutableMapOf<String, String>()

        override fun readString(key: String): String? = readOverride ?: values[key]

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
}
