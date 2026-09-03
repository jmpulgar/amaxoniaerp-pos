package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.FeResponseUpdate
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCommunicationException
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacEstadoDocumento
import com.amaxoniaerp.features.electronicinvoice.domain.PacEstadoDocumentoSolicitud
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryEnviarCorreoResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.io.IOException
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests del workflow de Facturación Electrónica PA ([PanamaInvoiceProcessor])
 * con dobles de prueba: repositorio en memoria (la clase es open precisamente
 * para esto) y cliente PAC falso que registra llamadas.
 *
 * Invariantes cubiertas:
 *  - `tipo_facturacion < 3` → NUNCA contacta al PAC.
 *  - Mapeo por paso: INVOICE_NOT_FOUND / CONFIG_ERROR / AUTH_ERROR /
 *    BUILD_ERROR / SEND_ERROR se retornan como Failure tipado.
 *  - HALLAZGO (characterization, NO corregir aquí): los `throw` dentro de
 *    lambdas `.map{}` (NotApplicable por tipo_facturacion y el rechazo del PAC
 *    con exitoso=false o CUFE vacío) ESCAPAN del contrato Result porque
 *    kotlin.Result.map no captura excepciones del transform; el FeStepFailure
 *    crudo llega a los callers (ProcessSaleUseCase lo captura con runCatching
 *    genérico perdiendo codigo/mensaje; la ruta directa cae al 500 de
 *    StatusPages). Corregirlo es decisión fiscal -> fuera de alcance.
 *  - Persistencia post-DGI best-effort: un fallo SQL tras aceptación NO revierte
 *    el Success (el documento ya fue aceptado por la DGI).
 *  - Correo best-effort: fallo de envío no falla el flujo; sin correo, no se llama.
 *  - resendInvoiceEmail valida tipo_facturacion y existencia de CUFE.
 */
class PanamaInvoiceProcessorTest {
    private class RecordingPacClient : PanamaElectronicInvoiceClient {
        var authCalls = 0
        var sendCalls = 0
        var emailCalls = 0
        var pdfCalls = 0
        var estadoCalls = 0
        var authResult: Result<PacAuthToken> = Result.success(PacAuthToken("jwt-ok"))
        var sendResult: Result<PacResponse> =
            Result.success(PacResponse(exitoso = true, codigo = "200", mensaje = "OK", cufe = "CUFE-1"))
        var estadoResult: Result<PacEstadoDocumento> =
            Result.success(
                PacEstadoDocumento(codigo = "102", mensaje = "El documento no existe", cufe = null),
            )

        override suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken> {
            authCalls++
            return authResult
        }

        override suspend fun sendDocument(
            baseUrl: String,
            token: PacAuthToken,
            payload: TheFactoryHkaDocumentoWrapper,
        ): Result<PacResponse> {
            sendCalls++
            return sendResult
        }

        override suspend fun consultarEstadoDocumento(
            baseUrl: String,
            token: PacAuthToken,
            solicitud: PacEstadoDocumentoSolicitud,
        ): Result<PacEstadoDocumento> {
            estadoCalls++
            consultedNumeroDocumento = solicitud.numeroDocumentoFiscal
            return estadoResult
        }

        var consultedNumeroDocumento: String? = null

        override suspend fun sendEmail(
            baseUrl: String,
            token: PacAuthToken,
            cufe: String,
            emails: List<String>,
        ): Result<TheFactoryEnviarCorreoResponse> {
            emailCalls++
            return Result.success(TheFactoryEnviarCorreoResponse(codigo = "200"))
        }

        override suspend fun downloadPdf(
            baseUrl: String,
            token: PacAuthToken,
            cufe: String,
        ): Result<ByteArray> {
            pdfCalls++
            return Result.success(ByteArray(0))
        }
    }

