package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.FakePendingPaymentDao
import com.amaxonia.kiosk.testutil.RecordingApi
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.yappyStatusJson
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckoutSessionTest {
    private val quote: KioskQuoteResponse = Json { ignoreUnknownKeys = true }.decodeFromString(quoteJson())
    private val charge = YappyCharge("ord-uuid-1234", "YP-1", "hash", Money.fromString("9.10"), 180)

    private val cleanupJob: Job = SupervisorJob()
    private val cleanupScope = CoroutineScope(cleanupJob + Dispatchers.Default)

    private fun api() =
        RecordingApi { request ->
            if (request.method.value == "DELETE") respond("", HttpStatusCode.NoContent) else json(yappyStatusJson("PENDING"))
        }

    @Test
    fun `clear cancels an in-flight card payment and a pending Yappy charge, and forgets the quote`() =
        runBlocking {
            val api = api()
            val terminal = FakePaymentTerminal()
            val checkout = CheckoutOrderUseCase(api.client, terminal, FakePendingPaymentDao())
            val session = CheckoutSession(terminal, YappyCheckoutUseCase(api.client, checkout), cleanupScope)
            session.storeQuote(CheckoutOrderUseCase.buildQuoteRequest(OrderGraph()), quote)
            session.setCardPaymentInFlight(true)
            session.setActiveYappyCharge(quote, charge)

            session.clear()
            cleanupJob.children.toList().joinAll()

            assertEquals(1, terminal.cancelCalls)
            assertEquals(1, api.requestsTo("DELETE", "/yappy/YP-1").size)
            assertNull(session.quote.value)
        }

    @Test
    fun `abort without in-flight payment does nothing`() =
        runBlocking {
            val api = api()
            val terminal = FakePaymentTerminal()
            val checkout = CheckoutOrderUseCase(api.client, terminal, FakePendingPaymentDao())
            val session = CheckoutSession(terminal, YappyCheckoutUseCase(api.client, checkout), cleanupScope)

            session.abortInFlightPayment()
            cleanupJob.children.toList().joinAll()

            assertEquals(0, terminal.cancelCalls)
            assertEquals(0, api.requests.size)
        }
}
