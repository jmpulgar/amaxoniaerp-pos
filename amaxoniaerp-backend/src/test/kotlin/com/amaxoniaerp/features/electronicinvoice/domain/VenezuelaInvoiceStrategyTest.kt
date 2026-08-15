package com.amaxoniaerp.features.electronicinvoice.domain

import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaAuthResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClientException
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaEmisionResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaEmisionResultado
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaUltimoDocumentoRequest
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaUltimoDocumentoResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaUltimoDocumentoResultado
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests del orquestador [VenezuelaInvoiceStrategy].
 *
 * Cubren el flujo end-to-end con un repositorio y cliente HKA en memoria:
 *  - 1. (FASE 1.1) tipo_facturacion YA NO gobierna la activación del digital.
 *       La selección HKA20 vs digital la decide el UseCase con `useHka20` antes
 *       de invocar la strategy; si llega aquí, se ejecuta el digital sin
 *       mirar parametros_generales.
 *  - 2. tipoDocumento != 01 → UnsupportedDocumentType (no HKA).
 *  - 3. factura ya emitida → AlreadyIssued (no HKA).
 *  - 4/5. Autenticación exitosa vs rechazada.
 *  - 9. Emisión exitosa persiste los tres campos fiscales.
 *  - 10. codigo de negocio != 200 → Failure, sin persistencia.
 *  - 13. Timeout emisión → Uncertain, sin persistencia ni reintento.
 *  - 24. Emisión fallida NO persiste datos fiscales.
 *
 * Los números 7 y 8 (correlativo local mayor/menor que remoto) también se
 * validan aquí forzando el remoto del fake.
 */
class VenezuelaInvoiceStrategyTest {
    /**
     * DB en memoria que se pasa a la Strategy para satisfacer la firma del
     * contrato. En estos tests los fakes NO abren transacciones contra ella;
     * solo importa que la instancia no sea null.
     */
    private val db: Database =
        Database.connect("jdbc:h2:mem:ve_strategy_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")

    // ─── 1. (FASE 1.1) tipo_facturacion YA NO decide HKA20 en la strategy ────

    @Test
    fun `tipo_facturacion distinto de 5 ya NO retorna NotApplicable porque la seleccion HKA20 vive en el UseCase`() =
        runBlocking {
            // FASE 1.1: la selección HKA20 vs digital NO se deduce de parametros_generales.
            // Si el flujo llega a la strategy es porque.useHka20 != true → ejecutar digital.
            // Por tanto, con tipo_facturacion=0 (antes era "HKA20") la strategy ahora SÍ
            // procede: autentica y emite. Lo verificamos forzando un auth OK + emision OK.
            val repo = fakeRepo(tipoFacturacion = 0)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emision =
                        VenezuelaHkaResponse(
                            httpStatus = 200,
                            rawBody = "{}",
                            codigo = "200",
                            mensaje = "OK",
                            validaciones = emptyList(),
                            resultado =
                                VenezuelaHkaEmisionResponse(
                                    codigo = "200",
                                    mensaje = "El documento fue procesado con éxito.",
                                    resultado =
                                        VenezuelaHkaEmisionResultado(
                                            numeroDocumento = "0000000051",
                                            numeroControl = "L001P0010000000051",
                                        ),
                                ),
                        ),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-1")

            assertIs<ElectronicInvoiceResult.Success>(result, "Con tipo_facturacion=0 ahora debe proceder el digital")
            assertEquals(1, client.authCalls, "Debe autenticar porque useHka20 ya no se deduce aquí")
            assertEquals("0000000051", repo.persistedNumeroDocumento)
        }

    // ─── 3. Factura ya emitida → AlreadyIssued ────────────────────────────

