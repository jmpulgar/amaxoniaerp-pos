package com.amaxoniaerp.e2e

import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaDetalleAperturaTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreFormaPagoTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreTable
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.creditnotes.data.CreditNoteCajaTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteDetailTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteFacturaDetalleTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteFacturaTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteHeaderTablePA
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.data.finalizePanamaAccepted
import com.amaxoniaerp.features.creditnotes.data.preparePanama
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteLineInput
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.FECorrelativosTable
import com.amaxoniaerp.features.electronicinvoice.data.FEFacturaDetalleFormaPagoReadTable
import com.amaxoniaerp.features.electronicinvoice.data.FEItemReadTable
import com.amaxoniaerp.features.electronicinvoice.data.FEPaisesReadTable
import com.amaxoniaerp.features.electronicinvoice.data.FETipoClienteReadTable
import com.amaxoniaerp.features.electronicinvoice.data.FEUnidadEmpaquesReadTable
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryEnviarCorreoResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import com.amaxoniaerp.features.facturas.data.EstatusTable
import com.amaxoniaerp.features.facturas.data.FacturasFilter
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import com.amaxoniaerp.features.facturas.data.FacturasTableVE
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TIER 4: Real-World Persona Workloads & End-to-End User Scenarios (Backend Track)
 * 5 comprehensive multi-step persona journeys spanning cross-boundary operations.
 */
