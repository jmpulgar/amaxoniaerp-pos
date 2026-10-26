package com.amaxonia.pos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.domain.model.printer.TicketFontSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TicketFontSizeSettingTest {
    private lateinit var store: LocalStore

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fakeSecure =
                object : SecureKeyValueStore {
                    private val map = mutableMapOf<String, String>()

                    override fun readString(key: String): String? = map[key]

                    override fun writeString(
                        key: String,
                        value: String,
                    ) {
                        map[key] = value
                    }

                    override fun remove(key: String) {
                        map.remove(key)
                    }
                }
            store = LocalStore(context, fakeSecure)
            store.dataStore.edit { prefs ->
                prefs.remove(store.ticketFontSizeKey)
            }
        }

    @Test
    fun defaultFontSizeIsNormal() =
        runTest {
            val size = store.readTicketFontSize()
            assertEquals(TicketFontSize.NORMAL, size)
        }

    @Test
    fun saveAndReadCompactFontSize() =
        runTest {
            store.saveTicketFontSize(TicketFontSize.COMPACT)
            val size = store.readTicketFontSize()
            assertEquals(TicketFontSize.COMPACT, size)
            assertEquals(TicketFontSize.COMPACT, store.ticketFontSizeFlow().first())
        }
}
