package com.amaxoniaerp.features.kiosk.data.yappy

import com.amaxoniaerp.features.kiosk.application.YappySessionKey
import com.amaxoniaerp.features.kiosk.application.YappySessionManager
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCharge
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCredentials
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyDevice
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyKioskConfig
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.amaxoniaerp.features.kiosk.domain.yappy.YappySessionExpiredException
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyTransactionStatus
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyUpstreamException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private typealias YappyHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/**
 * Contrato HTTP del adaptador Yappy (espejo de caja.class.php) y de la gestión de sesión:
 * parseo de token con fallbacks, forma exacta del payload del QR (headers y montos con 2
 * decimales), normalización de estados y reapertura de sesión ante 401/403.
 */
class YappyClientTest {
    private val credentials =
        YappyCredentials(
            apiKey = "api-key-123",
            secretKey = "secret-456",
            // Barra final deliberada: el cliente debe tolerarla como el rtrim del PHP.
            baseUrl = "https://api-integrationcheckout-uat.yappycloud.com/v1/",
        )
    private val device = YappyDevice(deviceId = "UNIDAD-01", groupId = "GRUPO-01")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(handler: YappyHandler): YappyClient = YappyClient(HttpClient(MockEngine { request -> handler(request) }))

    private fun MockRequestHandleScope.ok(body: String): HttpResponseData = respond(body, HttpStatusCode.OK, jsonHeaders)

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    // ─── Sesión ────────────────────────────────────────────────────────────