    @Test
    fun `factura ya emitida retorna AlreadyIssued sin llamar HKA`() =
        runBlocking {
            val repo =
                fakeRepo(
                    tipoFacturacion = 5,
                    alreadyIssued = AlreadyIssuedResult.Complete("00000100", "L001P001-100"),
                )
            val client = CountingClient()
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-2")

            assertIs<ElectronicInvoiceResult.AlreadyIssued>(result)
            assertEquals("00000100", result.numeroDocumentoFiscal)
            assertEquals("L001P001-100", result.numeroControl)
            assertEquals(0, client.authCalls, "No se debe autenticar si la factura ya está emitida")
        }

    // ─── FASE 1.1 — Item 1: idempotencia OR ──────────────────────────────

    @Test
    fun `idempotencia OR - solo numeroDocumentoFiscal presente retorna Failure PARTIAL y NO llama HKA`() =
        runBlocking {
            val repo =
                fakeRepo(
                    tipoFacturacion = 5,
                    alreadyIssued = AlreadyIssuedResult.Partial(numeroDocumentoFiscal = "00000099", numeroControl = null),
                )
            val client = CountingClient()
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-or-1")

            val failure = assertIs<ElectronicInvoiceResult.Failure>(result, "Solo numDoc → Partial → Failure")
            assertEquals("PARTIAL_FISCAL_DATA", failure.codigo)
            assertEquals(0, client.authCalls, "OR: con solo numDoc NO se debe llamar al PAC")
        }

    @Test
    fun `idempotencia OR - solo numero_control_thka presente retorna Failure PARTIAL y NO llama HKA`() =
        runBlocking {
            val repo =
                fakeRepo(
                    tipoFacturacion = 5,
                    alreadyIssued = AlreadyIssuedResult.Partial(numeroDocumentoFiscal = null, numeroControl = "L001P001-200"),
                )
            val client = CountingClient()
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-or-2")

            val failure = assertIs<ElectronicInvoiceResult.Failure>(result, "Solo numCtrl → Partial → Failure")
            assertEquals("PARTIAL_FISCAL_DATA", failure.codigo)
            assertEquals(0, client.authCalls, "OR: con solo numCtrl NO se debe llamar al PAC")
        }

    @Test
    fun `idempotencia OR - ambos ausentes continua el flujo de emision`() =
        runBlocking {
            val repo =
                fakeRepo(
                    tipoFacturacion = 5,
                    alreadyIssued = AlreadyIssuedResult.None,
                )
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emision = okEmision("00000005"),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-or-3")

            assertIs<ElectronicInvoiceResult.Success>(result, "None → continuar emisión")
            assertEquals(1, client.authCalls, "OR: con ambos ausentes SÍ se debe llamar al PAC")
        }

    // ─── 2. tipoDocumento != 01 → UnsupportedDocumentType ──────────────────