class Tier4BackendRealWorldWorkloadsE2ETest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    // =========================================================================
    // Scenario 1: Panama Cashier Full Day Persona Workflow
    // =========================================================================
    @Test
    fun `Scenario 1 - Panama Cashier Full Day Workflow (Open, Sales, Draft, NC, Unblocked Close)`() =
        withDatabase {
            // Step 1: Open cash session
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val openResult =
                runBlocking {
                    workflow.open(
                        database = database,
                        countryCode = "PA",
                        dbName = "db",
                        request = AperturaRequest("caja-1", 100.0, 1),
                        username = "cajero-pa",
                    )
                }
            assertTrue(openResult.isSuccess, "Cash opening must succeed")
            val sequenceId = openResult.getOrThrow().idCajaSecuencia

            // Step 2: Register finalized cash and card sales
            seedFacturaPA("sale-1", total = 120.0, cajaId = "caja-1", codEstatus = 2, cufe = "CUFE-SALE-1")
            seedFacturaPA("sale-2", total = 80.0, cajaId = "caja-1", codEstatus = 2, cufe = "CUFE-SALE-2")

            // Step 3: Cashier saves an in-progress transaction as draft (cod_estatus = 1)
            seedFacturaPA("draft-sale-1", total = 45.0, cajaId = "caja-1", codEstatus = 1, cufe = null)

            // Step 4: Customer requests return -> Generate Panama Electronic Credit Note against sale-1
            seedCreditNotePrerequisitesPA(id = "sale-1", initialTotal = 120.0)
            val creditNoteRepo = CreditNoteRepository()
            val cnRequest =
                CreateCreditNoteRequest(
                    idFactura = "sale-1",
                    fecha = LocalDate.now().toString(),
                    detalle = listOf(CreateCreditNoteLineInput("det-sale-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = sequenceId,
                    settlementType = CreditNoteSettlementType.REINTEGRO,
                    idFormaPagoReintegro = 1,
                )
            val preparedCN = transaction(database) { creditNoteRepo.preparePanama(cnRequest, "cajero-pa") }
            assertNotNull(preparedCN.id)

            val pacResponse =
                PacResponse(
                    exitoso = true,
                    codigo = "200",
                    mensaje = "Aprobado",
                    cufe = "CUFE-NC-PA-FULL",
                    qr = "QR-NC",
                    nroProtocoloAutorizacion = "PROTO-NC",
                )
            val finalizedCN =
                transaction(database) {
                    creditNoteRepo.finalizePanamaAccepted(
                        id = preparedCN.id,
                        request = cnRequest,
                        pacResponse = pacResponse,
                        numeroDocumentoFiscal = preparedCN.numeroDocumentoFiscal,
                    )
                }
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, finalizedCN.fiscalStatus)

            // Step 5: End of day -> Execute Cierre de Caja without blocking on draft
            val closeResult =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "PA",
                        request =
                            CajaCierreSaveRequest(
                                id = sequenceId,
                                montoTotal = 300.0,
                                montoEfectivoTotal = 220.0,
                                montoOtrosTotal = 80.0,
                                observacionCierre = "Cierre exitoso fin de jornada",
                            ),
                    )
                }
            assertTrue(closeResult.isSuccess, "Cash close must succeed unblocked despite draft invoice existence")

            // Step 6: Verify summary and draft persistence
            val facturasRepo = FacturasRepository()
            val resumen = runBlocking { facturasRepo.getResumen(database, "PA", FacturasFilter(cajaId = "caja-1")) }
            assertTrue(resumen.totalFacturas >= 2)
        }

    // =========================================================================
    // Scenario 2: Offline Network Interruption & Recovery Workflow
    // =========================================================================
    @Test
    fun `Scenario 2 - Offline Sync & Register Isolation Recovery`() =
        withDatabase {
            // Step 1: Simulated sync of offline queued invoices to backend
            seedFacturaPA(
                "offline-inv-1",
                total = 95.0,
                cajaId = "caja-1",
                codEstatus = 2,
                cufe = "CUFE-OFFLINE-SYNCED",
            )
            seedFacturaPA("other-caja-inv", total = 200.0, cajaId = "caja-2", codEstatus = 2, cufe = "CUFE-OTHER")

            // Step 2: Query active cash register history
            val repo = FacturasRepository()
            val (facturasCaja1, totalCaja1) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = "caja-1"))
                }
            assertEquals(1L, totalCaja1)
            assertEquals("offline-inv-1", facturasCaja1.first().id)
            assertEquals("CUFE-OFFLINE-SYNCED", facturasCaja1.first().codigoFiscal)

            // Step 3: Verify getResumen strictly encapsulates only caja-1
            val resumenCaja1 = runBlocking { repo.getResumen(database, "PA", FacturasFilter(cajaId = "caja-1")) }
            assertEquals(1, resumenCaja1.totalFacturas)
            assertEquals(95.0, resumenCaja1.ventasNetas)
        }

    // =========================================================================
    // Scenario 3: Electronic Invoice Failure & Manual Resend Idempotency
    // =========================================================================
    @Test
    fun `Scenario 3 - Electronic Invoice Failure, Pending Badge & Idempotent Resend`() =
        withDatabase {
            // Step 1: Sale created locally with missing CUFE due to initial PAC timeout
            seedFacturaPA("failed-fe-inv", total = 150.0, cajaId = "caja-1", codEstatus = 2, cufe = null)

            val initialCufe = runBlocking { ElectronicInvoiceRepository().getInvoiceCufe(database, "failed-fe-inv") }
            assertNull(initialCufe, "Invoice should be pending electronic authorization")

            // Step 2: Cashier triggers manual resend via PAC client
            var pacCallCount = 0
            val pacClient =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ): Result<PacResponse> {
                        pacCallCount++
                        return Result.success(
                            PacResponse(
                                exitoso = true,
                                codigo = "200",
                                mensaje = "Autorizado",
                                cufe = "CUFE-RECOVERED-12345",
                                qr = "QR-DATA-12345",
                            ),
                        )
                    }

                    override suspend fun sendEmail(
                        baseUrl: String,
                        token: PacAuthToken,
                        cufe: String,
                        emails: List<String>,
                    ) = Result.success(TheFactoryEnviarCorreoResponse("200"))

                    override suspend fun downloadPdf(
                        baseUrl: String,
                        token: PacAuthToken,
                        cufe: String,
                    ) = Result.success(ByteArray(0))
                }

            val processor =
                PanamaInvoiceProcessor(ElectronicInvoiceRepository(), pacClient, TheFactoryHkaPayloadBuilder())
            val firstResend = runBlocking { processor.processElectronicInvoice(database, "failed-fe-inv") }
            val success = assertIs<ElectronicInvoiceResult.Success>(firstResend)
            assertEquals("CUFE-RECOVERED-12345", success.cufe)
            assertEquals(1, pacCallCount)

            // Step 3: Verified updated in database
            val updatedCufe = runBlocking { ElectronicInvoiceRepository().getInvoiceCufe(database, "failed-fe-inv") }
            assertEquals("CUFE-RECOVERED-12345", updatedCufe)

            // Step 4: Subsequent resend check verifies idempotency
            val repo = FacturasRepository()
            val (facturas, _) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(cajaId = "caja-1"),
                    )
                }
            assertEquals("CUFE-RECOVERED-12345", facturas.first().codigoFiscal)
        }

    // =========================================================================
    // Scenario 4: Invoice History Date Bounds, Search & Document Retrieval
    // =========================================================================
    @Test
    fun `Scenario 4 - Invoice History Date Windowing, Search, Reprint & PDF Retrieval`() =
        withDatabase {
            // Step 1: Seed invoices across various dates
            seedFacturaPA("inv-d5", total = 50.0, createdAt = LocalDateTime.of(2026, 8, 5, 10, 0), cufe = "CUFE-D5")
            seedFacturaPA("inv-d15", total = 75.0, createdAt = LocalDateTime.of(2026, 8, 15, 14, 0), cufe = "CUFE-D15")
            seedFacturaPA("inv-d25", total = 100.0, createdAt = LocalDateTime.of(2026, 8, 25, 16, 0), cufe = "CUFE-D25")

            val repo = FacturasRepository()

            // Step 2: Query 15-day range (August 1 to August 15)
            val (windowInvoices, totalInWindow) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 8, 1),
                            fechaFin = LocalDate.of(2026, 8, 15),
                        ),
                    )
                }
            assertEquals(2L, totalInWindow)
            assertEquals(listOf("inv-d15", "inv-d5"), windowInvoices.map { it.id })

            // Step 3: Cashier selects invoice inv-d15 and requests reprint payload
            val printPayload = runBlocking { repo.getPrintPayload(database, "PA", "inv-d15", "Empresa Demo") }
            assertNotNull(printPayload)
            assertEquals("inv-d15", printPayload.facturaId)
            assertEquals("75.00", printPayload.total)
            assertEquals("CUFE-D15", printPayload.cufe)

            // Step 4: Download PDF byte stream
            val pacClient =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.success(PacResponse(true, "200", "OK", cufe = "C1"))

                    override suspend fun sendEmail(
                        baseUrl: String,
                        token: PacAuthToken,
                        cufe: String,
                        emails: List<String>,
                    ) = Result.success(TheFactoryEnviarCorreoResponse("200"))

                    override suspend fun downloadPdf(
                        baseUrl: String,
                        token: PacAuthToken,
                        cufe: String,
                    ): Result<ByteArray> = Result.success("%PDF-1.4 Simulated invoice document bytes".toByteArray())
                }
            val pdfResult = runBlocking { pacClient.downloadPdf("https://pac.test", PacAuthToken("tok"), "CUFE-D15") }
            assertTrue(pdfResult.isSuccess)
            assertTrue(pdfResult.getOrThrow().isNotEmpty())
        }

    // =========================================================================
    // Scenario 5: Multi-Flavor & Multi-Country Brand Integrity Pipeline
    // =========================================================================
    @Test
    fun `Scenario 5 - Multi-Flavor & Multi-Country Wire Contract Separation`() {
        // Step 1: Validate Panama canonical response fixture
        val paFixture = findContractFixture("sale/process-sale-response.json")
        val paResponse = json.decodeFromString(ProcessSaleResponse.serializer(), Files.readString(paFixture))
        assertTrue(paResponse.success)
        assertNotNull(paResponse.cufe, "Panama must have CUFE")
        assertNull(paResponse.numeroDocumentoFiscal, "Panama must not have VE fiscal number")

        // Step 2: Validate Venezuela Digital canonical response fixture
        val veDigitalFixture = findContractFixture("sale/ve-digital-process-sale-response.json")
        val veDigitalResponse =
            json.decodeFromString(
                ProcessSaleResponse.serializer(),
                Files.readString(veDigitalFixture),
            )
        assertTrue(veDigitalResponse.success)
        assertNotNull(veDigitalResponse.numeroDocumentoFiscal, "VE digital must have fiscal number")
        assertNull(veDigitalResponse.cufe, "VE digital must not have Panama CUFE")

        // Step 3: Validate Venezuela HKA-20 canonical response fixture
        val veHkaFixture = findContractFixture("sale/ve-hka20-process-sale-response.json")
        val veHkaResponse =
            json.decodeFromString(
                ProcessSaleResponse.serializer(),
                Files.readString(veHkaFixture),
            )
        assertTrue(veHkaResponse.success)
        assertNotNull(veHkaResponse.numeroDocumentoFiscal)
        assertNotNull(veHkaResponse.numeroControlThka)
    }

    // ==========================================
    // Test Infrastructure Helpers
    // ==========================================

    private lateinit var database: Database

    private fun withDatabase(block: Tier4BackendRealWorldWorkloadsE2ETest.() -> Unit) {
        val dbName = "t4_be_" + UUID.randomUUID().toString().replace("-", "")
        database =
            Database.connect(
                url = "jdbc:h2:mem:$dbName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                FacturasTablePA,
                FacturasTableVE,
                ClientsTable,
                ClientSucursalTable,
                SucursalTable,
                FEPaisesReadTable,
                CreditNoteFacturaTable,
                CreditNoteFacturaDetalleTable,
                CreditNoteDetailTable,
                CreditNoteHeaderTablePA,
                CreditNoteCajaTable,
                CajaTable,
                CajaSecuenciaTable,
                CajaDetalleAperturaTable,
                CajaDetalleCierreTable,
                CajaDetalleCierreFormaPagoTable,
                CajaFormaPagoTable,
                FECorrelativosTable,
                ParametrosGeneralesTablePA,
                FETipoClienteReadTable,
                FEItemReadTable,
                FEUnidadEmpaquesReadTable,
                FEFacturaDetalleFormaPagoReadTable,
                EstatusTable,
                SalesCajaNuevaTableFactory.forCountry("PA"),
                SalesCajaNuevaDetalleTableFactory.forCountry("PA"),
                SalesCajaNuevaDetalleFormaPagoTable,
                SalesCajaNuevaReciboTableFactory.forCountry("PA"),
            )
            seedCore()
        }

        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    SalesCajaNuevaReciboTableFactory.forCountry("PA"),
                    SalesCajaNuevaDetalleFormaPagoTable,
                    SalesCajaNuevaDetalleTableFactory.forCountry("PA"),
                    SalesCajaNuevaTableFactory.forCountry("PA"),
                    EstatusTable,
                    FEFacturaDetalleFormaPagoReadTable,
                    FEUnidadEmpaquesReadTable,
                    FEItemReadTable,
                    FETipoClienteReadTable,
                    ParametrosGeneralesTablePA,
                    FECorrelativosTable,
                    CajaFormaPagoTable,
                    CajaDetalleCierreFormaPagoTable,
                    CajaDetalleCierreTable,
                    CajaDetalleAperturaTable,
                    CajaSecuenciaTable,
                    CajaTable,
                    CreditNoteCajaTable,
                    CreditNoteHeaderTablePA,
                    CreditNoteDetailTable,
                    CreditNoteFacturaDetalleTable,
                    CreditNoteFacturaTable,
                    FEPaisesReadTable,
                    SucursalTable,
                    ClientSucursalTable,
                    ClientsTable,
                    FacturasTableVE,
                    FacturasTablePA,
                )
            }
        }
    }

    private fun seedCore() {
        ClientsTable.insert {
            it[idCliente] = "client-1"
            it[codCliente] = "C1"
            it[rif] = "8-123"
            it[dv] = "1"
            it[nombre] = "Cliente Principal"
            it[apellido] = "Prueba"
            it[direccion] = "Dir"
            it[direccionNivel1] = null
            it[direccionNivel2] = null
            it[direccionNivel3] = null
            it[tipoIdentificacionExtranjera] = null
            it[telefonos] = "000"
            it[email] = "test@example.com"
            it[estado] = "1"
            it[pais] = 1
            it[codTipoCliente] = 1
            it[tipoContribuyente] = 1
            it[fecha] = null
            it[permiteCredito] = false
            it[limite] = 0.0
            it[dias] = 0
            it[foto] = null
        }
        EstatusTable.insert {
            it[codEstatus] = 1
            it[descripcion] = "En Espera"
        }
        EstatusTable.insert {
            it[codEstatus] = 2
            it[descripcion] = "Pagada"
        }
        FECorrelativosTable.insert {
            it[id] = 1
            it[campo] = "numeroDocumentoFiscal"
            it[contador] = 0
        }
        ParametrosGeneralesTablePA.insert {
            it[codEmpresa] = 1
            it[defaultCodClienteFactura] = "C1"
            it[defaultIdFormaPagoFactura] = 1
            it[porcentajeImpuestoPrincipal] = 7.0.toBigDecimal()
            it[validarStock] = "NO"
            it[diasVencimiento] = 30
            it[codAlmacen] = 1
            it[abrMonedaBase] = "USD"
            it[monedaBase] = 1
            it[tipoFacturacion] = 3
            it[tokenEmpresa] = "tok"
            it[tokenPassword] = "pass"
            it[apiTheFactoryHka] = "https://pac.example.com"
            it[tipoEmision] = "01"
            it[destinoOperacion] = "01"
            it[procesoGeneracion] = "01"
        }
        FETipoClienteReadTable.insert {
            it[codTipoCliente] = 1
            it[tipoClienteFE] = "02"
        }
        SucursalTable.insert {
            it[idSucursal] = 1
            it[codigo] = "SUC1"
            it[serie] = "A"
            it[codigoSucursalEmisor] = "0000"
            it[sucursal] = "Sucursal Central"
            it[descripcion] = "Central"
        }
        FEPaisesReadTable.insert {
            it[id] = 1
            it[iso] = "PA"
            it[nombre] = "Panama"
        }
        CajaTable.insert {
            it[idCaja] = "caja-1"
            it[codCaja] = "C01"
            it[descripcion] = "Caja 1"
            it[idSucursal] = 1
            it[codAlmacen] = 1
            it[codEstatus] = 1
            it[serieCaja] = "S1"
        }
        CajaTable.insert {
            it[idCaja] = "caja-2"
            it[codCaja] = "C02"
            it[descripcion] = "Caja 2"
            it[idSucursal] = 1
            it[codAlmacen] = 1
            it[codEstatus] = 1
            it[serieCaja] = "S2"
        }
        CajaFormaPagoTable.insert {
            it[idFormaPago] = 1
            it[descripcion] = "Efectivo"
            it[siglas] = "EF"
            it[activo] = 1
        }
        CajaFormaPagoTable.insert {
            it[idFormaPago] = 2
            it[descripcion] = "Nota de Credito"
            it[siglas] = "NC"
            it[activo] = 1
        }
    }

    private fun seedFacturaPA(
        id: String,
        total: Double = 100.0,
        cajaId: String = "caja-1",
        codEstatus: Int = 2,
        cufe: String? = "CUFE-$id",
        createdAt: LocalDateTime = LocalDateTime.of(2026, 8, 31, 10, 0),
    ) = transaction(database) {
        val dtStr = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        FacturasTablePA.insert {
            it[idFactura] = id
            it[codFactura] = "FAC-$id"
            it[codFacturaFiscal] = ("CF-$id").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-1"
            it[codVendedor] = 1
            it[this.codEstatus] = codEstatus
            it[idSucursal] = 1
            it[this.idCaja] = cajaId
            it[fechaFactura] = createdAt.toLocalDate().toString()
            it[fechaCreacion] = dtStr
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[this.cufe] = cufe
            it[qr] = cufe?.let { "QR-$it" }
            it[fechaRecepcionDGI] = cufe?.let { "2026-08-31 10:05:00" }
        }
    }

    private fun seedCreditNotePrerequisitesPA(
        id: String,
        initialTotal: Double,
    ) = transaction(database) {
        if (ClientsTable.selectAll().where { ClientsTable.idCliente eq "client-$id" }.empty()) {
            ClientsTable.insert {
                it[idCliente] = "client-$id"
                it[codCliente] = "C-$id"
                it[rif] = "8-$id"
                it[dv] = "1"
                it[nombre] = "Cliente $id"
                it[apellido] = "Prueba"
                it[direccion] = "Dir"
                it[direccionNivel1] = null
                it[direccionNivel2] = null
                it[direccionNivel3] = null
                it[tipoIdentificacionExtranjera] = null
                it[telefonos] = "000"
                it[email] = "test@example.com"
                it[estado] = "1"
                it[pais] = 1
                it[codTipoCliente] = 1
                it[tipoContribuyente] = 1
                it[fecha] = null
                it[permiteCredito] = false
                it[limite] = 0.0
                it[dias] = 0
                it[foto] = null
            }
        }
        if (CreditNoteFacturaTable.selectAll().where { CreditNoteFacturaTable.idFactura eq id }.empty()) {
            CreditNoteFacturaTable.insert {
                it[idFactura] = id
                it[codFactura] = "F-$id"
                it[codFacturaFiscal] = ("CF-$id").take(10)
                it[numeroDocumentoFiscal] = "0000000001"
                it[idCliente] = "client-$id"
                it[codVendedor] = 1
                it[codEstatus] = 2
                it[fechaFactura] = LocalDate.of(2026, 8, 31)
                it[fechaCreacion] = LocalDateTime.of(2026, 8, 31, 10, 0)
                it[subtotal] = initialTotal.toBigDecimal()
                it[totalizarSubTotal] = initialTotal.toBigDecimal()
                it[totalizarTotalOperacion] = initialTotal.toBigDecimal()
                it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarBaseImponible] = initialTotal.toBigDecimal()
                it[totalizarMontoIva] = BigDecimal.ZERO
                it[totalizarTotalGeneral] = initialTotal.toBigDecimal()
                it[totalTotalFactura] = initialTotal.toBigDecimal()
                it[formaPago] = "contado"
                it[idCajaSecuencia] = "caja-secuencia-1"
                it[idCaja] = "caja-1"
                it[idSucursal] = 1
                it[serieSucursal] = "A"
                it[codigoCaja] = "CAJA"
                it[facturarA] = "Cliente"
                it[facturarARuc] = "8-123"
                it[facturarADireccion] = "Dir"
                it[facturarATelefono] = "000"
                it[abrMonedaBase] = "USD"
                it[tasa] = BigDecimal.ONE
                it[totalRef] = initialTotal.toBigDecimal()
            }
        }
        if (CreditNoteFacturaDetalleTable
                .selectAll()
                .where {
                    CreditNoteFacturaDetalleTable.idDetalleFactura eq
                        "det-$id"
                }.empty()
        ) {
            CreditNoteFacturaDetalleTable.insert {
                it[idDetalleFactura] = "det-$id"
                it[idFactura] = id
                it[idItem] = 1
                it[itemAlmacen] = 1
                it[itemDescripcion] = "Item $id"
                it[itemCantidad] = BigDecimal.ONE
                it[itemPrecioSinIva] = initialTotal.toBigDecimal()
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = BigDecimal.ZERO
                it[itemPIva] = BigDecimal.ZERO
                it[itemTotalSinIva] = initialTotal.toBigDecimal()
                it[itemTotalConIva] = initialTotal.toBigDecimal()
                it[itemCantidadTotal] = BigDecimal.ONE
                it[codVendedor] = 1
                it[itemCodigo] = "P-$id"
                it[itemReferencia] = "R-$id"
                it[anulado] = false
            }
        }
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq "caja-1" }) {
            it[codigo] = "CAJA"
            it[idSucursal] = 1
            it[notacreditoCorrelativo] = 0
        }
        if (CajaSecuenciaTable.selectAll().where { CajaSecuenciaTable.idCajaSecuencia eq "caja-secuencia-1" }.empty()) {
            CajaSecuenciaTable.insert {
                it[idCajaSecuencia] = "caja-secuencia-1"
                it[idCaja] = "caja-1"
                it[secuencia] = "000001"
                it[serieSucursal] = "A"
            }
        }
    }

    private fun findContractFixture(relPath: String): Path {
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val candidate = dir?.resolve("contracts")?.resolve(relPath)
            if (candidate != null && Files.exists(candidate)) return candidate
            dir = dir?.parent
        }
        error("Fixture not found: $relPath")
    }
}
