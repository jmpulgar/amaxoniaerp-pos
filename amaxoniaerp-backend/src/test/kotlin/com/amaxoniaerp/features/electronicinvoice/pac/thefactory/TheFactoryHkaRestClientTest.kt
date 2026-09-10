package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCommunicationException
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacEstadoDocumentoSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private typealias MockRequestHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/**
 * Tests del cliente HTTP [TheFactoryHkaRestClient] (PAC Panamá) con [MockEngine].
 *
 * Cubren los escenarios de transporte del brief sobre el port
 * [com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient]:
 *   - Autenticación exitosa / rechazada / sin token en el body.
 *   - Emisión aceptada (codigo 200) y rechazada (codigo de negocio != 200).
 *   - Normalización al [PacResponse] estandarizado (incluye el caso
 *     `resultado` contiene "exitoso" aunque el codigo difiera).
 *   - HTTP 5xx con el cuerpo preservado en el mensaje.
 *   - Cuerpo ilegible y timeout: resultado INCIERTO (Result.failure sin
 *     respuesta fabricada — el número fiscal pudo haberse creado en el PAC).
 *   - Descarga de PDF y envío de correo.
 *
 * El cliente NO se modifica: estos tests caracterizan su comportamiento actual.
 */
class TheFactoryHkaRestClientTest {
    private val credentials =
        PacCredentials(
            usuario = "usuarioDemo",
            clave = "claveSecreta",
            // Barra final deliberada: trimEnd('/') debe tolerarla.
            baseUrl = "https://pac.demohka.com/",
        )

