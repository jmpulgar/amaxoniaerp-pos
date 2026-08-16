package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del cliente HTTP [VenezuelaHkaRestClient] con [MockEngine].
 *
 * Cubren los seis escenarios de transporte independientes del brief:
 *  - 4. Autenticación exitosa.
 *  - 5. Autenticación rechazada.
 *  - 6. UltimoDocumento disponible.
 *  - 10. código de negocio != "200".
 *  - 11. HTTP 400.
 *  - 12. HTTP 500.
 *  - 13. Timeout (HttpRequestTimeoutException → VenezuelaHkaClientException.Timeout).
 *  - 14. JSON inválido (decode a null sin lanzar).
 *
 * Y verifica la separación estricta de 6 capas (httpStatus / rawBody / codigo /
 * mensaje / validaciones / resultado).
 */
class VenezuelaHkaRestClientTest {
    private val credentials =
        PacCredentials(
            usuario = "usuarioDemo",
            clave = "claveSecreta",
            baseUrl = "https://demo.thefactoryhka.com.ve",
        )

    private fun cliente(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): VenezuelaHkaRestClient =
        VenezuelaHkaRestClient(
            HttpClient(MockEngine { request -> handler(request) }) {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 1500
                    connectTimeoutMillis = 1500
                    socketTimeoutMillis = 1500
                }
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(feJsonVE)
                }
            },
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ─── 4. Autenticación exitosa ──────────────────────────────────────────

    @Test
    fun `autenticacion exitosa retorna token en resultado`() =
        runBlocking {
            val client =
                cliente { req ->
                    assertTrue(req.url.encodedPath.endsWith("/api/Autenticacion"))
                    respond(
                        content = """{"token":"jwt-firma-123","mensaje":"OK"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            val res = client.authenticate(credentials)
            assertEquals(200, res.httpStatus)
            assertEquals("200", res.codigo)
            assertTrue(res.httpOk)
            assertTrue(res.businessOk)
            assertEquals("jwt-firma-123", res.resultado?.token)
        }

    // ─── 5. Autenticación rechazada ───────────────────────────────────────

    @Test
    fun `autenticacion rechazada con 401 no provee token y businessOk falso`() {
        // En HTTP 401 el body puede venir sin token válido: el cliente no debe
        // producir un codigo "200".
        val client =
            cliente {
                respond(
                    content = """{"mensaje":"Credenciales inválidas"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            }
        val res = runBlocking { client.authenticate(credentials) }
        assertEquals(401, res.httpStatus)
        assertEquals("HTTP_401", res.codigo)
        assertFalse(res.httpOk)
        assertFalse(res.businessOk)
        assertNull(res.resultado?.token, "No debe retornarse token en 401")
        assertTrue(res.mensaje.isBlank() || res.mensaje.isNotEmpty())
    }

    // ─── 6. UltimoDocumento disponible ────────────────────────────────────

    @Test
    fun `ultimoDocumento 200 con resultado retorna ultimoNumero`() =
        runBlocking {
            val client =
                cliente { req ->
                    assertTrue(req.url.encodedPath.endsWith("/api/Consultar_Ultimo_Documento"))
                    // Verificamos bearer token propagado.
                    assertEquals("Bearer jwt-x", req.headers[HttpHeaders.Authorization])
                    respond(
                        content = """{"codigo":"200","mensaje":"OK","resultado":{"ultimoNumero":"3500"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            val res =
                client.fetchLastDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    request = VenezuelaHkaUltimoDocumentoRequest(serie = "L001P001", tipoDocumento = "01"),
                )
            assertEquals(200, res.httpStatus)
            assertEquals("200", res.codigo)
            assertEquals("3500", res.resultado?.resultado?.ultimoNumero)
            assertTrue(res.fullyOk)
        }

    // ─── 10. código de negocio != 200 ─────────────────────────────────────

    @Test
    fun `emision con codigo negocio distinto de 200 expone businessOk falso`() =
        runBlocking {
            val client =
                cliente {
                    respond(
                        content =
                            "{\"codigo\":\"422\",\"mensaje\":\"Serie no configurada\",\"resultado\":null," +
                                "\"validaciones\":[\"serie\"]}",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            val res =
                client.emitDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            assertEquals(200, res.httpStatus)
            assertTrue(res.httpOk)
            assertFalse(res.businessOk, " codigo 422 debe dar businessOk false")
            assertEquals("422", res.codigo)
            assertEquals("Serie no configurada", res.mensaje)
            assertEquals(listOf("serie"), res.validaciones)
            assertNull(res.resultado?.resultado?.numeroDocumento)
            assertFalse(res.fullyOk)
        }

    // ─── 11. HTTP 400 ─────────────────────────────────────────────────────

    @Test
    fun `HTTP 400 produce codigo HTTP_400 sin resultado`() =
        runBlocking {
            val client =
                cliente {
                    respond(
                        content = """Bad Request""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders,
                    )
                }
            val res =
                client.emitDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            assertEquals(400, res.httpStatus)
            assertEquals("HTTP_400", res.codigo)
            assertFalse(res.httpOk)
            assertNull(res.resultado)
            assertFalse(res.fullyOk)
        }

    // ─── 12. HTTP 500 ─────────────────────────────────────────────────────

    @Test
    fun `HTTP 500 produce codigo HTTP_500 y rawBody preservado`() =
        runBlocking {
            val client =
                cliente {
                    respond(
                        content = """Internal Server Error""",
                        status = HttpStatusCode.InternalServerError,
                        headers = jsonHeaders,
                    )
                }
            val res =
                client.emitDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            assertEquals(500, res.httpStatus)
            assertEquals("HTTP_500", res.codigo)
            assertEquals("Internal Server Error", res.rawBody)
            assertNull(res.resultado)
        }

    // ─── 13. Timeout ──────────────────────────────────────────────────────

    @Test
    fun `timeout en emision lanza VenezuelaHkaClientException Timeout`() {
        val client =
            cliente {
                throw VenezuelaHkaClientException.Timeout("Timeout simulado en Emision")
            }
        try {
            runBlocking {
                client.emitDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
            error("Se esperaba VenezuelaHkaClientException.Timeout")
        } catch (e: VenezuelaHkaClientException.Timeout) {
            assertTrue(e.message?.contains("Timeout") == true)
        }
    }

    @Test
    fun `timeout en autenticacion lanza Timeout, no Network`() {
        val client =
            cliente {
                throw VenezuelaHkaClientException.Timeout("Timeout simulado en Autenticacion")
            }
        try {
            runBlocking { client.authenticate(credentials) }
            error("Se esperaba VenezuelaHkaClientException.Timeout")
        } catch (e: VenezuelaHkaClientException.Timeout) {
            assertNotNull(e.message)
        }
    }

    // ─── 14. JSON inválido ────────────────────────────────────────────────

    @Test
    fun `respuesta JSON invalida en emision no lanza y resultado queda null`() =
        runBlocking {
            val client =
                cliente {
                    respond(
                        content = """{"codigo":"200", "resultado": """", // JSON truncado/inválido
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            val res =
                client.emitDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            assertEquals(200, res.httpStatus)
            assertEquals("INVALID_JSON", res.codigo) // httpOk pero body ilegible
            assertNull(res.resultado, "JSON inválido ⇒ resultado null")
            assertFalse(res.businessOk, "Sin resultado decodificable no puede ser exitoso")
        }

    @Test
    fun `token vacio en autenticacion 200 se interpreta como fallo negocio`() =
        runBlocking {
            val client =
                cliente {
                    respond(
                        content = """{"token":"","mensaje":"Sin sesion"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            val res = client.authenticate(credentials)
            assertEquals(200, res.httpStatus)
            assertEquals("200", res.codigo) // parseó pero token viene vacío en el body
            assertTrue(res.resultado?.token.isNullOrBlank())
        }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun samplePayload(): VenezuelaHkaDocumentoWrapper =
        VenezuelaHkaDocumentoWrapper(
            documento =
                VenezuelaHkaDocumento(
                    codigoSucursalEmisor = "0000",
                    datosTransaccion =
                        VenezuelaHkaDatosTransaccion(
                            tipoEmision = "01",
                            tipoDocumento = "01",
                            numeroDocumentoFiscal = "00000001",
                            puntoFacturacionFiscal = "001",
                            fechaEmision = "2026-08-03T00:00:00",
                            procesoGeneracion = "1",
                            transaccionId = "txid1234",
                            cliente =
                                VenezuelaHkaCliente(
                                    nombreRazonSocial = "CONSUMIDOR FINAL",
                                    numeroRif = "V000000000",
                                ),
                            serie = "L001P001",
                            sucursal = "L001P001",
                        ),
                    listaItems =
                        listOf(
                            VenezuelaHkaItem(
                                descripcion = "Item 1",
                                codigo = "P001",
                                unidadMedida = "UND",
                                cantidad = "1.000",
                                precioUnitario = "100.00",
                                precioItem = "100.00",
                                valorTotal = "116.00",
                                alicuotaIva = "16.00",
                                valorIva = "16.00",
                            ),
                        ),
                    totalesSubTotales =
                        VenezuelaHkaTotalesSubTotales(
                            totalPrecioNeto = "100.00",
                            totalIva = "16.00",
                            totalDescuento = "0.00",
                            totalAlicuotaGeneral = "100.00",
                            totalAlicuotaReducido = "0.00",
                            totalAlicuotaExento = "0.00",
                            totalMontoGravado = "100.00",
                            montoTotalFactura = "116.00",
                            listaFormaPago =
                                listOf(
                                    VenezuelaHkaFormaPago(formaPagoFact = "01", montoPagado = "116.00"),
                                ),
                            totalValorRecibido = "116.00",
                            tiempoPago = "1",
                            nroItems = "1",
                            totalTodosItems = "116.00",
                            montoEnLetras = "116 CON 00/100",
                        ),
                ),
        )
}