    @Test
    fun `openSession posts device payload with credentials and reads body token`() =
        runBlocking<Unit> {
            var captured: HttpRequestData? = null
            val yappy =
                client { req ->
                    captured = req
                    ok("""{"status":{"code":"YP-0000"},"body":{"token":"session-abc","status":"OPEN"}}""")
                }

            val token = yappy.openSession(credentials, device)

            assertEquals("session-abc", token)
            val req = captured!!
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("https://api-integrationcheckout-uat.yappycloud.com/v1/session/device", req.url.toString())
            assertEquals("api-key-123", req.headers["api-key"])
            assertEquals("secret-456", req.headers["secret-key"])
            assertEquals(null, req.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(req.bodyText()).jsonObject["body"]!!.jsonObject
            assertEquals("UNIDAD-01", body["device"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertEquals("GRUPO-01", body["group_id"]!!.jsonPrimitive.content)
        }

    @Test
    fun `openSession falls back to token, data token and session_token`() =
        runBlocking<Unit> {
            val bodies =
                mapOf(
                    """{"token":"t-root"}""" to "t-root",
                    """{"data":{"token":"t-data"}}""" to "t-data",
                    """{"session_token":"t-session"}""" to "t-session",
                )
            bodies.forEach { (body, expected) ->
                assertEquals(expected, client { ok(body) }.openSession(credentials, device))
            }
        }

    @Test
    fun `openSession without token or with HTTP error is an upstream error`() =
        runBlocking<Unit> {
            assertFailsWith<YappyUpstreamException> {
                client { ok("""{"body":{"status":"OPEN"}}""") }.openSession(credentials, device)
            }
            val unauthorized =
                assertFailsWith<YappyUpstreamException> {
                    client { respond("""{"message":"bad keys"}""", HttpStatusCode.Unauthorized, jsonHeaders) }
                        .openSession(credentials, device)
                }
            // Credenciales inválidas al abrir sesión no son "sesión expirada": no se reintenta.
            assertFalse(unauthorized is YappySessionExpiredException)
        }

    // ─── QR ────────────────────────────────────────────────────────────────

    @Test
    fun `generateQr sends headers, type in path and amounts as 2-decimal JSON numbers`() =
        runBlocking<Unit> {
            var captured: HttpRequestData? = null
            val yappy =
                client { req ->
                    captured = req
                    ok("""{"status":{"code":"YP-0000"},"body":{"transactionId":"TX-998877","hash":"QR-HASH-PAYLOAD"}}""")
                }

            val qr =
                yappy.generateQr(
                    credentials = credentials,
                    sessionToken = "session-abc",
                    qrType = YappyQrType.DYN,
                    charge =
                        YappyCharge(
                            subTotal = BigDecimal("11.68"),
                            tax = BigDecimal("0.82"),
                            total = BigDecimal("12.5"),
                            orderId = "K1-007",
                            description = "Kiosco Entrada",
                        ),
                )

            assertEquals("TX-998877", qr.transactionId)
            assertEquals("QR-HASH-PAYLOAD", qr.hash)
            val req = captured!!
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("/v1/qr/generate/DYN", req.url.encodedPath)
            assertEquals("api-key-123", req.headers["api-key"])
            assertEquals("secret-456", req.headers["secret-key"])
            assertEquals("session-abc", req.headers["authorization"])
            assertEquals(ContentType.Application.Json, (req.body as TextContent).contentType)
            assertEquals(
                """{"body":{"charge_amount":{"sub_total":11.68,"tax":0.82,"tip":0.00,"discount":0.00,"total":12.50},""" +
                    """"order_id":"K1-007","description":"Kiosco Entrada"}}""",
                req.bodyText(),
            )
        }

    @Test
    fun `generateQr accepts id and qr fallbacks and fails without transaction id`() =
        runBlocking<Unit> {
            val charge = YappyCharge(BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal("1.00"), "K1-001", "Kiosco")
            val qr =
                client { ok("""{"body":{"id":"TX-ALT","qr":"QR-ALT"}}""") }
                    .generateQr(credentials, "s", YappyQrType.HYB, charge)
            assertEquals("TX-ALT", qr.transactionId)
            assertEquals("QR-ALT", qr.hash)

            assertFailsWith<YappyUpstreamException> {
                client { ok("""{"body":{"hash":"QR-ONLY"}}""") }.generateQr(credentials, "s", YappyQrType.DYN, charge)
            }
        }

    // ─── Estado y anulación ────────────────────────────────────────────────

    @Test
    fun `getTransactionStatus normalizes Yappy status values`() =
        runBlocking<Unit> {
            val cases =
                mapOf(
                    """{"body":{"status":"COMPLETED"}}""" to YappyTransactionStatus.COMPLETED,
                    """{"body":{"status":"pending"}}""" to YappyTransactionStatus.PENDING,
                    """{"body":{"status":"DECLINED"}}""" to YappyTransactionStatus.DECLINED,
                    """{"body":{"status":"EXPIRED"}}""" to YappyTransactionStatus.EXPIRED,
                    """{"body":{"status":"FAILED"}}""" to YappyTransactionStatus.FAILED,
                    """{"body":{"status":"VOIDED"}}""" to YappyTransactionStatus.CANCELLED,
                    """{"body":{"status":"CANCELLED"}}""" to YappyTransactionStatus.CANCELLED,
                    """{"body":{"status":"IN_REVIEW"}}""" to YappyTransactionStatus.PENDING,
                    """{"data":{"status":"COMPLETED"}}""" to YappyTransactionStatus.COMPLETED,
                    """{"status":{"code":"YP-0000"},"body":{}}""" to YappyTransactionStatus.PENDING,
                )
            cases.forEach { (body, expected) ->
                var path = ""
                val status =
                    client { req ->
                        path = req.url.encodedPath
                        assertEquals(HttpMethod.Get, req.method)
                        assertEquals("tok", req.headers["authorization"])
                        ok(body)
                    }.getTransactionStatus(credentials, "tok", "TX-1")
                assertEquals(expected, status, body)
                assertEquals("/v1/transaction/TX-1", path)
            }
        }

    @Test
    fun `cancelTransaction uses PUT on the transaction`() =
        runBlocking<Unit> {
            var captured: HttpRequestData? = null
            client { req ->
                captured = req
                ok("""{"status":{"code":"YP-0000"}}""")
            }.cancelTransaction(credentials, "tok", "TX-9")
            assertEquals(HttpMethod.Put, captured!!.method)
            assertEquals("/v1/transaction/TX-9", captured!!.url.encodedPath)
            assertEquals("tok", captured!!.headers["authorization"])
        }

    @Test
    fun `401 and 403 on session-scoped calls signal an expired session`() =
        runBlocking<Unit> {
            listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
                assertFailsWith<YappySessionExpiredException> {
                    client { respond("{}", status, jsonHeaders) }.getTransactionStatus(credentials, "tok", "TX-1")
                }
            }
        }

    @Test
    fun `server errors, invalid JSON and transport failures are upstream errors`() =
        runBlocking<Unit> {
            val server =
                assertFailsWith<YappyUpstreamException> {
                    client { respond("boom", HttpStatusCode.InternalServerError) }.getTransactionStatus(credentials, "tok", "TX-1")
                }
            assertTrue(server.message!!.contains("500"))
            assertFailsWith<YappyUpstreamException> {
                client { ok("<html>") }.getTransactionStatus(credentials, "tok", "TX-1")
            }
            assertFailsWith<YappyUpstreamException> {
                client { throw IOException("timeout") }.getTransactionStatus(credentials, "tok", "TX-1")
            }
        }

    @Test
    fun `QR type is read from config defaulting to DYN`() {
        assertEquals(YappyQrType.DYN, YappyQrType.fromConfig(null))
        assertEquals(YappyQrType.DYN, YappyQrType.fromConfig(""))
        assertEquals(YappyQrType.HYB, YappyQrType.fromConfig(" hyb "))
        assertEquals(YappyQrType.DYN, YappyQrType.fromConfig("STATIC"))
    }

    // ─── Gestión de sesión ─────────────────────────────────────────────────

    private val config = YappyKioskConfig(credentials, device)
    private val key = YappySessionKey(companyDb = "momi_pa", idCaja = "CAJA-01")

    @Test
    fun `session manager opens lazily and reuses the cached token`() =
        runBlocking<Unit> {
            var opens = 0
            val yappy =
                client { req ->
                    if (req.url.encodedPath.endsWith("/session/device")) {
                        opens++
                        ok("""{"body":{"token":"tok-$opens"}}""")
                    } else {
                        assertEquals("tok-1", req.headers["authorization"])
                        ok("""{"body":{"status":"PENDING"}}""")
                    }
                }
            val manager = YappySessionManager(yappy)

            repeat(2) {
                manager.withSession(key, config) { token -> yappy.getTransactionStatus(credentials, token, "TX-1") }
            }

            assertEquals(1, opens)
        }

    @Test
    fun `session manager reopens the session once on 401 and retries`() =
        runBlocking<Unit> {
            var opens = 0
            val statusTokens = mutableListOf<String?>()
            val yappy =
                client { req ->
                    if (req.url.encodedPath.endsWith("/session/device")) {
                        opens++
                        ok("""{"body":{"token":"tok-$opens"}}""")
                    } else {
                        statusTokens += req.headers["authorization"]
                        if (req.headers["authorization"] == "tok-1") {
                            respond("""{"message":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                        } else {
                            ok("""{"body":{"status":"COMPLETED"}}""")
                        }
                    }
                }
            val manager = YappySessionManager(yappy)

            val status = manager.withSession(key, config) { token -> yappy.getTransactionStatus(credentials, token, "TX-1") }

            assertEquals(YappyTransactionStatus.COMPLETED, status)
            assertEquals(2, opens)
            assertEquals(listOf<String?>("tok-1", "tok-2"), statusTokens)
        }

    @Test
    fun `session manager retries only once when the fresh session is also rejected`() =
        runBlocking<Unit> {
            var opens = 0
            var statusCalls = 0
            val yappy =
                client { req ->
                    if (req.url.encodedPath.endsWith("/session/device")) {
                        opens++
                        ok("""{"body":{"token":"tok-$opens"}}""")
                    } else {
                        statusCalls++
                        respond("{}", HttpStatusCode.Forbidden, jsonHeaders)
                    }
                }
            val manager = YappySessionManager(yappy)

            assertFailsWith<YappySessionExpiredException> {
                manager.withSession(key, config) { token -> yappy.getTransactionStatus(credentials, token, "TX-1") }
            }
            assertEquals(2, opens)
            assertEquals(2, statusCalls)
        }
}
