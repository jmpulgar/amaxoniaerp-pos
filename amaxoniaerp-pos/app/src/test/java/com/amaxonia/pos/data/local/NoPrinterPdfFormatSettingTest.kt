package com.amaxonia.pos.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.domain.model.printer.NoPrinterPdfFormat
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
class NoPrinterPdfFormatSettingTest {
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
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
    }

    @Test
    fun defaultFormatIsFacturaCarta() =
        runTest {
            val format = store.readNoPrinterPdfFormat()
            assertEquals(NoPrinterPdfFormat.FACTURA_CARTA, format)
        }

    @Test
    fun saveAndReadTicketTermico() =
        runTest {
            store.saveNoPrinterPdfFormat(NoPrinterPdfFormat.TICKET_TERMICO)
            val format = store.readNoPrinterPdfFormat()
            assertEquals(NoPrinterPdfFormat.TICKET_TERMICO, format)
            assertEquals(NoPrinterPdfFormat.TICKET_TERMICO, store.noPrinterPdfFormatFlow().first())
        }
}
