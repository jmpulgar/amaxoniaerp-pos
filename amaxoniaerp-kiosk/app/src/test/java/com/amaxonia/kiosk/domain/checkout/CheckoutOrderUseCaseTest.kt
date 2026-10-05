package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskDeviceCredentials
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.data.db.PendingPayment
import com.amaxonia.kiosk.data.db.PendingPaymentDao
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.payment.DevMockPaymentTerminal
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePendingPaymentDao : PendingPaymentDao {
    val payments = mutableMapOf<String, PendingPayment>()

    override suspend fun insert(payment: PendingPayment) {
        payments[payment.orderId] = payment
    }

    override suspend fun update(payment: PendingPayment) {
        payments[payment.orderId] = payment
    }

    override suspend fun getByStatus(status: String): List<PendingPayment> {
        return payments.values.filter { it.status == status }
    }

    override suspend fun getByOrderId(orderId: String): PendingPayment? {
        return payments[orderId]
    }

    override suspend fun delete(orderId: String) {
        payments.remove(orderId)
    }
}

class CheckoutOrderUseCaseTest {
    private lateinit var tokenStorage: KioskTokenStorage
    private lateinit var orderGraph: OrderGraph
    private lateinit var fakeDao: FakePendingPaymentDao

    private val sampleItem =
        KioskItemDto(
            id = 1,
            categoryId = 1,
            name = "Combo Hamburguesa",
            description = "Con papas y soda",
            price = "8.50",
            taxRate = "7.00",
            imageUrl = null,
            soldOut = false,
            modifierGroups = emptyList(),
        )

    private val quoteResponseJson =
        """
        {
            "orderId": "ord-uuid-1234",
            "formattedOrderNumber": "K1-042",
            "subtotal": "8.50",
            "tax": "0.60",
            "total": "9.10",
            "expiresAt": "2026-10-05T18:00:00Z",
            "diningMode": "COMER_AQUI",
            "tableTent": null,
            "customerId": "CF",
            "lines": []
        }
        """.trimIndent()

    private val payResponseJson =
        """
        {
            "orderNumber": "K1-042",
            "status": "FISCAL_SUCCESS",
            "invoice": {
                "codFactura": "FAC-2026-001",
                "cufe": "CUFE123456789",
                "qr": "https://dgi.mef.gob.pa/fe/123",
                "fechaRecepcionDGI": "2026-10-05 16:30:00"
            },
            "dispatch": "RETIRO_MOSTRADOR",
            "receipt": {
                "companyName": "Amaxonia Kiosk",
                "ruc": "12345-1-12345",
                "dv": "42",
                "address": null,
                "orderNumber": "K1-042",
                "diningMode": "COMER_AQUI",
                "tableTent": null,
                "customerName": "Consumidor Final",
                "customerId": "CF",
                "date": "2026-10-05 16:30:00",
                "lines": [],
                "subtotal": "8.50",
                "tax": "0.60",
                "total": "9.10",
                "paymentBrand": "VISA",
                "paymentLast4": "4242",
                "paymentAuthCode": "AUT123456",
                "paymentReference": "REF12345"
            }
        }
        """.trimIndent()

    @Before
    fun setUp() {
        tokenStorage = KioskTokenStorage()
        tokenStorage.clear()
        tokenStorage.savePairing(
            KioskDeviceCredentials(
                deviceId = "dev-1",
                deviceToken = "token-test",
                deviceName = "K1",
                prefix = "K1",
                countryCode = "PA",
                companyDb = "test_db",
                serverUrl = "http://localhost:8080",
            ),
        )

        orderGraph = OrderGraph()
        orderGraph.addLine(sampleItem, qty = 1)
        fakeDao = FakePendingPaymentDao()
    }

