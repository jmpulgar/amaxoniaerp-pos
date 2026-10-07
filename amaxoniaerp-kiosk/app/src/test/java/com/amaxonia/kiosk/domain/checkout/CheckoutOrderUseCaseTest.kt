package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.data.db.PendingPayment
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.domain.payment.PaymentResult
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.FakePendingPaymentDao
import com.amaxonia.kiosk.testutil.RecordingApi
import com.amaxonia.kiosk.testutil.bodyText
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.payResponseJson
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckoutOrderUseCaseTest {
    private lateinit var orderGraph: OrderGraph
    private lateinit var fakeDao: FakePendingPaymentDao

    private val quote: KioskQuoteResponse = Json { ignoreUnknownKeys = true }.decodeFromString(quoteJson())

    @Before
    fun setUp() {
        orderGraph = OrderGraph()
        orderGraph.addLine(sampleItem, qty = 1)
        fakeDao = FakePendingPaymentDao()
    }

    private fun happyApi() =
        RecordingApi { request ->
            when (request.url.encodedPath) {
                "/api/v1/kiosk/orders/quote" -> json(quoteJson())
                "/api/v1/kiosk/orders/ord-uuid-1234/pay" -> json(payResponseJson)
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

    @Test
    fun `quote is a separate step and sends the idempotency key`() =
        runTest {
            val api = happyApi()
            val useCase = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), fakeDao)

            val result = useCase.quote(CheckoutOrderUseCase.buildQuoteRequest(orderGraph), "key-1")

            assertEquals("9.10", result.getOrThrow().total)
            assertEquals("key-1", api.requests.single().headers["Idempotency-Key"])
            assertTrue(fakeDao.payments.isEmpty())
        }

    @Test
    fun `payWithCard charges exactly the quote total and registers method CARD`() =
        runTest {
            val api = happyApi()
            val terminal = FakePaymentTerminal()
            var approvedCalled = false
            val useCase = CheckoutOrderUseCase(api.client, terminal, fakeDao)

            val result = useCase.payWithCard(quote, onApproved = { approvedCalled = true })

            assertTrue(result is CheckoutResult.Success)
            assertTrue(approvedCalled)
            assertEquals(Money.fromString("9.10"), terminal.lastAmount)
            val payBody = Json.parseToJsonElement(api.requestsTo("POST", "/pay").single().bodyText()).jsonObject
            assertEquals("CARD", payBody["method"]?.jsonPrimitive?.content)
            assertEquals("9.10", payBody["amount"]?.jsonPrimitive?.content)
            assertEquals(PendingPayment.STATUS_SYNCED, fakeDao.getByOrderId("ord-uuid-1234")?.status)
            assertEquals("CARD", fakeDao.getByOrderId("ord-uuid-1234")?.method)
        }

    @Test
    fun `terminal declined payment terminates flow without calling backend pay`() =
        runTest {
            val api = happyApi()
            val terminal = FakePaymentTerminal(result = PaymentResult.Declined("FONDOS INSUFICIENTES"))
            val useCase = CheckoutOrderUseCase(api.client, terminal, fakeDao)

            val result = useCase.payWithCard(quote)

            assertEquals(CheckoutResult.PaymentDeclined("FONDOS INSUFICIENTES"), result)
            assertTrue(api.requestsTo("POST", "/pay").isEmpty())
            assertEquals(0, fakeDao.payments.size)
        }

    @Test
    fun `backend pay failure preserves payment in Room outbox with PENDING status`() =
        runTest {
            val api =
                RecordingApi { request ->
                    if (request.url.encodedPath.endsWith("/pay")) {
                        json("""{"error": "DGI Gateway Timeout"}""", HttpStatusCode.GatewayTimeout)
                    } else {
                        json(quoteJson())
                    }
                }
            val useCase = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), fakeDao)

            val result = useCase.payWithCard(quote)

            assertTrue(result is CheckoutResult.PaidPendingSync)
            val pending = fakeDao.getByOrderId("ord-uuid-1234")
            assertNotNull(pending)
            assertEquals(PendingPayment.STATUS_PENDING, pending?.status)
            assertEquals(1, pending?.attempts)
            assertTrue(pending?.lastError.orEmpty().contains("DGI Gateway Timeout"))
        }

    @Test
    fun `registerPayment stores the method in the outbox`() =
        runTest {
            val api = happyApi()
            val useCase = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), fakeDao)

            useCase.registerPayment(quote, com.amaxonia.kiosk.testutil.approvedCard(), PaymentMethod.YAPPY)

            assertEquals("YAPPY", fakeDao.getByOrderId("ord-uuid-1234")?.method)
        }

    @Test
    fun `manual card payment registers the chosen card method without touching the terminal`() =
        runTest {
            val api = happyApi()
            val terminal = FakePaymentTerminal(isAvailable = false)
            val useCase = CheckoutOrderUseCase(api.client, terminal, fakeDao)

            val result = useCase.registerManualCardPayment(quote, KioskCardOption(id = 50, name = "MASTERCARD", siglas = "TDC"))

            assertTrue(result is CheckoutResult.Success)
            assertEquals(0, terminal.processCalls)
            val body = Json.parseToJsonElement(api.requests.last().bodyText()).jsonObject
            assertEquals("CARD", body["method"]?.jsonPrimitive?.content)
            assertEquals(50, body["paymentMethodId"]?.jsonPrimitive?.int)
            assertEquals(CheckoutOrderUseCase.MANUAL_REFERENCE, body["reference"]?.jsonPrimitive?.content)
            assertTrue(body["transactionId"]?.jsonPrimitive?.content.orEmpty().startsWith(CheckoutOrderUseCase.MANUAL_TRANSACTION_PREFIX))
            assertEquals(50, fakeDao.getByOrderId("ord-uuid-1234")?.paymentMethodId)
        }

    @Test
    fun `syncPendingPayments retries pending orders sending the stored method`() =
        runTest {
            val api =
                RecordingApi { request ->
                    if (request.url.encodedPath == "/api/v1/kiosk/orders/ord-pending-999/pay") {
                        json(payResponseJson)
                    } else {
                        respond("Not Found", HttpStatusCode.NotFound)
                    }
                }
            fakeDao.insert(
                PendingPayment(
                    orderId = "ord-pending-999",
                    transactionId = "YP-999",
                    authCode = "",
                    reference = "YP-999",
                    last4 = "",
                    brand = "YAPPY",
                    amount = "9.10",
                    status = PendingPayment.STATUS_PENDING,
                    attempts = 1,
                    lastError = "Connection timeout",
                    method = "YAPPY",
                ),
            )
            val useCase = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), fakeDao)

            val syncResults = useCase.syncPendingPayments()

            assertEquals(1, syncResults.size)
            assertTrue(syncResults.first().success)
            val body = Json.parseToJsonElement(api.requests.single().bodyText()).jsonObject
            assertEquals("YAPPY", body["method"]?.jsonPrimitive?.content)
            assertEquals(PendingPayment.STATUS_SYNCED, fakeDao.getByOrderId("ord-pending-999")?.status)
        }

    @Test
    fun `syncPendingPayments keeps failed orders pending and counts the attempt`() =
        runTest {
            val api = RecordingApi { respond("down", HttpStatusCode.ServiceUnavailable) }
            fakeDao.insert(
                PendingPayment(
                    orderId = "o-1",
                    transactionId = "T",
                    authCode = "A",
                    reference = "R",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "1.00",
                    status = PendingPayment.STATUS_PENDING,
                ),
            )
            val useCase = CheckoutOrderUseCase(api.client, FakePaymentTerminal(), fakeDao)

            val results = useCase.syncPendingPayments()

            assertFalse(results.single().success)
            assertEquals(PendingPayment.STATUS_PENDING, fakeDao.getByOrderId("o-1")?.status)
            assertEquals(1, fakeDao.getByOrderId("o-1")?.attempts)
        }
}
