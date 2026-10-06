package com.amaxonia.kiosk.domain.session

import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.YappyCheckoutUseCase
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.FakePendingPaymentDao
import com.amaxonia.kiosk.testutil.RecordingApi
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import com.amaxonia.kiosk.ui.accessibility.AccessibilityManager
import com.amaxonia.kiosk.ui.accessibility.AccessibilityState
import com.amaxonia.kiosk.ui.idle.IdleTimerManager
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResetterTest {
    @Test
    fun `resetSession clears cart, customer, payment, accessibility and language for the next customer`() =
        runTest {
            val api = RecordingApi { json(quoteJson()) }
            val terminal = FakePaymentTerminal()
            val checkout = CheckoutOrderUseCase(api.client, terminal, FakePendingPaymentDao())
            val session = CheckoutSession(terminal, YappyCheckoutUseCase(api.client, checkout), this)
            val orderGraph =
                OrderGraph().apply {
                    addLine(sampleItem)
                    setDiningMode("PARA_LLEVAR")
                    setTableTent("12")
                    setCustomer("8-123-456", "Juan")
                }
            session.storeQuote(
                CheckoutOrderUseCase.buildQuoteRequest(orderGraph),
                Json { ignoreUnknownKeys = true }.decodeFromString(quoteJson()),
            )
            session.setCardPaymentInFlight(true)
            val completed = MutableStateFlow<CompletedOrderInfo?>(mockk(relaxed = true))
            val accessibility =
                AccessibilityManager().apply {
                    toggleAccessibleMode()
                    toggleHighContrast()
                    toggleLanguage()
                }
            val idle = IdleTimerManager().apply { setPaused(true) }
            var synced = false

            SessionResetter(orderGraph, completed, session, accessibility, idle) { synced = true }.resetSession()
            advanceUntilIdle()

            assertTrue(orderGraph.lines.value.isEmpty())
            assertEquals("COMER_AQUI", orderGraph.diningMode.value)
            assertNull(orderGraph.tableTent.value)
            assertEquals("CF", orderGraph.customerId.value)
            assertNull(completed.value)
            assertNull(session.quote.value)
            assertEquals(1, terminal.cancelCalls)
            assertEquals(AccessibilityState(), accessibility.state.value)
            assertFalse(idle.state.value.isPaused)
            assertTrue(synced)
        }
}
