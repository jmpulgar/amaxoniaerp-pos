package com.amaxonia.kiosk.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KioskApiClientTest {
    private lateinit var tokenStorage: KioskTokenStorage

    @Before
    fun setUp() {
        tokenStorage = KioskTokenStorage()
        tokenStorage.clear()
    }

    @Test
    fun `pair calls pairing endpoint, parses response and saves token to storage`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/kiosk/pairing", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                                "deviceId": "dev-001",
                                "deviceToken": "mock-token-xyz",
                                "deviceName": "Kiosko Principal",
                                "prefix": "K1"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result =
                apiClient.pair(
                    serverUrl = "http://localhost:8080",
                    countryCode = "PA",
                    companyDb = "momi_administrativo",
                    pairingCode = "12345678",
                )

            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("dev-001", response.deviceId)
            assertEquals("mock-token-xyz", response.deviceToken)
            assertEquals("K1", response.prefix)

            assertEquals("mock-token-xyz", tokenStorage.deviceToken)
            assertTrue(tokenStorage.isPaired())
        }

    @Test
    fun `getConfig with new data returns Success and stores etag`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "token-abc"

            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/kiosk/config", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                                "version": 3,
                                "brandColor": "#D32F2F",
                                "logoUrl": null,
                                "media": [],
                                "diningModes": ["COMER_AQUI", "PARA_LLEVAR"],
                                "dispatch": "RETIRO_MOSTRADOR",
                                "defaultCustomerId": "1",
                                "currency": {
                                    "base": "USD",
                                    "secondary": "Bs",
                                    "rate": "36.50"
                                },
                                "country": "PA"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ETag to listOf("\"cfg-v3-hash\""),
                            ),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result = apiClient.getConfig()
            assertTrue(result is NetworkResult.Success)
            val success = result as NetworkResult.Success
            assertEquals(3, success.data.version)
            assertEquals("\"cfg-v3-hash\"", success.etag)
            assertEquals("\"cfg-v3-hash\"", tokenStorage.configEtag)
        }

    @Test
    fun `getConfig with matching If-None-Match returns NotModified`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "token-abc"
            tokenStorage.configEtag = "\"cfg-v3-hash\""

            val mockEngine =
                MockEngine { request ->
                    assertEquals("\"cfg-v3-hash\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result = apiClient.getConfig(ifNoneMatch = "\"cfg-v3-hash\"")
            assertTrue(result is NetworkResult.NotModified)
        }

    @Test
    fun `quoteOrder sends idempotency key and parses response`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "token-abc"

            val mockEngine =
                MockEngine { request ->
                    assertEquals("idemp-key-123", request.headers["Idempotency-Key"])
                    respond(
                        content =
                            """
                            {
                                "orderId": "ord-999",
                                "formattedOrderNumber": "K1-001",
                                "subtotal": "10.00",
                                "tax": "0.70",
                                "total": "10.70",
                                "expiresAt": "2026-10-05T21:00:00",
                                "diningMode": "COMER_AQUI",
                                "tableTent": null,
                                "customerId": "1",
                                "lines": []
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result =
                apiClient.quoteOrder(
                    idempotencyKey = "idemp-key-123",
                    request =
                        KioskQuoteRequest(
                            diningMode = "COMER_AQUI",
                            lines =
                                listOf(
                                    KioskQuoteLineRequest(itemId = 10, qty = 2),
                                ),
                        ),
                )

            assertTrue(result.isSuccess)
            val quote = result.getOrThrow()
            assertEquals("ord-999", quote.orderId)
            assertEquals("K1-001", quote.formattedOrderNumber)
            assertEquals("10.70", quote.total)
        }

    @Test
    fun `payOrder accepts 202 Accepted status for PAID_PENDING_INVOICE fallback`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "token-abc"

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content =
                            """
                            {
                                "orderNumber": "K1-001",
                                "status": "PAID_PENDING_INVOICE",
                                "invoice": null,
                                "dispatch": "RETIRO_MOSTRADOR",
                                "receipt": {
                                    "companyName": "Momi Cafe",
                                    "ruc": "12345-1-12345",
                                    "dv": "42",
                                    "address": null,
                                    "orderNumber": "K1-001",
                                    "diningMode": "COMER_AQUI",
                                    "tableTent": null,
                                    "customerName": "Consumidor Final",
                                    "customerId": "1",
                                    "date": "2026-10-05 15:30:00",
                                    "lines": [],
                                    "subtotal": "10.00",
                                    "tax": "0.70",
                                    "total": "10.70",
                                    "paymentBrand": "VISA",
                                    "paymentLast4": "4242",
                                    "paymentAuthCode": "AUTH-123",
                                    "paymentReference": "REF-456"
                                }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.Accepted,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result =
                apiClient.payOrder(
                    orderId = "ord-999",
                    request =
                        KioskPaymentRequest(
                            transactionId = "tx-1",
                            authCode = "AUTH-123",
                            reference = "REF-456",
                            last4 = "4242",
                            brand = "VISA",
                            amount = "10.70",
                            method = "CARD",
                        ),
                )

            assertTrue(result.isSuccess)
            val payment = result.getOrThrow()
            assertEquals("K1-001", payment.orderNumber)
            assertEquals("PAID_PENDING_INVOICE", payment.status)
            assertNotNull(payment.receipt)
        }

    @Test
    fun `unlock returns true on 204 No Content`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "token-abc"

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "",
                        status = HttpStatusCode.NoContent,
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result = apiClient.unlock("admin123")
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow())
        }

    @Test
    fun `401 Unauthorized clears token and throws KioskAuthenticationException`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.deviceToken = "revoked-token"
            tokenStorage.deviceId = "dev-1"

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "{\"error\":\"Token revoked\"}",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = KioskHttpClientFactory.create(tokenStorage, mockEngine)
            val apiClient = KioskApiClient(httpClient, tokenStorage)

            val result = apiClient.unlock("wrong")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KioskAuthenticationException)
            assertFalse(tokenStorage.isPaired())
        }

    private fun yappyClient(
        handler:
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData,
    ): Pair<KioskApiClient, MockEngine> {
        tokenStorage.serverUrl = "http://localhost:8080"
        tokenStorage.deviceToken = "token-abc"
        val engine = MockEngine { request -> handler(request) }
        return KioskApiClient(KioskHttpClientFactory.create(tokenStorage, engine), tokenStorage) to engine
    }

    @Test
    fun `createYappyCharge posts to the order and parses the charge`() =
        runTest {
            val (client, engine) =
                yappyClient {
                    respond(
                        content = """{"transactionId":"YP-1","qrHash":"hash-1","amount":"12.50","expiresInSec":180}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val charge = client.createYappyCharge("ord-1").getOrThrow()

            val request = engine.requestHistory.single()
            assertEquals("POST", request.method.value)
            assertEquals("/api/v1/kiosk/orders/ord-1/yappy", request.url.encodedPath)
            assertEquals("Bearer token-abc", request.headers[HttpHeaders.Authorization])
            assertEquals("hash-1", charge.qrHash)
            assertEquals("12.50", charge.amount)
            assertEquals(180, charge.expiresInSec)
        }

    @Test
    fun `createYappyCharge maps 409 to KioskConflictException and 503 keeps the status code and error message`() =
        runTest {
            val (conflictClient, _) =
                yappyClient {
                    respond(
                        """{"error":"Orden expirada"}""",
                        HttpStatusCode.Conflict,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val conflict = conflictClient.createYappyCharge("ord-1").exceptionOrNull()
            assertTrue(conflict is KioskConflictException)
            assertEquals("Orden expirada", conflict?.message)

            val (unavailableClient, _) =
                yappyClient {
                    respond(
                        """{"error":"Yappy no configurado"}""",
                        HttpStatusCode.ServiceUnavailable,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val unavailable = unavailableClient.createYappyCharge("ord-1").exceptionOrNull() as KioskApiException
            assertEquals(503, unavailable.statusCode)
            assertTrue(unavailable.message.orEmpty().contains("Yappy no configurado"))
        }

    @Test
    fun `getYappyStatus and cancelYappyCharge hit the transaction resource`() =
        runTest {
            val (client, engine) =
                yappyClient { request ->
                    if (request.method.value == "DELETE") {
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        respond(
                            """{"transactionId":"YP-1","status":"COMPLETED"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }

            assertEquals("COMPLETED", client.getYappyStatus("ord-1", "YP-1").getOrThrow().status)
            assertTrue(client.cancelYappyCharge("ord-1", "YP-1").isSuccess)

            assertEquals(listOf("GET", "DELETE"), engine.requestHistory.map { it.method.value })
            assertTrue(engine.requestHistory.all { it.url.encodedPath == "/api/v1/kiosk/orders/ord-1/yappy/YP-1" })
        }

    @Test
    fun `getConfig parses paymentMethods`() =
        runTest {
            val (client, _) =
                yappyClient {
                    respond(
                        """{"version":1,"paymentMethods":["CARD","YAPPY"],"dispatch":"MESAS","diningModes":["COMER_AQUI"]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val config = (client.getConfig() as NetworkResult.Success).data
            assertEquals(listOf("CARD", "YAPPY"), config.paymentMethods)
            assertEquals("MESAS", config.dispatch)
        }

    @Test
    fun `relative asset paths are resolved against the paired server`() =
        runTest {
            val (client, _) =
                yappyClient {
                    val body =
                        if (it.url.encodedPath.endsWith("/catalog")) {
                            """{"categories":[{"id":1,"name":"B","iconUrl":"https://cdn.example/i.png"}],""" +
                                """"items":[{"id":1,"categoryId":1,"name":"H","description":null,"price":"1.00",""" +
                                """"taxRate":"7.00","imageUrl":"/api/data/PA/db/item/b.jpg","soldOut":false,"modifierGroups":[]}]}"""
                        } else {
                            """{"logoUrl":"/api/data/PA/db/logo.png",""" +
                                """"media":[{"type":"IMAGE","url":"/api/data/PA/db/banners/a.jpg","durationSec":5}]}"""
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }

            val catalog = (client.getCatalog() as NetworkResult.Success).data
            assertEquals("http://localhost:8080/api/data/PA/db/item/b.jpg", catalog.items.single().imageUrl)
            assertEquals("https://cdn.example/i.png", catalog.categories.single().iconUrl)

            val config = (client.getConfig() as NetworkResult.Success).data
            assertEquals("http://localhost:8080/api/data/PA/db/logo.png", config.logoUrl)
            assertEquals("http://localhost:8080/api/data/PA/db/banners/a.jpg", config.media.single().url)
        }
}
