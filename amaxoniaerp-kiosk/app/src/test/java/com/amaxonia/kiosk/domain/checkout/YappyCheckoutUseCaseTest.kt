package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.data.db.PendingPayment
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.FakePendingPaymentDao
import com.amaxonia.kiosk.testutil.RecordingApi
import com.amaxonia.kiosk.testutil.bodyText
import com.amaxonia.kiosk.testutil.json
import com.amaxonia.kiosk.testutil.payResponseJson
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.yappyChargeJson
import com.amaxonia.kiosk.testutil.yappyStatusJson
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val POLL_MS = 20L

class YappyCheckoutUseCaseTest {
    private val quote: KioskQuoteResponse = Json { ignoreUnknownKeys = true }.decodeFromString(quoteJson())
    private val dao = FakePendingPaymentDao()

    /** Yappy backend double: answers status polls from [statuses] in order (last one repeats). */
    private fun yappyApi(
        statuses: List<String> = listOf("COMPLETED"),
        chargeStatus: HttpStatusCode = HttpStatusCode.OK,
        chargeBody: String = yappyChargeJson(),
    ): RecordingApi {
        var poll = 0
        return RecordingApi { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/pay") -> json(payResponseJson)
                path.endsWith("/yappy") -> json(chargeBody, chargeStatus)
                request.method.value == "DELETE" -> respond("", HttpStatusCode.NoContent)
                path.endsWith("/yappy/YP-1") -> json(yappyStatusJson(statuses[minOf(poll++, statuses.lastIndex)]))
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }
    }

    private fun useCase(api: RecordingApi) =
        YappyCheckoutUseCase(
            api.client,
            CheckoutOrderUseCase(api.client, FakePaymentTerminal(), dao),
            pollIntervalMs = POLL_MS,
        )

    private suspend fun created(useCase: YappyCheckoutUseCase): YappyCharge =
        (useCase.createCharge(quote) as YappyChargeResult.Created).charge

    @Test
    fun `completed charge is polled until confirmed and registered with method YAPPY`() =
        runBlocking {
            val api = yappyApi(statuses = listOf("PENDING", "PENDING", "COMPLETED"))
            val useCase = useCase(api)
            val charge = created(useCase)
            var confirmed = false

            val outcome = useCase.awaitPayment(quote, charge) { confirmed = true }

            assertTrue(confirmed)
            assertTrue((outcome as YappyOutcome.Paid).result is CheckoutResult.Success)
            assertEquals(3, api.requestsTo("GET", "/yappy/YP-1").size)
            val payBody = Json.parseToJsonElement(api.requestsTo("POST", "/pay").single().bodyText()).jsonObject
            assertEquals("YAPPY", payBody["method"]?.jsonPrimitive?.content)
            assertEquals("YP-1", payBody["transactionId"]?.jsonPrimitive?.content)
            assertEquals("YP-1", payBody["reference"]?.jsonPrimitive?.content)
            assertEquals("YAPPY", payBody["brand"]?.jsonPrimitive?.content)
            assertEquals("", payBody["authCode"]?.jsonPrimitive?.content)
            assertEquals("9.10", payBody["amount"]?.jsonPrimitive?.content)
            val outbox = dao.getByOrderId(quote.orderId)
            assertEquals("YAPPY", outbox?.method)
            assertEquals(PendingPayment.STATUS_SYNCED, outbox?.status)
        }

    @Test
    fun `declined charge is reported as rejected without paying`() =
        runBlocking {
            val api = yappyApi(statuses = listOf("PENDING", "DECLINED"))
            val useCase = useCase(api)

            val outcome = useCase.awaitPayment(quote, created(useCase))

            assertEquals(YappyOutcome.Rejected(YappyStatus.DECLINED), outcome)
            assertTrue(api.requestsTo("POST", "/pay").isEmpty())
        }

    @Test
    fun `charge that never completes expires after expiresInSec and is deleted`() =
        runBlocking {
            val api = yappyApi(statuses = listOf("PENDING"), chargeBody = yappyChargeJson(expiresInSec = 1))
            val useCase = useCase(api)

            val outcome = useCase.awaitPayment(quote, created(useCase))

            assertEquals(YappyOutcome.Expired, outcome)
            assertTrue(api.requestsTo("GET", "/yappy/YP-1").size > 1)
            assertEquals(1, api.requestsTo("DELETE", "/yappy/YP-1").size)
            assertTrue(dao.payments.isEmpty())
        }

    @Test
    fun `transient polling errors keep polling until a terminal state`() =
        runBlocking {
            var poll = 0
            val api =
                RecordingApi { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/pay") -> json(payResponseJson)
                        path.endsWith("/yappy") -> json(yappyChargeJson())
                        poll++ == 0 -> respond("boom", HttpStatusCode.BadGateway)
                        else -> json(yappyStatusJson("COMPLETED"))
                    }
                }
            val useCase = useCase(api)

            val outcome = useCase.awaitPayment(quote, created(useCase))

            assertTrue(outcome is YappyOutcome.Paid)
        }

    @Test
    fun `409 maps to ORDER_EXPIRED, 503 to NOT_CONFIGURED and 502 to UPSTREAM`() =
        runBlocking {
            val cases =
                mapOf(
                    HttpStatusCode.Conflict to YappyFailure.ORDER_EXPIRED,
                    HttpStatusCode.ServiceUnavailable to YappyFailure.NOT_CONFIGURED,
                    HttpStatusCode.BadGateway to YappyFailure.UPSTREAM,
                )
            cases.forEach { (status, expected) ->
                val api = yappyApi(chargeStatus = status, chargeBody = """{"error": "detalle $status"}""")
                val result = useCase(api).createCharge(quote) as YappyChargeResult.Failed
                assertEquals(expected, result.reason)
                assertTrue(result.message.contains("detalle"))
            }
        }

    @Test
    fun `charge amount different from the quote total is rejected and cancelled`() =
        runBlocking {
            val api = yappyApi(chargeBody = yappyChargeJson(amount = "1.00"))

            val result = useCase(api).createCharge(quote)

            assertEquals(YappyFailure.UPSTREAM, (result as YappyChargeResult.Failed).reason)
            assertEquals(1, api.requestsTo("DELETE", "/yappy/YP-1").size)
        }

    @Test
    fun `cancelOrSettle deletes a pending charge`() =
        runBlocking {
            val api = yappyApi(statuses = listOf("PENDING"))
            val useCase = useCase(api)

            val result = useCase.cancelOrSettle(quote, created(useCase))

            assertNull(result)
            assertEquals(1, api.requestsTo("DELETE", "/yappy/YP-1").size)
        }

    @Test
    fun `cancelOrSettle registers a charge the customer already paid instead of losing it`() =
        runBlocking {
            val api = yappyApi(statuses = listOf("COMPLETED"))
            val useCase = useCase(api)

            val result = useCase.cancelOrSettle(quote, created(useCase))

            assertTrue(result is CheckoutResult.Success)
            assertTrue(api.requestsTo("DELETE", "/yappy/YP-1").isEmpty())
            assertEquals("YAPPY", dao.getByOrderId(quote.orderId)?.method)
        }
}