    private class FakeRepository(
        private val context: InvoiceFEContext?,
        private val loadError: Throwable? = null,
        var updateCalls: Int = 0,
        var incrementCalls: Int = 0,
        var updateError: Throwable? = null,
        var storedCufe: String? = null,
    ) : ElectronicInvoiceRepository() {
        override suspend fun loadInvoiceContext(
            database: Database,
            invoiceId: String,
        ): InvoiceFEContext {
            loadError?.let { throw it }
            return context!!
        }

        override suspend fun updateInvoiceWithFEResponse(
            database: Database,
            update: FeResponseUpdate,
        ): Int {
            updateError?.let { throw it }
            updateCalls++
            storedCufe = update.cufe
            return 1
        }

        override suspend fun incrementNumeroDocumentoFiscal(database: Database) {
            incrementCalls++
        }

        override suspend fun getInvoiceCufe(
            database: Database,
            invoiceId: String,
        ): String? = storedCufe
    }

    private val database =
        Database.connect(
            "jdbc:h2:mem:pa_processor_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "org.h2.Driver",
        )

    private fun processor(
        repository: ElectronicInvoiceRepository,
        client: PanamaElectronicInvoiceClient,
    ) = PanamaInvoiceProcessor(repository, client, TheFactoryHkaPayloadBuilder())

    @Test
    fun `tipo facturacion menor a 3 nunca contacta al PAC y escapa como excepcion cruda`() =
        runBlocking {
            val client = RecordingPacClient()
            val repo = FakeRepository(context(facturacion = 0))
            val result =
                try {
                    processor(repo, client).processElectronicInvoice(database, "F-1")
                    error("se esperaba escape")
                } catch (e: RuntimeException) {
                    e
                }
            // Characterization: el NotApplicable se lanza dentro de un .map{} y
            // Result.map NO captura excepciones del transform -> escapa el
            // FeStepFailure crudo (clase privada) en lugar de retornarse.
            assertEquals("FeStepFailure", result::class.simpleName)
            assertEquals(0, client.authCalls)
            assertEquals(0, client.sendCalls)
        }

    @Test
    fun `factura inexistente mapea a INVOICE_NOT_FOUND`() =
        runBlocking {
            val repo = FakeRepository(context = null, loadError = FEInvoiceNotFoundException("no existe"))
            val result = processor(repo, RecordingPacClient()).processElectronicInvoice(database, "F-404")
            assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("INVOICE_NOT_FOUND", result.codigo)
        }

    @Test
    fun `configuracion faltante mapea a CONFIG_ERROR`() =
        runBlocking {
            val repo = FakeRepository(context = null, loadError = FEConfigurationException("sin credenciales PAC"))
            val result = processor(repo, RecordingPacClient()).processElectronicInvoice(database, "F-2")
            assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("CONFIG_ERROR", result.codigo)
        }

    @Test
    fun `fallo de autenticacion PAC mapea a AUTH_ERROR`() =
        runBlocking {
            val client = RecordingPacClient()
            client.authResult = Result.failure(PacCommunicationException("HTTP 401"))
            val result = processor(FakeRepository(context()), client).processElectronicInvoice(database, "F-3")
            assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("AUTH_ERROR", result.codigo)
            assertEquals(0, client.sendCalls, "no debe enviarse documento si no hay token")
        }