    private fun cliente(handler: MockRequestHandler): TheFactoryHkaRestClient =
        TheFactoryHkaRestClient(
            HttpClient(MockEngine { request -> handler(request) }) {
                install(ContentNegotiation) {
                    json(feJson)
                }
            },
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ─── Autenticación ─────────────────────────────────────────────────────

    @Test
    fun `autenticacion exitosa retorna token y tolera barra final en baseUrl`() {
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Autenticacion"))
                assertEquals(HttpMethod.Post, req.method)
                respond(
                    content = """{"token":"jwt-pa-123","mensaje":"OK"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res = runBlocking { client.authenticate(credentials) }
        assertTrue(res.isSuccess)
        val token = res.getOrThrow()
        assertEquals("jwt-pa-123", token.token)
        assertTrue(token.expiresAt > 0L)
    }

    @Test
    fun `autenticacion con credenciales rechazadas falla con PacCommunicationException`() {
        val client =
            cliente {
                respond(
                    content = """{"mensaje":"Credenciales invalidas"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            }
        val res = runBlocking { client.authenticate(credentials) }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertIs<PacCommunicationException>(ex)
        assertTrue(ex.message?.contains("HTTP 401") == true)
    }

    @Test
    fun `autenticacion 200 sin token en el body falla por token vacio`() {
        val client =
            cliente {
                respond(
                    content = """{"mensaje":"Sin sesion"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res = runBlocking { client.authenticate(credentials) }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertIs<PacCommunicationException>(ex)
        assertTrue(ex.message?.contains("Token") == true)
    }

    // ─── Emisión de documentos ─────────────────────────────────────────────

    @Test
    fun `envio aceptado normaliza a PacResponse exitoso con cufe y protocolo`() {
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Enviar"))
                assertEquals("Bearer jwt-x", req.headers[HttpHeaders.Authorization])
                respond(
                    content =
                        """{"codigo":"200","resultado":"Factura recibida exitosamente","mensaje":"OK",""" +
                            """"cufe":"CUFE-123","qr":"QR-DATA","fechaRecepcionDGI":"2026-08-21T10:00:00",""" +
                            """"nroProtocoloAutorizacion":"NPA-9","fechaLimite":"2026-09-21"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        assertTrue(res.isSuccess)
        val pac = res.getOrThrow()
        assertTrue(pac.exitoso)
        assertEquals("200", pac.codigo)
        assertEquals("CUFE-123", pac.cufe)
        assertEquals("QR-DATA", pac.qr)
        assertEquals("NPA-9", pac.nroProtocoloAutorizacion)
        assertEquals("2026-09-21", pac.fechaLimite)
    }

    @Test
    fun `envio con resultado exitoso y codigo distinto de 200 tambien es exitoso`() {
        val client =
            cliente {
                respond(
                    content = """{"codigo":"0","resultado":"Proceso Exitoso"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        val pac = res.getOrThrow()
        assertTrue(pac.exitoso)
        assertEquals("0", pac.codigo)
        // mensaje cae al campo resultado cuando mensaje viene ausente
        assertEquals("Proceso Exitoso", pac.mensaje)
        assertNull(pac.cufe)
    }

    @Test
    fun `envio rechazado produce PacResponse no exitoso sin cufe`() {
        val client =
            cliente {
                respond(
                    content = """{"codigo":"422","mensaje":"RUC invalido"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        assertTrue(res.isSuccess)
        val pac = res.getOrThrow()
        assertFalse(pac.exitoso)
        assertEquals("422", pac.codigo)
        assertEquals("RUC invalido", pac.mensaje)
        assertNull(pac.cufe)
    }

    @Test
    fun `HTTP 500 en envio falla preservando el cuerpo en el mensaje`() {
        val client =
            cliente {
                respond(
                    content = """Internal Server Error""",
                    status = HttpStatusCode.InternalServerError,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertIs<PacCommunicationException>(ex)
        assertTrue(ex.message?.contains("HTTP 500") == true)
        assertTrue(ex.message?.contains("Internal Server Error") == true)
    }

    // ─── Resultados inciertos (sin respuesta fabricada) ────────────────────

    @Test
    fun `respuesta ilegible en envio 200 falla sin inventar confirmacion`() {
        val client =
            cliente {
                respond(
                    content = """{"codigo":"200", "resu""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertFalse(
            ex is PacCommunicationException,
            "El body ilegible es un fallo de decode, no una clasificación de transporte",
        )
        assertNull(res.getOrNull(), "No debe fabricarse un PacResponse sin cuerpo legible")
    }

    @Test
    fun `timeout en envio falla como comunicacion incierta sin PacResponse`() {
        val client =
            cliente {
                // Un timeout real emerge del engine como IOException; se simula
                // aquí porque HttpRequestTimeoutException tiene constructor
                // interno ligado al request.
                throw java.io.IOException("Timeout simulado en Enviar")
            }
        val res =
            runBlocking {
                client.sendDocument(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    payload = samplePayload(),
                )
            }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertFalse(
            ex is PacCommunicationException,
            "El fallo de E/S no debe clasificarse como error de comunicación PAC",
        )
        assertTrue(ex.message?.contains("Timeout") == true)
        assertNull(res.getOrNull())
    }

    // ─── Descarga de PDF y XML ─────────────────────────────────────────────

    @Test
    fun `serializacion de TheFactoryDescargaArchivoRequest no omite tipoArchivo con encodeDefaults false`() {
        val reqDefault = TheFactoryDescargaArchivoRequest(cufe = "CUFE-123")
        val jsonDefault = feJson.encodeToString(TheFactoryDescargaArchivoRequest.serializer(), reqDefault)
        assertTrue(jsonDefault.contains(""""tipoArchivo":"pdf""""), "Debe incluir tipoArchivo=pdf por defecto")
        assertTrue(jsonDefault.contains(""""cufe":"CUFE-123""""))

        val reqExplicit = TheFactoryDescargaArchivoRequest(cufe = "CUFE-456", tipoArchivo = "pdf")
        val jsonExplicit = feJson.encodeToString(TheFactoryDescargaArchivoRequest.serializer(), reqExplicit)
        assertTrue(jsonExplicit.contains(""""tipoArchivo":"pdf""""), "Debe incluir tipoArchivo=pdf explicito")

        val reqXml = TheFactoryDescargaArchivoRequest(cufe = "CUFE-789", tipoArchivo = "xml")
        val jsonXml = feJson.encodeToString(TheFactoryDescargaArchivoRequest.serializer(), reqXml)
        assertTrue(jsonXml.contains(""""tipoArchivo":"xml""""), "Debe incluir tipoArchivo=xml")
    }

    @Test
    fun `descarga de PDF exitosa retorna los bytes crudos y envia tipoArchivo pdf`() {
        val pdfBytes = "%PDF-1.7 contenido simulado".toByteArray()
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Descarga") || req.url.encodedPath.endsWith("/api/DescargaPDF"))
                val bodyText =
                    when (val b = req.body) {
                        is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                        else -> ""
                    }
                assertTrue(bodyText.contains(""""tipoArchivo":"pdf""""), "El body debe contener tipoArchivo=pdf")
                assertTrue(bodyText.contains(""""cufe":"CUFE-PDF-1""""), "El body debe contener el CUFE")
                respond(
                    content = pdfBytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
                )
            }
        val res =
            runBlocking {
                client.downloadPdf(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-PDF-1",
                )
            }
        assertTrue(res.isSuccess)
        assertEquals(pdfBytes.toList(), res.getOrThrow().toList())
    }

    @Test
    fun `descarga de PDF exitosa con JSON Base64 decodifica el archivo correctamente`() {
        val pdfContent = "%PDF-1.7 contenido decodificado".toByteArray()
        val base64String =
            java.util.Base64
                .getEncoder()
                .encodeToString(pdfContent)
        val jsonResponse = """{"codigo":200,"resultado":"Exitoso","mensaje":"Documento descargado","archivo":"$base64String"}"""
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Descarga"))
                val bodyText =
                    when (val b = req.body) {
                        is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                        else -> ""
                    }
                assertTrue(bodyText.contains(""""tipoArchivo":"pdf""""), "El body debe contener tipoArchivo=pdf")
                respond(
                    content = jsonResponse,
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.downloadPdf(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-PDF-BASE64",
                )
            }
        assertTrue(res.isSuccess)
        assertEquals(pdfContent.toList(), res.getOrThrow().toList())
    }

    @Test
    fun `descarga de PDF con error de negocio 109 tipoArchivo es requerido falla con PacCommunicationException`() {
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Descarga"))
                respond(
                    content = """{"Codigo":109,"Resultado":"Error","Mensaje":"tipoArchivo es requerido"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.downloadPdf(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-ERR-109",
                )
            }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertIs<PacCommunicationException>(ex)
        assertTrue(ex.message?.contains("tipoArchivo es requerido") == true)
    }

    @Test
    fun `descarga de XML exitosa envia tipoArchivo xml y decodifica correctamente`() {
        val xmlContent = "<rFE>xml simulado</rFE>".toByteArray()
        val base64String =
            java.util.Base64
                .getEncoder()
                .encodeToString(xmlContent)
        val jsonResponse = """{"codigo":200,"resultado":"Exitoso","mensaje":"Documento descargado","archivo":"$base64String"}"""
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/Descarga"))
                val bodyText =
                    when (val b = req.body) {
                        is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                        else -> ""
                    }
                assertTrue(bodyText.contains(""""tipoArchivo":"xml""""), "El body debe contener tipoArchivo=xml")
                assertTrue(bodyText.contains(""""cufe":"CUFE-XML-1""""), "El body debe contener el CUFE")
                respond(
                    content = jsonResponse,
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.downloadXml(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-XML-1",
                )
            }
        assertTrue(res.isSuccess)
        assertEquals(xmlContent.toList(), res.getOrThrow().toList())
    }

    @Test
    fun `descarga de PDF con HTTP 404 falla con PacCommunicationException`() {
        val client =
            cliente {
                respond(
                    content = """Not Found""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.downloadPdf(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-MISSING",
                )
            }
        assertTrue(res.isFailure)
        assertIs<PacCommunicationException>(res.exceptionOrNull())
    }

    // ─── Envío de correo ───────────────────────────────────────────────────

    @Test
    fun `envio de correo exitoso decodifica la confirmacion del PAC`() {
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/EnvioCorreo"))
                assertEquals("Bearer jwt-x", req.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"codigo":"200","resultado":"Correo enviado","cufe":"CUFE-MAIL-1"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendEmail(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-MAIL-1",
                    emails = listOf("cliente@example.com"),
                )
            }
        assertTrue(res.isSuccess)
        val correo = res.getOrThrow()
        assertEquals("200", correo.codigo)
        assertEquals("CUFE-MAIL-1", correo.cufe)
    }

    @Test
    fun `envio de correo con HTTP 503 falla con PacCommunicationException y cuerpo preservado`() {
        val client =
            cliente {
                respond(
                    content = """Service Unavailable""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.sendEmail(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    cufe = "CUFE-MAIL-2",
                    emails = listOf("cliente@example.com"),
                )
            }
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertIs<PacCommunicationException>(ex)
        assertTrue(ex.message?.contains("Service Unavailable") == true)
    }

    // ─── EstadoDocumento (conciliación anti-1513) ──────────────────────────

    @Test
    fun `estado documento autorizado normaliza cufe y fecha de recepcion`() {
        val client =
            cliente { req ->
                assertTrue(req.url.encodedPath.endsWith("/api/EstadoDocumento"))
                assertEquals(HttpMethod.Post, req.method)
                assertEquals("Bearer jwt-x", req.headers[HttpHeaders.Authorization])
                respond(
                    content =
                        """{"codigo":"200","mensaje":"OK","resultado":"Autorizado",""" +
                            """"cufe":"CUFE-REC","fechaRecepcionDocumento":"2026-09-01T10:00:00"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.consultarEstadoDocumento(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    solicitud = solicitudConciliacion(),
                )
            }
        assertTrue(res.isSuccess)
        val estado = res.getOrThrow()
        assertEquals("200", estado.codigo)
        assertTrue(estado.autorizado, "codigo 200 con CUFE debe considerarse autorizado")
        assertEquals("CUFE-REC", estado.cufe)
        assertEquals("2026-09-01T10:00:00", estado.fechaRecepcionDGI)
    }

    @Test
    fun `estado documento inexistente reporta no autorizado sin fallar`() {
        val client =
            cliente {
                respond(
                    content = """{"codigo":"102","mensaje":"El documento no existe"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.consultarEstadoDocumento(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    solicitud = solicitudConciliacion(),
                )
            }
        assertTrue(res.isSuccess)
        val estado = res.getOrThrow()
        assertFalse(estado.autorizado)
        assertEquals("102", estado.codigo)
    }

    @Test
    fun `HTTP 503 en EstadoDocumento falla con PacCommunicationException`() {
        val client =
            cliente {
                respond(
                    content = """Service Unavailable""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = jsonHeaders,
                )
            }
        val res =
            runBlocking {
                client.consultarEstadoDocumento(
                    baseUrl = credentials.baseUrl,
                    token = PacAuthToken("jwt-x"),
                    solicitud = solicitudConciliacion(),
                )
            }
        assertTrue(res.isFailure)
        assertIs<PacCommunicationException>(res.exceptionOrNull())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun solicitudConciliacion() =
        PacEstadoDocumentoSolicitud(
            numeroDocumentoFiscal = "00000000000000000042",
            codigoSucursalEmisor = "0001",
            puntoFacturacionFiscal = "001",
            tipoDocumento = "01",
        )

    private fun samplePayload(): TheFactoryHkaDocumentoWrapper =
        TheFactoryHkaDocumentoWrapper(
            documento =
                TheFactoryHkaDocumento(
                    codigoSucursalEmisor = "0001",
                    datosTransaccion =
                        TheFactoryHkaDatosTransaccion(
                            tipoEmision = "01",
                            tipoDocumento = "01",
                            numeroDocumentoFiscal = "00000000000000000001",
                            puntoFacturacionFiscal = "001",
                            fechaEmision = "2026-08-21T00:00:00-05:00",
                            naturalezaOperacion = "01",
                            tipoOperacion = "1",
                            destinoOperacion = "1",
                            formatoCAFE = "1",
                            entregaCAFE = "1",
                            envioContenedor = "1",
                            procesoGeneracion = "1",
                            tipoVenta = "01",
                            cliente =
                                TheFactoryHkaCliente(
                                    tipoClienteFE = "02",
                                    razonSocial = "CONSUMIDOR FINAL",
                                    telefono1 = "000-0000",
                                ),
                        ),
                    listaItems =
                        listOf(
                            TheFactoryHkaItem(
                                descripcion = "Item 1",
                                codigo = "P001",
                                cantidad = "1.000000",
                                precioUnitario = "100.000000",
                                valorTotal = "107.000000",
                                tasaITBMS = "07.00",
                                valorITBMS = "7.000000",
                            ),
                        ),
                    totalesSubTotales =
                        TheFactoryHkaTotalesSubTotales(
                            totalPrecioNeto = "100.000000",
                            totalITBMS = "7.000000",
                            totalFactura = "107.000000",
                            totalValorRecibido = "107.000000",
                            tiempoPago = "0",
                            nroItems = "1",
                            totalTodosItems = "100.000000",
                            listaFormaPago =
                                listOf(
                                    TheFactoryHkaFormaPago(formaPagoFact = "02", valorCuotaPagada = "107.000000"),
                                ),
                        ),
                ),
        )
}
