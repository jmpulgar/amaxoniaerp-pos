package com.amaxonia.kiosk.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `getConfig with new data returns Success and stores etag`() =
        runTest {
            tokenStorage.serverUrl = "http://localhost:8080"
            tokenStorage.authToken = "token-abc"

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
            tokenStorage.authToken = "token-abc"
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
            tokenStorage.authToken = "token-abc"

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
            tokenStorage.authToken = "token-abc"

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
            tokenStorage.authToken = "token-abc"

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
    fun `401 Unauthorized clears the session, keeps the server URL and emits LoggedOut`() =
        runTest {
            tokenStorage.saveSession(session(token = "revoked-token"))
            tokenStorage.saveCaja("CAJA-1", "Caja 1", "K1")

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

            val events = mutableListOf<KioskSessionEvent>()
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { apiClient.sessionEvents.toList(events) }

            val result = apiClient.getConfig()

            assertTrue((result as NetworkResult.Failure).error is KioskAuthenticationException)
            assertFalse(tokenStorage.isLoggedIn())
            assertNull(tokenStorage.authToken)
            assertNull(tokenStorage.cajaId)
            assertEquals("http://localhost:8080/", tokenStorage.serverUrl)
            assertEquals("PA", tokenStorage.countryCode)
            assertEquals(listOf<KioskSessionEvent>(KioskSessionEvent.LoggedOut), events)
            collector.cancel()
        }

    private fun yappyClient(
        handler:
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData,
    ): Pair<KioskApiClient, MockEngine> {
        tokenStorage.serverUrl = "http://localhost:8080"
        tokenStorage.authToken = "token-abc"
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

    // --- System login, cajas and kiosk session headers ---

    private fun session(token: String = "company-token") =
        KioskSession(
            token = token,
            userId = 7,
            username = "cajero1",
            companyId = 2,
            companyName = "Compañía Prueba",
            companyDb = "t_prueba",
            countryCode = "PA",
            serverUrl = "http://localhost:8080/",
        )

    private fun client(
        handler:
            suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData,
            ) -> io.ktor.client.request.HttpResponseData,
    ): Pair<KioskApiClient, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return KioskApiClient(KioskHttpClientFactory.create(tokenStorage, engine), tokenStorage) to engine
    }

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `login posts the credentials with X-Country-Code and parses the contract`() =
        runTest {
            val (client, engine) =
                client {
                    respond(
                        """
                        {"token":"jwt-identity-token","user":{"id":7,"username":"cajero1","role":"CAJERO"},
                         "companies":[{"id":2,"name":"Compañía Prueba","rif":"TEST-ID"}],"countryCode":"PA","schemaType":"TYPE_A"}
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        json,
                    )
                }

            val login = client.login("http://localhost:8080", "pa", " cajero1 ", "secreta").getOrThrow()

            val request = engine.requestHistory.single()
            assertEquals("/auth/login", request.url.encodedPath)
            assertEquals("PA", request.headers[KioskHeaders.COUNTRY_CODE])
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[KioskHeaders.CAJA])
            val body = (request.body as io.ktor.http.content.TextContent).text
            assertTrue(body.contains("\"username\":\"cajero1\""))
            assertTrue(body.contains("\"password\":\"secreta\""))
            assertEquals("jwt-identity-token", login.token)
            assertEquals(7, login.user.id)
            assertEquals("TEST-ID", login.companies.single().rif)
        }

    @Test
    fun `login 401 is a wrong-password error and never touches the stored session`() =
        runTest {
            tokenStorage.saveSession(session())
            val (client, _) = client { respond("""{"error":"Credenciales inválidas"}""", HttpStatusCode.Unauthorized, json) }

            val error = client.login("http://localhost:8080/", "PA", "cajero1", "mala").exceptionOrNull()

            assertTrue(error is KioskInvalidCredentialsException)
            assertEquals("Usuario o contraseña incorrectos", error?.message)
            assertTrue(tokenStorage.isLoggedIn())
        }

    @Test
    fun `login maps transport failures to KioskConnectivityException and server errors to their message`() =
        runTest {
            val (offline, _) = client { throw java.net.UnknownHostException("api.listoerp.app") }
            assertTrue(offline.login("https://api.listoerp.app/", "PA", "u", "p").exceptionOrNull() is KioskConnectivityException)

            val (failing, _) = client { respond("""{"error":"Usuario inactivo"}""", HttpStatusCode.Forbidden, json) }
            val error = failing.login("https://api.listoerp.app/", "PA", "u", "p").exceptionOrNull() as KioskApiException
            assertEquals("Usuario inactivo", error.message)
            assertEquals(403, error.statusCode)
        }

    @Test
    fun `selectCompany sends the identity token and parses the company token`() =
        runTest {
            val (client, engine) =
                client {
                    respond(
                        """
                        {"success":true,"token":"jwt-company-token","currentCompany":{"id":2,"name":"Compañía Prueba",
                         "adminDb":"t_prueba","accountingDb":"cont_prueba","payrollDb":"nom_prueba","rif":"TEST-ID"},
                         "countryCode":"PA","schemaType":"TYPE_A"}
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        json,
                    )
                }

            val selected = client.selectCompany("localhost:8080", "jwt-identity-token", 2).getOrThrow()

            val request = engine.requestHistory.single()
            assertEquals("https://localhost:8080/auth/company", request.url.toString())
            assertEquals("Bearer jwt-identity-token", request.headers[HttpHeaders.Authorization])
            assertEquals("{\"companyId\":2}", (request.body as io.ktor.http.content.TextContent).text)
            assertEquals("jwt-company-token", selected.token)
            assertEquals("t_prueba", selected.currentCompany.adminDb)
        }

    @Test
    fun `getCajas sends the company token and Company-DB and maps the backend Caja JSON`() =
        runTest {
            tokenStorage.saveSession(session())
            val (client, engine) =
                client {
                    respond(
                        """
                        [
                          {"idCaja":"c-1","codCaja":"001","caja":"CAJA1","descripcion":"Caja Kiosco","estatus":1,"idSucursal":1,
                           "codAlmacen":null,"default_warehouse_id":3,"default_vendedor_id":null,"available_sellers":[],
                           "serie_sucursal":"A","default_tax_rate":7.0,"serieCaja":"K","sucursalNombre":"Centro","sucursalCodigo":"01"},
                          {"idCaja":"c-2","codCaja":"002","caja":null,"descripcion":null,"estatus":0,"idSucursal":null,"serieCaja":""}
                        ]
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        json,
                    )
                }

            val cajas = client.getCajas().getOrThrow()

            val request = engine.requestHistory.single()
            assertEquals("http://localhost:8080/api/cajas", request.url.toString())
            assertEquals("Bearer company-token", request.headers[HttpHeaders.Authorization])
            assertEquals("t_prueba", request.headers[KioskHeaders.COMPANY_DB])
            assertEquals(listOf("c-1", "c-2"), cajas.map { it.idCaja })
            assertTrue(cajas[0].isActive)
            assertEquals("Caja Kiosco", cajas[0].displayName)
            assertEquals("Centro", cajas[0].sucursalNombre)
            assertFalse(cajas[1].isActive)
            assertEquals("002", cajas[1].displayName)
        }

    @Test
    fun `kiosk calls carry the company token, caja and prefix headers`() =
        runTest {
            tokenStorage.saveSession(session())
            tokenStorage.saveCaja(cajaId = "c-1", cajaName = "Caja Kiosco", prefix = "K2")
            val (client, engine) = client { respond("""{"version":1}""", HttpStatusCode.OK, json) }

            client.getConfig()

            val headers = engine.requestHistory.single().headers
            assertEquals("Bearer company-token", headers[HttpHeaders.Authorization])
            assertEquals("c-1", headers[KioskHeaders.CAJA])
            assertEquals("K2", headers[KioskHeaders.PREFIX])
        }

    @Test
    fun `400 Caja del kiosco no valida clears only the caja and emits InvalidCaja`() =
        runTest {
            tokenStorage.saveSession(session())
            tokenStorage.saveCaja(cajaId = "c-9", cajaName = "Vieja", prefix = "K3")
            val (client, _) = client { respond("""{"error":"Caja del kiosco no válida"}""", HttpStatusCode.BadRequest, json) }
            val events = mutableListOf<KioskSessionEvent>()
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { client.sessionEvents.toList(events) }

            val error = (client.getCatalog() as NetworkResult.Failure).error

            assertTrue(error is KioskInvalidCajaException)
            assertTrue(tokenStorage.isLoggedIn())
            assertFalse(tokenStorage.hasCaja())
            assertEquals("K3", tokenStorage.prefix)
            assertEquals(listOf<KioskSessionEvent>(KioskSessionEvent.InvalidCaja), events)
            collector.cancel()
        }

    @Test
    fun `503 keeps the server message for the out-of-service banner`() =
        runTest {
            tokenStorage.saveSession(session())
            val message = "El kiosco no está habilitado en esta empresa (falta migración)"
            val (client, _) = client { respond("""{"error":"$message"}""", HttpStatusCode.ServiceUnavailable, json) }

            val error = (client.getConfig() as NetworkResult.Failure).error as KioskApiException

            assertEquals(503, error.statusCode)
            assertEquals(message, error.serverMessage)
            assertTrue(tokenStorage.isLoggedIn())
        }

    @Test
    fun `unlock with a wrong password returns false and keeps the session`() =
        runTest {
            tokenStorage.saveSession(session())
            val (client, _) = client { respond("""{"error":"Contraseña de desbloqueo incorrecta"}""", HttpStatusCode.Unauthorized, json) }

            assertEquals(false, client.unlock("wrong").getOrThrow())
            assertTrue(tokenStorage.isLoggedIn())
        }

    @Test
    fun `catalog modifier options parse isDefault and default it to false`() =
        runTest {
            val (client, _) =
                client {
                    respond(
                        """{"categories":[],"items":[{"id":1,"categoryId":1,"name":"Combo","description":null,"price":"5.00",""" +
                            """"taxRate":"7.00","imageUrl":null,"soldOut":false,"modifierGroups":[{"id":1,"name":"Bebida","min":1,""" +
                            """"max":1,"isMandatory":true,"isCombo":true,"options":[{"id":1,"name":"Agua","extraPrice":"0.00"},""" +
                            """{"id":2,"name":"Soda","extraPrice":"0.00","isDefault":true}]}]}]}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }

            val options = (client.getCatalog() as NetworkResult.Success).data.items.single().modifierGroups.single().options

            assertEquals(listOf(false, true), options.map { it.isDefault })
        }

    @Test
    fun `server URLs are normalized`() {
        assertEquals("https://api.listoerp.app/", KioskApiClient.normalizeServerUrl(" api.listoerp.app "))
        assertEquals("http://10.0.2.2:8080/", KioskApiClient.normalizeServerUrl("http://10.0.2.2:8080"))
        assertTrue(KioskApiClient.isInvalidCajaError("Caja del kiosco no válida"))
        assertFalse(KioskApiClient.isInvalidCajaError("Petición inválida"))
        assertTrue(KioskApiClient.isInvalidCajaError("Falta el header X-Kiosk-Caja con la caja del kiosco"))
        assertTrue(KioskApiClient.isInvalidCajaError("X-Kiosk-Prefix inválido: debe tener de 1 a 5 letras (A-Z) o números"))
    }

    @Test
    fun `403 Se requiere token de empresa logs the kiosk out like a 401`() =
        runTest {
            tokenStorage.saveSession(session())
            tokenStorage.saveCaja("c-1", "Caja 1", "K1")
            val (client, _) = client { respond("""{"error":"Se requiere token de empresa"}""", HttpStatusCode.Forbidden, json) }
            val events = mutableListOf<KioskSessionEvent>()
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { client.sessionEvents.toList(events) }

            val error = (client.getCatalog() as NetworkResult.Failure).error

            assertTrue(error is KioskAuthenticationException)
            assertFalse(tokenStorage.isLoggedIn())
            assertEquals(listOf<KioskSessionEvent>(KioskSessionEvent.LoggedOut), events)
            collector.cancel()
        }

    @Test
    fun `relative already-encoded media paths are resolved without double encoding`() =
        runTest {
            tokenStorage.saveSession(session())
            val (client, _) =
                client {
                    respond(
                        """{"media":[{"type":"VIDEO","url":"/api/data/PA/momi_pa/banners/video%20promo.MP4","durationSec":0}]}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }

            val media = (client.getConfig() as NetworkResult.Success).data.media.single()

            assertEquals("http://localhost:8080/api/data/PA/momi_pa/banners/video%20promo.MP4", media.url)
            assertEquals(0, media.durationSec)
        }
}