    @Test
    fun `rechazo de negocio del PAC no persiste respuesta ni consume correlativo`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult =
                Result.success(PacResponse(exitoso = false, codigo = "422", mensaje = "RUC invalido"))
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-4")
            // El rechazo se retorna como Failure tipado (codigo/mensaje del PAC
            // disponibles para los clientes) sin persistir datos fiscales.
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("422", failure.codigo)
            assertEquals("RUC invalido", failure.mensaje)
            assertEquals(false, failure.reintentable)
            assertEquals(1, client.sendCalls)
            assertEquals(0, client.estadoCalls, "un rechazo no duplicado no concilia")
            assertEquals(0, repo.updateCalls, "un rechazo no persiste datos fiscales")
            assertEquals(0, repo.incrementCalls, "un rechazo no consume correlativo")
        }

    @Test
    fun `respuesta exitosa sin CUFE se trata como rechazo - no persiste ni incrementa`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult =
                Result.success(PacResponse(exitoso = true, codigo = "200", mensaje = "OK", cufe = null))
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-5")
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals(0, repo.updateCalls)
            assertEquals(0, repo.incrementCalls)
            assertEquals("200", failure.codigo)
        }

    @Test
    fun `flujo feliz persiste CUFE incrementa correlativo y envia correo`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult =
                Result.success(
                    PacResponse(
                        exitoso = true,
                        codigo = "200",
                        mensaje = "Proceso Exitoso",
                        cufe = "CUFE-ABC",
                        qr = "QR-DATA",
                    ),
                )
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-6")
            val success = assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("CUFE-ABC", success.cufe)
            assertEquals("QR-DATA", success.qr)
            assertEquals(1, repo.updateCalls)
            assertEquals(1, repo.incrementCalls)
            assertEquals(1, client.emailCalls, "con correo configurado se envía")
        }

    @Test
    fun `error SQL post aceptacion DGI no revierte el Success`() =
        runBlocking {
            val client = RecordingPacClient()
            val repo =
                FakeRepository(
                    context(),
                    updateError = SQLException("db caida"),
                )
            val result = processor(repo, client).processElectronicInvoice(database, "F-7")
            // El documento YA fue aceptado por la DGI: el resultado sigue siendo
            // Success aunque la persistencia local haya fallado.
            val success = assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("CUFE-1", success.cufe)
        }

    @Test
    fun `cliente sin correo omite el envio de correo sin fallar el flujo`() =
        runBlocking {
            val client = RecordingPacClient()
            val repo = FakeRepository(context(clienteCorreo = null))
            val result = processor(repo, client).processElectronicInvoice(database, "F-8")
            assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals(0, client.emailCalls)
        }

    /**
     * TASK conciliación anti-1513: un timeout es un resultado INCIERTO — el
     * documento PUDO haberse creado en el PAC. Antes de fallar, el procesador
     * consulta EstadoDocumento; si el PAC no lo conoce, falla como SEND_ERROR
     * `reintentable` sin persistir datos fiscales ni consumir correlativo.
     */
    @Test
    fun `timeout de transporte concilia con el PAC y sin documento falla reintentable sin persistir`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult = Result.failure(IOException("Timeout esperando DGI"))
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-9")
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("SEND_ERROR", failure.codigo)
            assertTrue(
                failure.mensaje.contains("comunicación con el PAC"),
                "el mensaje debe identificar el problema de comunicación: ${failure.mensaje}",
            )
            assertEquals(1, client.sendCalls)
            assertEquals(1, client.estadoCalls, "ante un timeout se consulta EstadoDocumento antes de fallar")
            assertTrue(failure.reintentable == true, "un timeout es reintentable si el PAC no conoce el documento")
            assertEquals(0, repo.updateCalls)
            assertEquals(0, repo.incrementCalls)
        }

    @Test
    fun `timeout con documento autorizado en el PAC recupera CUFE persiste e incrementa`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult = Result.failure(IOException("Timeout esperando DGI"))
            client.estadoResult =
                Result.success(
                    PacEstadoDocumento(
                        codigo = "200",
                        mensaje = "Documento autorizado",
                        cufe = "CUFE-RECUPERADO",
                        fechaRecepcionDGI = "2026-09-01T10:00:00",
                    ),
                )
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-10")
            val success = assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("CUFE-RECUPERADO", success.cufe)
            assertEquals(1, client.estadoCalls)
            assertEquals("00000000000000000001", client.consultedNumeroDocumento)
            assertEquals(1, repo.updateCalls, "el CUFE recuperado se persiste en la factura")
            assertEquals(1, repo.incrementCalls, "el número consumido por la DGI se da por usado")
        }

    @Test
    fun `rechazo 1513 de la DGI concilia y recupera el CUFE del primer envio`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult =
                Result.success(
                    PacResponse(
                        exitoso = false,
                        codigo = "203",
                        mensaje = "1513-Número del documento fiscal duplicado.",
                    ),
                )
            client.estadoResult =
                Result.success(
                    PacEstadoDocumento(
                        codigo = "200",
                        mensaje = "Documento autorizado",
                        cufe = "CUFE-PRIMER-ENVIO",
                    ),
                )
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-11")
            val success = assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("CUFE-PRIMER-ENVIO", success.cufe)
            assertEquals(1, client.estadoCalls)
            assertEquals(1, repo.updateCalls)
            assertEquals(1, repo.incrementCalls)
        }

    @Test
    fun `rechazo fiscal 2007 no concilia y no es reintentable`() =
        runBlocking {
            val client = RecordingPacClient()
            client.sendResult =
                Result.success(
                    PacResponse(
                        exitoso = false,
                        codigo = "203",
                        mensaje = "2007-Item 1: No informado ningún código de producto en la Codificación Panameña de Bienes y Servicios.",
                    ),
                )
            val repo = FakeRepository(context())
            val result = processor(repo, client).processElectronicInvoice(database, "F-12")
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals(listOf("2007"), failure.incidenciasFiscales.map { it.codigo })
            assertEquals(false, failure.reintentable, "2007 exige corrección de datos, no reintento")
            assertEquals(0, client.estadoCalls, "2007 exige corrección de datos, no conciliación")
            assertEquals(0, repo.updateCalls)
            assertEquals(0, repo.incrementCalls)
        }

    private fun config(facturacion: Int) =
        FEConfigData(
            tokenEmpresa = "token-demo",
            tokenPassword = "pass-demo",
            apiTheFactoryHka = "https://pac.invalid",
            tipoEmision = "01",
            destinoOperacion = "1",
            procesoGeneracion = "1",
            codigoSucursalEmisorFallback = "0001",
            puntoFacturacionFiscalFallback = "001",
            fechaInicioContingencia = null,
            motivoContingencia = null,
            tipoFacturacion = facturacion,
        )

    private fun factura() =
        FEFacturaData(
            idFactura = "F-1",
            codFactura = "FAC-000001",
            numeroDocumentoFiscal = "00000000000000000001",
            fechaFactura = "2026-08-21 10:00:00",
            tipoDocumento = "01",
            naturalezaOperacion = "01",
            tipoOperacion = "1",
            formatoCAFE = "1",
            entregaCAFE = "1",
            envioContenedor = "1",
            tipoVenta = "01",
            tipoFactura = "01",
            observacion = null,
            montoItemsFactura = 100.0,
            ivaTotalFactura = 7.0,
            totalTotalFactura = 107.0,
            totalizarDescuentoGlobal = 0.0,
            cajaId = "C1",
        )

    private fun cliente(correo: String?) =
        FEClienteData(
            tipoClienteFE = "02",
            tipoContribuyente = "1",
            identificacion = "0000000-1-000000",
            dv = "00",
            nombre = "CONSUMIDOR FINAL",
            codigoUbicacion = "8-1-1",
            telefono = "000-0000",
            correo = correo,
            direccion = "Calle 1",
            paisIso = "PA",
            paisExtranjeroIso = null,
        )

    private fun detalle() =
        FEDetalleData(
            descripcion = "Item 1",
            codigo = "P001",
            unidadMedida = "und",
            codigoCPBS = "C001",
            codigoCPBSAbrev = "C0",
            cantidad = 1.0,
            precioSinIva = 100.0,
            montoDescuento = 0.0,
            piva = 7.0,
            totalSinIva = 100.0,
            totalConIva = 107.0,
            porcentajeIsc = null,
            importeIsc = null,
            idOti = null,
            importeOti = null,
        )

    private fun context(
        facturacion: Int = 3,
        clienteCorreo: String? = "cliente@example.com",
    ): InvoiceFEContext =
        InvoiceFEContext(
            config = config(facturacion),
            factura = factura(),
            cliente = cliente(clienteCorreo),
            detalles = listOf(detalle()),
            formasPago =
                listOf(
                    FEFormaPagoData(siglas = "EF", formaPagoFact = "02", descripcion = "Efectivo", monto = 107.0),
                ),
            retencion = null,
            montoCancelar = 107.0,
            codigoSucursalEmisor = "0001",
            puntoFacturacionFiscal = "001",
            vuelto = null,
        )
}