    @Test
    fun `tipoDocumento distinto de 01 retorna UnsupportedDocumentType sin llamar HKA`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5, tipoDocumento = "04") // nota crédito
            val client = CountingClient()
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-3")

            assertIs<ElectronicInvoiceResult.UnsupportedDocumentType>(result)
            assertEquals("04", result.tipoDocumento)
            assertEquals(0, client.authCalls, "No se debe autenticar si el tipoDocumento no está soportado")
        }

    // ─── 4. Autenticación exitosa + 9. Emisión exitosa ────────────────────

    @Test
    fun `emision exitosa retorna Success y persiste los tres campos fiscales`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emision =
                        VenezuelaHkaResponse(
                            httpStatus = 200,
                            rawBody = """{"codigo":"200","resultado":{"numeroDocumento":"00000060","numeroControl":"L001P001-60"}}""",
                            codigo = "200",
                            mensaje = "OK",
                            validaciones = emptyList(),
                            resultado =
                                VenezuelaHkaEmisionResponse(
                                    codigo = "200",
                                    mensaje = "OK",
                                    resultado =
                                        VenezuelaHkaEmisionResultado(
                                            numeroDocumento = "00000060",
                                            numeroControl = "L001P001-60",
                                        ),
                                ),
                        ),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-4")

            assertIs<ElectronicInvoiceResult.Success>(result)
            // Persistencia: los tres campos fiscales.
            assertEquals("00000060", repo.persistedNumeroDocumento)
            assertEquals("L001P001-60", repo.persistedNumeroControl)
            // FASE 2 (Punto 1): Success expone campos PROPIOS de VE; cufe/qr/nroProtocolos
            // (de Panamá) se mantienen en null.
            assertEquals("00000060", result.numeroDocumentoFiscal)
            assertEquals("L001P001-60", result.numeroControlThka)
            assertNull(result.cufe, "Venezuela NO debe transportar via cufe de Panamá")
            assertNull(result.nroProtocoloAutorizacion, "Venezuela NO debe transportar via nroProtocoloAutorizacion")
            assertNull(result.qr)
            assertNull(result.fechaRecepcionDGI)
        }

    // ─── 5. Autenticación rechazada → Failure(AUTH_REJECTED) ──────────────

    @Test
    fun `autenticacion rechazada 401 retorna Failure AUTH_REJECTED sin reservar correlativo`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5).apply { failReserveOnCall = true }
            val client =
                CountingClient(
                    auth =
                        VenezuelaHkaResponse(
                            httpStatus = 401,
                            rawBody = """{"mensaje":"Credenciales inválidas"}""",
                            codigo = "HTTP_401",
                            mensaje = "Credenciales inválidas",
                            validaciones = emptyList(),
                            resultado = VenezuelaHkaAuthResponse(token = null, mensaje = "Credenciales inválidas"),
                        ),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-5")

            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("AUTH_REJECTED", failure.codigo)
            assertEquals(0, repo.reservedTimes, "No se debe reservar correlativo si auth falla")
        }

    // ─── 10. codigo de negocio != 200 ──────────────────────────────────────

    @Test
    fun `codigo de negocio distinto de 200 retorna Failure y NO persiste`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emision =
                        VenezuelaHkaResponse(
                            httpStatus = 200,
                            rawBody = """{"codigo":"422","mensaje":"RIF invalido"}""",
                            codigo = "422",
                            mensaje = "RIF invalido",
                            validaciones = listOf("rif"),
                            resultado = VenezuelaHkaEmisionResponse(codigo = "422", mensaje = "RIF invalido", resultado = null),
                        ),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-6")

            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("422", failure.codigo)
            assertEquals("RIF invalido", failure.mensaje)
            assertNull(repo.persistedNumeroDocumento, "codigo != 200 no debe persistir número fiscal")
            assertNull(repo.persistedNumeroControl)
        }

    // ─── 13. Timeout en emisión → Uncertain ────────────────────────────────

    @Test
    fun `timeout en emision retorna Uncertain y NO persiste ni reintenta`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emisionThrow = VenezuelaHkaClientException.Timeout("Timeout simulado en Emision"),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-7")

            val uncertain = assertIs<ElectronicInvoiceResult.Uncertain>(result)
            assertEquals("EMISION_TIMEOUT", uncertain.codigo)
            assertNull(repo.persistedNumeroDocumento, "Timeout no debe persistir número inventado")
            assertNull(repo.persistedNumeroControl)
            assertEquals(1, client.emissionCalls, "NO debe reintentar automáticamente tras timeout")
        }

    @Test
    fun `timeout en autenticacion retorna Uncertain AUTH_NET_ERROR`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5)
            val client =
                CountingClient(
                    authThrow = VenezuelaHkaClientException.Timeout("Timeout auth"),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-8")

            val uncertain = assertIs<ElectronicInvoiceResult.Uncertain>(result)
            assertEquals("AUTH_NET_ERROR", uncertain.codigo)
        }

    // ─── 7 y 8. max(local, remoto+1) ───────────────────────────────────────

    @Test
    fun `correlativo local mayor que remoto usa el local`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5, contadorInicial = 80, formato = 8)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"), // remoto+1 = 51 < local 80
                    emision = okEmision("00000080"),
                )
            val strategy = strategy(repo, client)
            val result = strategy.processElectronicInvoice(db, "inv-9")
            assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("00000080", repo.lastEmittedNumberPassedToBuilder)
        }

    @Test
    fun `correlativo remoto mayor que local usa remoto_mas_uno`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5, contadorInicial = 10, formato = 8)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("99"), // remoto+1 = 100 > local 10
                    emision = okEmision("00000100"),
                )
            val strategy = strategy(repo, client)
            val result = strategy.processElectronicInvoice(db, "inv-9b")
            assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("00000100", repo.lastEmittedNumberPassedToBuilder)
        }

    // ─── 6. UltimoDocumento disponible pero fallido → cae a reservar local ─

    @Test
    fun `UltimoDocumento no concluyente cae al correlativo local sin fallar`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5, contadorInicial = 5, formato = 8)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento =
                        VenezuelaHkaResponse(
                            httpStatus = 404,
                            rawBody = "",
                            codigo = "HTTP_404",
                            mensaje = "Aún no hay documentos",
                            validaciones = emptyList(),
                            resultado = null,
                        ),
                    emision = okEmision("00000005"),
                )
            val strategy = strategy(repo, client)
            val result = strategy.processElectronicInvoice(db, "inv-10")
            assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("00000005", repo.lastEmittedNumberPassedToBuilder)
        }

    // ─── 24. No persistir cuando la emisión falla ─────────────────────────

    @Test
    fun `emision fallida con HTTP 400 no persista datos fiscales`() =
        runBlocking {
            val repo = fakeRepo(tipoFacturacion = 5)
            val client =
                CountingClient(
                    auth = okAuth(),
                    ultimoDocumento = okUltimo("50"),
                    emision =
                        VenezuelaHkaResponse(
                            httpStatus = 400,
                            rawBody = """Bad Request""",
                            codigo = "HTTP_400",
                            mensaje = "HTTP 400 desde PAC VE",
                            validaciones = emptyList(),
                            resultado = null,
                        ),
                )
            val strategy = strategy(repo, client)

            val result = strategy.processElectronicInvoice(db, "inv-11")

            assertIs<ElectronicInvoiceResult.Failure>(result)
            assertNull(repo.persistedNumeroDocumento)
            assertNull(repo.persistedNumeroControl)
        }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun strategy(
        repo: FakeRepo,
        client: VenezuelaHkaClient,
    ): VenezuelaInvoiceStrategy =
        VenezuelaInvoiceStrategy(
            repository = repo,
            hkaClient = client,
            payloadBuilder = VenezuelaHkaPayloadBuilder(), // Item 3: el número ya viene del reserveAtLeast
            defaultSerie = "L001P001",
        )

    private fun okAuth() =
        VenezuelaHkaResponse(
            httpStatus = 200,
            rawBody = """{"token":"jwt-1"}""",
            codigo = "200",
            mensaje = "OK",
            validaciones = emptyList(),
            resultado = VenezuelaHkaAuthResponse(token = "jwt-1", mensaje = "OK"),
        )

    private fun okUltimo(num: String) =
        VenezuelaHkaResponse(
            httpStatus = 200,
            rawBody = """{"codigo":"200","resultado":{"ultimoNumero":"$num"}}""",
            codigo = "200",
            mensaje = "OK",
            validaciones = emptyList(),
            resultado =
                VenezuelaHkaUltimoDocumentoResponse(
                    codigo = "200",
                    mensaje = "OK",
                    resultado = VenezuelaHkaUltimoDocumentoResultado(ultimoNumero = num),
                ),
        )

    private fun okEmision(numDoc: String) =
        VenezuelaHkaResponse(
            httpStatus = 200,
            rawBody = """{"codigo":"200","resultado":{"numeroDocumento":"$numDoc","numeroControl":"L001P001-$numDoc"}}""",
            codigo = "200",
            mensaje = "OK",
            validaciones = emptyList(),
            resultado =
                VenezuelaHkaEmisionResponse(
                    codigo = "200",
                    mensaje = "OK",
                    resultado =
                        VenezuelaHkaEmisionResultado(
                            numeroDocumento = numDoc,
                            numeroControl = "L001P001-$numDoc",
                        ),
                ),
        )

    /** Repositorio falso que mantiene contadores observables sin tocar la DB. */
    private class FakeRepo(
        tipoFacturacion: Int = 5,
        val alreadyIssued: AlreadyIssuedResult = AlreadyIssuedResult.None,
        tipoDocumento: String = "01",
        val contadorInicial: Int = 1,
        val formato: Int = 8,
        var failReserveOnCall: Boolean = false,
    ) : VenezuelaElectronicInvoiceRepository() {
        var reservedTimes = 0
            private set
        var persistedNumeroDocumento: String? = null
            private set
        var persistedNumeroControl: String? = null
            private set
        var lastEmittedNumberPassedToBuilder: String? = null

        private val ctx: InvoiceVEContext = sampleVEContext(tipoFacturacion, tipoDocumento)

        override suspend fun loadInvoiceContext(
            database: Database,
            invoiceId: String,
        ): InvoiceVEContext = ctx

        override suspend fun loadAlreadyIssued(
            database: Database,
            invoiceId: String,
        ): AlreadyIssuedResult = alreadyIssued

        /**
         * FASE 1.1 — Item 3: simula reserveAtLeast.
         *
         * El fake NO verifica contención; respeta el contrato de "reservado =
         * max(contadorActual, minimumNextNumber)". El contadorInicial es el
         * valor que tendría la DB para esta corrida.
         */
        override suspend fun reserveAtLeast(
            database: Database,
            minimumNextNumber: Int,
        ): VECorrelativoReservado {
            if (failReserveOnCall) error("No se debería haber llamado a reservar correlativo")
            reservedTimes += 1
            val reservado =
                VECorrelativoReservado(
                    numero = maxOf(contadorInicial, minimumNextNumber),
                    formato = formato,
                )
            // Capturamos el número final EMITIDO para los tests que lo verifican.
            lastEmittedNumberPassedToBuilder = reservado.numeroFormateado()
            return reservado
        }

        override suspend fun reserveCorrelativoFacturaElectronica(database: Database): VECorrelativoReservado =
            reserveAtLeast(database, minimumNextNumber = 1)

        override suspend fun updateInvoiceWithVEResult(
            database: Database,
            invoiceId: String,
            numeroDocumento: String,
            numeroControl: String,
        ) {
            persistedNumeroDocumento = numeroDocumento
            persistedNumeroControl = numeroControl
        }

        /**
         * FASE 2 (Punto 5): el FakeRepo devuelve exactamente lo que se
         * persistió en memoria, imitando la regla "Success se construye con
         * los valores persistidos en factura, no con el objeto inmediato HKA".
         */
        override suspend fun loadFiscalDataForResponse(
            database: Database,
            invoiceId: String,
        ): VenezuelaElectronicInvoiceRepository.FiscalSnapshot =
            VenezuelaElectronicInvoiceRepository.FiscalSnapshot(
                numeroDocumentoFiscal = persistedNumeroDocumento,
                numeroControlThka = persistedNumeroControl,
            )
    }

    /** Cliente HKA fake observable y programable. */
    private class CountingClient(
        val auth: VenezuelaHkaResponse<VenezuelaHkaAuthResponse>? = null,
        val authThrow: VenezuelaHkaClientException? = null,
        val ultimoDocumento: VenezuelaHkaResponse<VenezuelaHkaUltimoDocumentoResponse>? = null,
        val ultimoDocumentoThrow: VenezuelaHkaClientException? = null,
        val emision: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse> = defaultEmisionFail(),
        val emisionThrow: VenezuelaHkaClientException? = null,
    ) : VenezuelaHkaClient {
        var authCalls = 0
            private set
        var ultimoCalls = 0
            private set
        var emissionCalls = 0
            private set

        override suspend fun authenticate(credentials: PacCredentials) =
            applyThrow(authThrow) {
                authCalls += 1
                auth ?: error("auth no configurado")
            }

        override suspend fun fetchLastDocument(
            baseUrl: String,
            token: PacAuthToken,
            request: VenezuelaHkaUltimoDocumentoRequest,
        ) = applyThrow(ultimoDocumentoThrow) {
            ultimoCalls += 1
            ultimoDocumento ?: error("ultimoDocumento no configurado")
        }

        override suspend fun emitDocument(
            baseUrl: String,
            token: PacAuthToken,
            payload: VenezuelaHkaDocumentoWrapper,
        ): VenezuelaHkaResponse<VenezuelaHkaEmisionResponse> {
            // El intento de emisión SI ocurre (timeout sobreviene durante la llamada).
            // Lo contamos ANTES de tirar para poder validar "no reintentar => 1 solo intento".
            emissionCalls += 1
            if (emisionThrow != null) throw emisionThrow
            return emision
        }

        private inline fun <T> applyThrow(
            thrown: VenezuelaHkaClientException?,
            block: () -> T,
        ): T {
            if (thrown != null) throw thrown
            return block()
        }

        companion object {
            private fun defaultEmisionFail() =
                VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>(
                    httpStatus = 0,
                    rawBody = "",
                    codigo = "NOT_CONFIGURED",
                    mensaje = "emision no configurada en fake",
                    validaciones = emptyList(),
                    resultado = null,
                )
        }
    }

    private fun fakeRepo(
        tipoFacturacion: Int = 5,
        alreadyIssued: AlreadyIssuedResult = AlreadyIssuedResult.None,
        tipoDocumento: String = "01",
        contadorInicial: Int = 1,
        formato: Int = 8,
    ) = FakeRepo(
        tipoFacturacion = tipoFacturacion,
        alreadyIssued = alreadyIssued,
        tipoDocumento = tipoDocumento,
        contadorInicial = contadorInicial,
        formato = formato,
    )
}