    @Test
    fun `successful checkout executes quote, terminal payment, outbox insert, and backend pay`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/kiosk/orders/quote" -> {
                            respond(
                                content = quoteResponseJson,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        "/api/v1/kiosk/orders/ord-uuid-1234/pay" -> {
                            respond(
                                content = payResponseJson,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("Not Found", HttpStatusCode.NotFound)
                    }
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)
            val terminal = DevMockPaymentTerminal(shouldSucceed = true, simulatedDelayMs = 0)

            val useCase = CheckoutOrderUseCase(apiClient, terminal, fakeDao)
            val result = useCase.execute(orderGraph)

            assertTrue(result is CheckoutResult.Success)
            val success = result as CheckoutResult.Success
            assertEquals("K1-042", success.paymentResponse.orderNumber)
            assertEquals("ord-uuid-1234", success.quote.orderId)
            assertEquals("4242", success.payment.last4)

            // Verify outbox was recorded and updated to SYNCED
            val pending = fakeDao.getByOrderId("ord-uuid-1234")
            assertNotNull(pending)
            assertEquals("SYNCED", pending?.status)
        }

    @Test
    fun `quote failure aborts flow and does not call terminal`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = """{"error": "Stock insuficiente"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)
            val terminal = DevMockPaymentTerminal(shouldSucceed = true, simulatedDelayMs = 0)

            val useCase = CheckoutOrderUseCase(apiClient, terminal, fakeDao)
            val result = useCase.execute(orderGraph)

            assertTrue(result is CheckoutResult.QuoteFailed)
            assertEquals(0, fakeDao.payments.size)
        }

    @Test
    fun `terminal declined payment terminates flow without calling backend pay`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/api/v1/kiosk/orders/quote") {
                        respond(
                            content = quoteResponseJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("Should not be called", HttpStatusCode.InternalServerError)
                    }
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)
            val terminal = DevMockPaymentTerminal(shouldSucceed = false, failureReason = "FONDOS INSUFICIENTES", simulatedDelayMs = 0)

            val useCase = CheckoutOrderUseCase(apiClient, terminal, fakeDao)
            val result = useCase.execute(orderGraph)

            assertTrue(result is CheckoutResult.PaymentDeclined)
            val declined = result as CheckoutResult.PaymentDeclined
            assertEquals("FONDOS INSUFICIENTES", declined.reason)
            assertEquals(0, fakeDao.payments.size)
        }

    @Test
    fun `backend pay network failure preserves payment in Room outbox with PENDING status`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/kiosk/orders/quote" -> {
                            respond(
                                content = quoteResponseJson,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        "/api/v1/kiosk/orders/ord-uuid-1234/pay" -> {
                            respond(
                                content = """{"error": "DGI Gateway Timeout"}""",
                                status = HttpStatusCode.GatewayTimeout,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("Not Found", HttpStatusCode.NotFound)
                    }
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)
            val terminal = DevMockPaymentTerminal(shouldSucceed = true, simulatedDelayMs = 0)

            val useCase = CheckoutOrderUseCase(apiClient, terminal, fakeDao)
            val result = useCase.execute(orderGraph)

            assertTrue(result is CheckoutResult.PaidPendingSync)
            val pendingSync = result as CheckoutResult.PaidPendingSync
            assertEquals("ord-uuid-1234", pendingSync.quote.orderId)

            // Outbox preserved!
            val pending = fakeDao.getByOrderId("ord-uuid-1234")
            assertNotNull(pending)
            assertEquals("PENDING", pending?.status)
            assertEquals(1, pending?.attempts)
        }

    @Test
    fun `syncPendingPayments successfully retries pending outbox orders`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/api/v1/kiosk/orders/ord-pending-999/pay") {
                        respond(
                            content = payResponseJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("Not Found", HttpStatusCode.NotFound)
                    }
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)
            val terminal = DevMockPaymentTerminal(shouldSucceed = true, simulatedDelayMs = 0)

            fakeDao.insert(
                PendingPayment(
                    orderId = "ord-pending-999",
                    transactionId = "TXN-999",
                    authCode = "AUT999",
                    reference = "REF999",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "9.10",
                    status = "PENDING",
                    attempts = 1,
                    lastError = "Connection timeout",
                ),
            )

            val useCase = CheckoutOrderUseCase(apiClient, terminal, fakeDao)
            val syncResults = useCase.syncPendingPayments()

            assertEquals(1, syncResults.size)
            assertTrue(syncResults.first().success)
            assertEquals("ord-pending-999", syncResults.first().orderId)

            val updatedPayment = fakeDao.getByOrderId("ord-pending-999")
            assertEquals("SYNCED", updatedPayment?.status)
        }
}
