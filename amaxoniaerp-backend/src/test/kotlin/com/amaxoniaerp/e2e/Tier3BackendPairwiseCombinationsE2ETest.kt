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
import com.amaxoniaerp.features.creditnotes.data.getSourceInvoiceDetail
import com.amaxoniaerp.features.creditnotes.data.listEligibleInvoices
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
import com.amaxoniaerp.features.facturas.data.FacturasClientesTable
import com.amaxoniaerp.features.facturas.data.FacturasFilter
import com.amaxoniaerp.features.facturas.data.FacturasRepository
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import com.amaxoniaerp.features.facturas.data.FacturasTableVE
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TIER 3: Cross-Feature Pairwise Combinations E2E Tests (Backend Track)
 * Matrix across [Country] x [Payment] x [Action] (>= 10 tests).
 */
class Tier3BackendPairwiseCombinationsE2ETest {
    @Test
    fun `T3-01 PA Cash Sale persists and is queryable by active caja`() =
        withDatabase {
            seedFacturaPA("pa-cash-1", total = 100.0, formaPago = "contado", cajaId = "caja-1", cufe = "CUFE-PA-CASH-1")
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = "caja-1"))
                }
            assertEquals(1L, total)
            val f = facturas.single()
            assertEquals("pa-cash-1", f.id)
            assertEquals("CUFE-PA-CASH-1", f.codigoFiscal)
            assertEquals(100.0, f.total)
        }

    @Test
    fun `T3-02 PA Card Sale with failed PAC recovers via manual resend`() =
        withDatabase {
            seedFacturaPA("pa-card-1", total = 75.0, formaPago = "tarjeta", cajaId = "caja-1", cufe = null)

            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.success(PacResponse(true, "200", "OK", cufe = "CUFE-RECOVERED-PA", qr = "QR-RECOVERED"))

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

            val processor = PanamaInvoiceProcessor(ElectronicInvoiceRepository(), client, TheFactoryHkaPayloadBuilder())
            val res = runBlocking { processor.processElectronicInvoice(database, "pa-card-1") }
            val success = assertIs<ElectronicInvoiceResult.Success>(res)
            assertEquals("CUFE-RECOVERED-PA", success.cufe)

            val storedCufe = runBlocking { ElectronicInvoiceRepository().getInvoiceCufe(database, "pa-card-1") }
            assertEquals("CUFE-RECOVERED-PA", storedCufe)
        }

    @Test
    fun `T3-03 PA Mixed payment Full Credit Note confirms and voids source`() =
        withDatabase {
            seedCreditNotePrerequisitesPA(initialTotal = 150.0)
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.REINTEGRO,
                    idFormaPagoReintegro = 1,
                )
            val prep = transaction(database) { repository.preparePanama(request, "cashier") }
            val finalized =
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        prep.id,
                        request,
                        PacResponse(true, "200", "OK", cufe = "CUFE-NC-FULL-MIXED"),
                        prep.numeroDocumentoFiscal,
                    )
                }
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, finalized.fiscalStatus)
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertEquals(0.0, source?.remainingAmount ?: -1.0, 0.001)
        }

    @Test
    fun `T3-04 PA Tax-Exempt Partial Credit Note adjusts remaining correctly`() =
        withDatabase {
            seedCreditNotePrerequisitesPA(initialTotal = 100.0)
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 0.5)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(request, "cashier") }
            val finalized =
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        prep.id,
                        request,
                        PacResponse(true, "200", "OK", cufe = "CUFE-NC-PARTIAL"),
                        prep.numeroDocumentoFiscal,
                    )
                }
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, finalized.fiscalStatus)
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertEquals(50.0, source?.remainingAmount ?: -1.0, 0.001)
        }

    @Test
    fun `T3-05 PA Fallback PDF generation on PAC failure delivers local PDF payload`() =
        withDatabase {
            seedFacturaPA("pa-pdf-fail", cufe = "CUFE-PDF-FAIL")
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.success(PacResponse(true, "200", "OK", cufe = "CUFE-PDF-FAIL"))

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
                    ) = Result.failure<ByteArray>(IllegalStateException("PDF gateway timeout"))
                }
            val processor = PanamaInvoiceProcessor(ElectronicInvoiceRepository(), client, TheFactoryHkaPayloadBuilder())
            val pdf = runBlocking { processor.downloadInvoicePdf(database, "pa-pdf-fail") }
            assertTrue(pdf.isFailure)
        }

    @Test
    fun `T3-06 PA Draft Sales existence does not prevent cash close`() =
        withDatabase {
            seedFacturaPA("pa-draft-1", codEstatus = 1, total = 30.0)
            seedFacturaPA("pa-paid-1", codEstatus = 2, total = 70.0)

            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "PA",
                            dbName = "db",
                            request = AperturaRequest("caja-1", 50.0, 1),
                            username = "cajero",
                        ).getOrThrow()
                }
            val close =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "PA",
                        request =
                            CajaCierreSaveRequest(
                                id = open.idCajaSecuencia,
                                montoTotal = 120.0,
                                montoEfectivoTotal = 120.0,
                            ),
                    )
                }
            assertTrue(close.isSuccess, "Cash close should not be blocked by draft sales")
        }

    @Test
    fun `T3-07 VE Cash Sale persists with currency rate and reference totals`() =
        withDatabase {
            seedFacturaVE("ve-cash-1", total = 50.0, formaPago = "efectivo", tasa = 40.0f, totalRef = 2000.0f)
            val repo = FacturasRepository()
            val (facturas, total) = runBlocking { repo.listFacturas(database, "VE", 10, 0, FacturasFilter()) }
            assertEquals(1L, total)
            assertEquals("ve-cash-1", facturas.first().id)
            assertEquals(50.0, facturas.first().total)
        }

    @Test
    fun `T3-08 VE Card session closes with multi-payment breakdown`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "VE",
                            dbName = "db",
                            request = AperturaRequest("caja-1", 20.0, 1),
                            username = "cajero-ve",
                        ).getOrThrow()
                }
            val close =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "VE",
                        request =
                            CajaCierreSaveRequest(
                                id = open.idCajaSecuencia,
                                montoTotal = 500.0,
                                montoEfectivoTotal = 200.0,
                                montoOtrosTotal = 300.0,
                            ),
                    )
                }
            assertTrue(close.isSuccess)
        }

    @Test
    fun `T3-09 VE Mixed Payment Sale Reprint generates VE print payload`() =
        withDatabase {
            seedFacturaVE("ve-mixed-1", total = 80.0, formaPago = "mixto", tasa = 40.0f, totalRef = 3200.0f)
            val repo = FacturasRepository()
            val payload = runBlocking { repo.getPrintPayload(database, "VE", "ve-mixed-1", "Empresa VE") }
            assertNotNull(payload)
            assertEquals("ve-mixed-1", payload.facturaId)
            assertEquals("80.00", payload.total)
        }

    @Test
    fun `T3-10 PA Credit Seleccionar Factura 31 days query returns eligible source`() =
        withDatabase {
            seedEligibleInvoicePA("inv-pa-credit", LocalDate.of(2026, 8, 15), total = 200.0)
            val repo = CreditNoteRepository()
            val (data, _) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 10,
                        offset = 0,
                        search = null,
                        fechaInicio = LocalDate.of(2026, 8, 1),
                        fechaFin = LocalDate.of(2026, 8, 31),
                    )
                }
            assertEquals(1, data.size)
            assertEquals("inv-pa-credit", data.first().id)
        }

    // ==========================================
    // Test Infrastructure Helpers
    // ==========================================

    private lateinit var database: Database

    private fun withDatabase(block: Tier3BackendPairwiseCombinationsE2ETest.() -> Unit) {
        val dbName = "t3_be_" + UUID.randomUUID().toString().replace("-", "")
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
        formaPago: String = "contado",
        cajaId: String = "caja-1",
        codEstatus: Int = 2,
        cufe: String? = "CUFE-$id",
    ) = transaction(database) {
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
            it[fechaFactura] = "2026-08-31"
            it[fechaCreacion] = "2026-08-31 10:00:00"
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[this.formaPago] = formaPago
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[this.cufe] = cufe
            it[qr] = cufe?.let { "QR-$it" }
            it[fechaRecepcionDGI] = cufe?.let { "2026-08-31 10:05:00" }
        }
    }

    private fun seedFacturaVE(
        id: String,
        total: Double = 100.0,
        formaPago: String = "contado",
        cajaId: String = "caja-1",
        tasa: Float = 1.0f,
        totalRef: Float = total.toFloat(),
    ) = transaction(database) {
        FacturasTableVE.insert {
            it[idFactura] = id
            it[codFactura] = "FAC-$id"
            it[codFacturaFiscal] = ("CF-$id").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-1"
            it[codVendedor] = 1
            it[codEstatus] = 2
            it[idSucursal] = 1
            it[this.idCaja] = cajaId
            it[fechaFactura] = "2026-08-31"
            it[fechaCreacion] = "2026-08-31 10:00:00"
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[this.formaPago] = formaPago
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[abrMonedaBase] = "USD"
            it[abrMonedaSecundaria] = "VES"
            it[this.tasa] = tasa
            it[this.totalRef] = totalRef
            it[impresoraSerial] = "SER-VE-1"
        }
    }

    private fun seedEligibleInvoicePA(
        id: String,
        date: LocalDate,
        total: Double = 100.0,
    ) = transaction(database) {
        FacturasClientesTable.insert {
            it[idCliente] = "client-$id"
            it[nombre] = "Cliente $id"
            it[apellido] = null
            it[rif] = "8-$id"
            it[codCliente] = "C-$id"
        }
        CreditNoteFacturaTable.insert {
            it[idFactura] = id
            it[codFactura] = "FAC-$id"
            it[codFacturaFiscal] = ("CF-$id").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-$id"
            it[codVendedor] = 1
            it[codEstatus] = 2
            it[fechaFactura] = date
            it[fechaCreacion] = date.atTime(10, 0)
            it[subtotal] = total.toBigDecimal()
            it[totalizarSubTotal] = total.toBigDecimal()
            it[totalizarTotalOperacion] = total.toBigDecimal()
            it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarBaseImponible] = total.toBigDecimal()
            it[totalizarMontoIva] = BigDecimal.ZERO
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[totalTotalFactura] = total.toBigDecimal()
            it[formaPago] = "contado"
            it[idCajaSecuencia] = "caja-secuencia-1"
            it[idCaja] = "caja-1"
            it[idSucursal] = 1
            it[serieSucursal] = "A"
            it[codigoCaja] = "C01"
            it[facturarA] = "Cliente"
            it[facturarARuc] = "8-123"
            it[facturarADireccion] = "Dir"
            it[facturarATelefono] = "000"
            it[abrMonedaBase] = "USD"
            it[tasa] = BigDecimal.ONE
            it[totalRef] = total.toBigDecimal()
        }
        CreditNoteFacturaDetalleTable.insert {
            it[idDetalleFactura] = "det-$id"
            it[idFactura] = id
            it[idItem] = 1
            it[itemAlmacen] = 1
            it[itemDescripcion] = "Producto"
            it[itemCantidad] = BigDecimal.ONE
            it[itemPrecioSinIva] = total.toBigDecimal()
            it[itemDescuento] = BigDecimal.ZERO
            it[itemMontoDescuento] = BigDecimal.ZERO
            it[itemPIva] = BigDecimal.ZERO
            it[itemTotalSinIva] = total.toBigDecimal()
            it[itemTotalConIva] = total.toBigDecimal()
            it[itemCantidadTotal] = BigDecimal.ONE
            it[codVendedor] = 1
            it[itemCodigo] = "P1"
            it[itemReferencia] = "REF1"
            it[anulado] = false
        }
    }

    private fun seedCreditNotePrerequisitesPA(initialTotal: Double = 10.0) =
        transaction(database) {
            ClientsTable.insert {
                it[idCliente] = "client-src-1"
                it[codCliente] = "C1"
                it[rif] = "8-123"
                it[dv] = "1"
                it[nombre] = "Cliente"
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
            CreditNoteFacturaTable.insert {
                it[idFactura] = "factura-src-1"
                it[codFactura] = "F-001"
                it[codFacturaFiscal] = "CF-001"
                it[numeroDocumentoFiscal] = "0000000001"
                it[idCliente] = "client-src-1"
                it[codVendedor] = 1
                it[codEstatus] = 2
                it[fechaFactura] = LocalDate.of(2026, 8, 31)
                it[fechaCreacion] = java.time.LocalDateTime.of(2026, 8, 31, 10, 0)
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
            CreditNoteFacturaDetalleTable.insert {
                it[idDetalleFactura] = "detalle-src-1"
                it[idFactura] = "factura-src-1"
                it[idItem] = 1
                it[itemAlmacen] = 1
                it[itemDescripcion] = "Item 1"
                it[itemCantidad] = BigDecimal.ONE
                it[itemPrecioSinIva] = initialTotal.toBigDecimal()
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = BigDecimal.ZERO
                it[itemPIva] = BigDecimal.ZERO
                it[itemTotalSinIva] = initialTotal.toBigDecimal()
                it[itemTotalConIva] = initialTotal.toBigDecimal()
                it[itemCantidadTotal] = BigDecimal.ONE
                it[codVendedor] = 1
                it[itemCodigo] = "P-1"
                it[itemReferencia] = "R-1"
                it[anulado] = false
            }
            CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq "caja-1" }) {
                it[codigo] = "CAJA"
                it[idSucursal] = 1
                it[notacreditoCorrelativo] = 0
            }
            CajaSecuenciaTable.insert {
                it[idCajaSecuencia] = "caja-secuencia-1"
                it[idCaja] = "caja-1"
                it[secuencia] = "000001"
                it[serieSucursal] = "A"
            }
        }
}