private fun sampleVEContext(
    tipoFacturacion: Int,
    tipoDocumento: String,
): InvoiceVEContext =
    InvoiceVEContext(
        config =
            VEConfigData(
                tipoFacturacion = tipoFacturacion,
                tipoEntornoVe = 0,
                tokenEmpresa = "U",
                tokenPassword = "P",
                baseUrl = "https://demo.thefactoryhka.com.ve",
                rif = "J123456789",
                nombreEmpresa = "EMPRESA",
                direccion = null,
                telefonos = null,
                igtf = BigDecimal("3.000000"),
                procesoGeneracion = "1",
                tipoEmision = "01",
                codigoSucursalEmisorFallback = "0000",
                puntoFacturacionFiscalFallback = "001",
            ),
        factura =
            VEFacturaData(
                idFactura = "inv-x",
                codFactura = "COD",
                numeroDocumentoFiscal = null,
                numeroControlThka = null,
                tipoDocumento = tipoDocumento,
                fechaFactura = "2026-08-03",
                fechaCreacion = null,
                facturarANombre = "CF",
                facturarARuc = "V000000000",
                facturarADireccion = "",
                facturarATelefono = "",
                totalTotalFactura = BigDecimal("116.00"),
                ivaTotalFactura = BigDecimal("16.00"),
                descuentosItemFactura = BigDecimal("0.00"),
                totalizarBaseImponible = BigDecimal("100.00"),
                totalizarMontoIva = BigDecimal("16.00"),
                totalizarTotalGeneral = BigDecimal("116.00"),
                montoItemsFactura = BigDecimal("100.00"),
                multiMoneda = "NO",
                tasa = BigDecimal.ONE,
                monedaBase = 1,
                abrMonedaBase = "VES",
                monedaSecundaria = 2,
                abrMonedaSecundaria = "USD",
            ),
        comprador = VECompradorData("CF", "V000000000", null, null, null),
        detalles = emptyList(),
        formasPago = emptyList(),
        caja = VECajaData("caja-1", "L001", "L001P001", "0000", "001"),
        correlativoReservado = VECorrelativoReservado(1, 8),
    )
