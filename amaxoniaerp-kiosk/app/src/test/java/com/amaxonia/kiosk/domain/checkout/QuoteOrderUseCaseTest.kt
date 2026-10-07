package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.FakePendingPaymentDao
import com.amaxonia.kiosk.testutil.RecordingApi
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class QuoteOrderUseCaseTest {
    private val orderGraph = OrderGraph().apply { addLine(sampleItem) }

    private fun TestScope.build(
        api: RecordingApi,
        now: Instant = Instant.parse("2026-10-05T12:00:00Z"),
    ): Pair<QuoteOrderUseCase, CheckoutSession> {
        val checkout = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), FakePendingPaymentDao())
        val session = CheckoutSession(FakePaymentTerminal(), YappyCheckoutUseCase(api.client, checkout), backgroundScope)
        return QuoteOrderUseCase(checkout, session) { now } to session
    }

    @Test
    fun `same cart is quoted once and the quote is reused by every payment method`() =
        runTest {
            val api = RecordingApi { json(quoteJson()) }
            val (quoteOrder, session) = build(api)

            val first = quoteOrder(orderGraph).getOrThrow()
            val second = quoteOrder(orderGraph).getOrThrow()

            assertEquals(first, second)
            assertEquals(1, api.requests.size)
            assertEquals(first, session.quote.value)
        }

    @Test
    fun `changing the cart re-quotes with a new idempotency key`() =
        runTest {
            val api = RecordingApi { json(quoteJson()) }
            val (quoteOrder, _) = build(api)

            quoteOrder(orderGraph)
            orderGraph.addLine(sampleItem)
            quoteOrder(orderGraph)

            assertEquals(2, api.requests.size)
            assertNotEquals(api.requests[0].headers["Idempotency-Key"], api.requests[1].headers["Idempotency-Key"])
        }

    @Test
    fun `retry after a failed quote reuses the idempotency key`() =
        runTest {
            var calls = 0
            val api = RecordingApi { if (calls++ == 0) respond("down", HttpStatusCode.ServiceUnavailable) else json(quoteJson()) }
            val (quoteOrder, _) = build(api)

            assertTrue(quoteOrder(orderGraph).isFailure)
            assertTrue(quoteOrder(orderGraph).isSuccess)

            assertEquals(api.requests[0].headers["Idempotency-Key"], api.requests[1].headers["Idempotency-Key"])
        }

    @Test
    fun `expired quote is discarded and re-quoted with a fresh key`() =
        runTest {
            val api = RecordingApi { json(quoteJson(expiresAt = "2026-10-05T11:00:00Z")) }
            val (quoteOrder, _) = build(api)

            quoteOrder(orderGraph)
            quoteOrder(orderGraph)

            assertEquals(2, api.requests.size)
            assertNotEquals(api.requests[0].headers["Idempotency-Key"], api.requests[1].headers["Idempotency-Key"])
        }

    @Test
    fun `quote about to expire is re-quoted before paying`() =
        runTest {
            // Expires 30 s after "now": inside the safety margin, so it is not reused.
            val api = RecordingApi { json(quoteJson(expiresAt = "2026-10-05T12:00:30Z")) }
            val (quoteOrder, _) = build(api)

            quoteOrder(orderGraph)
            quoteOrder(orderGraph)

            assertEquals(2, api.requests.size)
        }

    @Test
    fun `server-local expiry without zone is understood`() {
        val zone = ZoneId.of("America/Panama")
        assertEquals(
            Instant.parse("2026-10-05T17:10:00Z"),
            QuoteOrderUseCase.parseExpiry("2026-10-05T12:10:00", zone),
        )
        assertEquals(Instant.parse("2026-10-05T17:10:00Z"), QuoteOrderUseCase.parseExpiry("2026-10-05T17:10:00Z", zone))
        assertEquals(Instant.parse("2026-10-05T17:10:00Z"), QuoteOrderUseCase.parseExpiry("2026-10-05T12:10:00-05:00", zone))
        assertNull(QuoteOrderUseCase.parseExpiry("mañana", zone))
    }
}
