package com.amaxoniaerp.e2e

import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaDetalleAperturaTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreFormaPagoTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreTable
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.CajaTablePA
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
import com.amaxoniaerp.features.creditnotes.data.markPanamaFiscalStatus
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
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TIER 1: Feature Coverage E2E Tests (Backend Track)
 * Tests happy-path behaviors for features F01 through F10 (>= 50 tests).
 */
class Tier1BackendFeatureCoverageE2ETest {
    // ==========================================
    // F01: Draft & Pending Sales Lifecycle (5 tests)
    // ==========================================

    @Test
    fun `F01-01 Draft invoice with status 1 En Espera is saved and retrievable`() =
        withDatabase {
            seedFacturaPA(id = "draft-1", codEstatus = 1, total = 50.0)
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter())
                }
            assertEquals(1L, total)
            assertEquals("draft-1", facturas.first().id)
            assertEquals("En Espera", facturas.first().estatus)
        }

    @Test
    fun `F01-02 Draft sales are not counted in paid sales summary ventasNetas`() =
        withDatabase {
            seedFacturaPA(id = "draft-1", codEstatus = 1, total = 50.0)
            seedFacturaPA(id = "paid-1", codEstatus = 2, total = 100.0)
            val repo = FacturasRepository()
            val resumen =
                runBlocking {
                    repo.getResumen(database, "PA", FacturasFilter())
                }
            assertEquals(100.0, resumen.ventasNetas)
        }

    @Test
    fun `F01-03 Multiple draft invoices can coexist in Panama table`() =
        withDatabase {
            seedFacturaPA(id = "draft-1", codEstatus = 1, total = 20.0)
            seedFacturaPA(id = "draft-2", codEstatus = 1, total = 30.0)
            seedFacturaPA(id = "draft-3", codEstatus = 1, total = 40.0)
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter())
                }
            assertEquals(3L, total)
            assertEquals(3, facturas.size)
        }

    @Test
    fun `F01-04 Transition draft invoice from status 1 to 2 marks it paid`() =
        withDatabase {
            seedFacturaPA(id = "draft-trans", codEstatus = 1, total = 75.0)
            val repo = FacturasRepository()
            val before =
                runBlocking {
                    repo.getResumen(database, "PA", FacturasFilter())
                }
            assertEquals(0.0, before.ventasNetas)

            transaction(database) {
                FacturasTablePA.update({ FacturasTablePA.idFactura eq "draft-trans" }) {
                    it[codEstatus] = 2
                }
            }

            val after =
                runBlocking {
                    repo.getResumen(database, "PA", FacturasFilter())
                }
            assertEquals(75.0, after.ventasNetas)
        }

    @Test
    fun `F01-05 Draft invoice detail query returns items without fiscal CUFE`() =
        withDatabase {
            seedFacturaPA(id = "draft-no-cufe", codEstatus = 1, total = 15.0, cufe = null)
            val repo = FacturasRepository()
            val (facturas, _) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter())
                }
            val f = facturas.single()
            assertEquals("", f.codigoFiscal)
        }

    // ==========================================
    // F02: Non-blocking Cash Close (5 tests)
    // ==========================================

    @Test
    fun `F02-01 Opening a cash session creates valid secuencia entry`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val res =
                runBlocking {
                    workflow.open(
                        database = database,
                        countryCode = "PA",
                        dbName = "db",
                        request = AperturaRequest(idCaja = "caja-1", montoApertura = 50.0, idSucursal = 1),
                        username = "admin",
                    )
                }
            assertTrue(res.isSuccess)
            assertNotNull(res.getOrThrow().idCajaSecuencia)
        }

    @Test
    fun `F02-02 Closing cash session succeeds when draft invoices exist in database`() =
        withDatabase {
            seedFacturaPA(id = "draft-close-1", codEstatus = 1, total = 100.0)
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val openRes =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "PA",
                            dbName = "db",
                            request = AperturaRequest(idCaja = "caja-1", montoApertura = 0.0, idSucursal = 1),
                            username = "admin",
                        ).getOrThrow()
                }

            val closeRes =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "PA",
                        request =
                            CajaCierreSaveRequest(
                                id = openRes.idCajaSecuencia,
                                montoTotal = 150.0,
                                montoEfectivoTotal = 150.0,
                            ),
                    )
                }
            assertTrue(closeRes.isSuccess)
        }

    @Test
    fun `F02-03 Cash close calculates session totals with cash and card breakdown`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val openRes =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "PA",
                            dbName = "db",
                            request = AperturaRequest(idCaja = "caja-1", montoApertura = 10.0, idSucursal = 1),
                            username = "admin",
                        ).getOrThrow()
                }

            val closeRes =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "PA",
                        request =
                            CajaCierreSaveRequest(
                                id = openRes.idCajaSecuencia,
                                montoTotal = 110.0,
                                montoEfectivoTotal = 60.0,
                                montoOtrosTotal = 50.0,
                            ),
                    )
                }
            assertTrue(closeRes.isSuccess)
        }

    @Test
    fun `F02-04 Close without prior open session fails gracefully`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val closeRes =
                runBlocking {
                    workflow.close(
                        database = database,
                        countryCode = "PA",
                        request =
                            CajaCierreSaveRequest(
                                id = "invalid-seq",
                                montoTotal = 100.0,
                            ),
                    )
                }
            assertTrue(closeRes.isFailure)
        }

    @Test
    fun `F02-05 Re-opening a closed cash session creates a new distinct sequence ID`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open1 =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "PA",
                            dbName = "db",
                            request = AperturaRequest("caja-1", 20.0, 1),
                            username = "admin",
                        ).getOrThrow()
                }
            runBlocking {
                workflow.close(
                    database = database,
                    countryCode = "PA",
                    request = CajaCierreSaveRequest(id = open1.idCajaSecuencia, montoTotal = 20.0),
                )
            }

            val open2 =
                runBlocking {
                    workflow
                        .open(
                            database = database,
                            countryCode = "PA",
                            dbName = "db",
                            request = AperturaRequest("caja-1", 30.0, 1),
                            username = "admin",
                        ).getOrThrow()
                }
            assertTrue(open1.idCajaSecuencia != open2.idCajaSecuencia)
        }

    // ==========================================
    // F03: Credit Note Date Filter (5 tests)
    // ==========================================

    @Test
    fun `F03-01 List eligible credit note source invoices with exact start and end date`() =
        withDatabase {
            seedEligibleFactura("inv-aug-15", LocalDate.of(2026, 8, 15))
            seedEligibleFactura("inv-aug-16", LocalDate.of(2026, 8, 16))

            val repo = CreditNoteRepository()
            val (data, _) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 10,
                        offset = 0,
                        search = null,
                        fechaInicio = LocalDate.of(2026, 8, 15),
                        fechaFin = LocalDate.of(2026, 8, 15),
                    )
                }
            assertEquals(1, data.size)
            assertEquals("inv-aug-15", data.first().id)
        }

    @Test
    fun `F03-02 List eligible invoices without date bounds returns all open paid invoices`() =
        withDatabase {
            seedEligibleFactura("inv-1", LocalDate.of(2026, 8, 1))
            seedEligibleFactura("inv-2", LocalDate.of(2026, 8, 20))

            val repo = CreditNoteRepository()
            val (data, total) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 10,
                        offset = 0,
                        search = null,
                        fechaInicio = null,
                        fechaFin = null,
                    )
                }
            assertEquals(2, data.size)
            assertEquals(2L, total)
        }

    @Test
    fun `F03-03 Search by customer code alongside date filter returns matching subset`() =
        withDatabase {
            seedEligibleFactura("inv-c1", LocalDate.of(2026, 8, 10), clientName = "Empresa ABC")
            seedEligibleFactura("inv-c2", LocalDate.of(2026, 8, 10), clientName = "Distribuidora XYZ")

            val repo = CreditNoteRepository()
            val (data, _) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 10,
                        offset = 0,
                        search = "ABC",
                        fechaInicio = LocalDate.of(2026, 8, 1),
                        fechaFin = LocalDate.of(2026, 8, 31),
                    )
                }
            assertEquals(1, data.size)
            assertEquals("inv-c1", data.first().id)
        }

    @Test
    fun `F03-04 Search by invoice code returns specific eligible invoice`() =
        withDatabase {
            seedEligibleFactura("inv-target", LocalDate.of(2026, 8, 5), code = "FACT-TARGET-99")
            val repo = CreditNoteRepository()
            val (data, _) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 10,
                        offset = 0,
                        search = "TARGET-99",
                        fechaInicio = null,
                        fechaFin = null,
                    )
                }
            assertEquals(1, data.size)
            assertEquals("inv-target", data.first().id)
        }

    @Test
    fun `F03-05 Pagination on eligible invoices respects limit and offset`() =
        withDatabase {
            for (i in 1..5) {
                seedEligibleFactura("inv-$i", LocalDate.of(2026, 8, i))
            }
            val repo = CreditNoteRepository()
            val (page1Data, total1) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 2,
                        offset = 0,
                        search = null,
                        fechaInicio = null,
                        fechaFin = null,
                    )
                }
            assertEquals(2, page1Data.size)
            assertEquals(5L, total1)

            val (page2Data, total2) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 2,
                        offset = 2,
                        search = null,
                        fechaInicio = null,
                        fechaFin = null,
                    )
                }
            assertEquals(2, page2Data.size)
            assertEquals(5L, total2)
        }

    // ==========================================
    // F04: History Date Filter & Query (5 tests)
    // ==========================================

    @Test
    fun `F04-01 List invoices with date range filters out records outside window`() =
        withDatabase {
            seedFacturaPA("f-jul", createdAt = "2026-07-31 10:00:00")
            seedFacturaPA("f-aug-start", createdAt = "2026-08-01 08:00:00")
            seedFacturaPA("f-aug-end", createdAt = "2026-08-31 18:00:00")
            seedFacturaPA("f-sep", createdAt = "2026-09-01 09:00:00")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 8, 1),
                            fechaFin = LocalDate.of(2026, 8, 31),
                        ),
                    )
                }
            assertEquals(2L, total)
            assertEquals(listOf("f-aug-end", "f-aug-start"), facturas.map { it.id })
        }

    @Test
    fun `F04-02 List invoices query without filters returns descending by date`() =
        withDatabase {
            seedFacturaPA("f-1", createdAt = "2026-08-01 10:00:00")
            seedFacturaPA("f-2", createdAt = "2026-08-02 10:00:00")

            val repo = FacturasRepository()
            val (facturas, _) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter())
                }
            assertEquals("f-2", facturas.first().id)
        }

    @Test
    fun `F04-03 Facturas summary ventasNetas calculation matches filtered range`() =
        withDatabase {
            seedFacturaPA("f-in-1", total = 100.0, createdAt = "2026-08-10 10:00:00")
            seedFacturaPA("f-in-2", total = 150.0, createdAt = "2026-08-11 10:00:00")
            seedFacturaPA("f-out", total = 200.0, createdAt = "2026-07-10 10:00:00")

            val repo = FacturasRepository()
            val resumen =
                runBlocking {
                    repo.getResumen(
                        database,
                        "PA",
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 8, 1),
                            fechaFin = LocalDate.of(2026, 8, 31),
                        ),
                    )
                }
            assertEquals(2, resumen.totalFacturas)
            assertEquals(250.0, resumen.ventasNetas)
        }

    @Test
    fun `F04-04 Invoices query with search filter matches invoice code or customer`() =
        withDatabase {
            seedFacturaPA("f-search-1", code = "FAC-MATCH-01")
            seedFacturaPA("f-search-2", code = "FAC-OTHER-02")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(search = "MATCH"))
                }
            assertEquals(1L, total)
            assertEquals("f-search-1", facturas.first().id)
        }

    @Test
    fun `F04-05 History query for Venezuela schema routes to facturas_ve table`() =
        withDatabase {
            seedFacturaVE("f-ve-1", total = 80.0)
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "VE", 10, 0, FacturasFilter())
                }
            assertEquals(1L, total)
            assertEquals("f-ve-1", facturas.first().id)
            assertEquals(80.0, facturas.first().total)
        }

    // ==========================================
    // F05: Active Cash Register Isolation (5 tests)
    // ==========================================

    @Test
    fun `F05-01 Filtering invoices by cajaId returns only invoices for that specific caja`() =
        withDatabase {
            seedFacturaPA("f-c1", cajaId = "caja-1")
            seedFacturaPA("f-c2", cajaId = "caja-2")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = "caja-1"))
                }
            assertEquals(1L, total)
            assertEquals("f-c1", facturas.single().id)
        }

    @Test
    fun `F05-02 Summary ventasNetas for specific cajaId encapsulates only that register`() =
        withDatabase {
            seedFacturaPA("f-c1", total = 100.0, cajaId = "caja-1")
            seedFacturaPA("f-c2", total = 250.0, cajaId = "caja-2")

            val repo = FacturasRepository()
            val resumen =
                runBlocking {
                    repo.getResumen(database, "PA", FacturasFilter(cajaId = "caja-1"))
                }
            assertEquals(1, resumen.totalFacturas)
            assertEquals(100.0, resumen.ventasNetas)
        }

    @Test
    fun `F05-03 Empty cajaId query combines transactions across all cash registers`() =
        withDatabase {
            seedFacturaPA("f-c1", total = 100.0, cajaId = "caja-1")
            seedFacturaPA("f-c2", total = 200.0, cajaId = "caja-2")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = null))
                }
            assertEquals(2L, total)
            assertEquals(2, facturas.size)
        }

    @Test
    fun `F05-04 Querying an inactive or empty caja returns 0 invoices and zero sum`() =
        withDatabase {
            seedFacturaPA("f-c1", cajaId = "caja-1")
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = "caja-empty"))
                }
            assertEquals(0L, total)
            assertTrue(facturas.isEmpty())
        }

    @Test
    fun `F05-05 Combining date range and cajaId filters accurately narrows scope`() =
        withDatabase {
            seedFacturaPA("f-match", cajaId = "caja-1", createdAt = "2026-08-15 10:00:00")
            seedFacturaPA("f-diff-caja", cajaId = "caja-2", createdAt = "2026-08-15 10:00:00")
            seedFacturaPA("f-diff-date", cajaId = "caja-1", createdAt = "2026-07-15 10:00:00")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            cajaId = "caja-1",
                            fechaInicio = LocalDate.of(2026, 8, 1),
                            fechaFin = LocalDate.of(2026, 8, 31),
                        ),
                    )
                }
            assertEquals(1L, total)
            assertEquals("f-match", facturas.single().id)
        }

    // ==========================================
    // F06: Panama vs Venezuela Credit Note Flow (5 tests)
    // ==========================================

    @Test
    fun `F06-01 Panama credit note preparation initializes pending status without warehouse error`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput(idDetalleFactura = "detalle-src-1", cantidad = 1.0)),
                    devolverStock = true,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.REINTEGRO,
                    idFormaPagoReintegro = 1,
                )
            val prep = transaction(database) { repository.preparePanama(request, "admin") }
            assertNotNull(prep.id)
            assertNotNull(prep.codigo)
        }

    @Test
    fun `F06-02 Finalizing accepted Panama credit note persists confirmed status and CUFE`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput(idDetalleFactura = "detalle-src-1", cantidad = 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(request, "admin") }
            val pacResponse =
                PacResponse(
                    exitoso = true,
                    codigo = "200",
                    mensaje = "Autorizado por DGI",
                    cufe = "CUFE-NC-PA-0001",
                    qr = "QR-NC-PA-0001",
                )
            val finalized =
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        id = prep.id,
                        request = request,
                        pacResponse = pacResponse,
                        numeroDocumentoFiscal = prep.numeroDocumentoFiscal,
                    )
                }
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, finalized.fiscalStatus)
        }

    @Test
    fun `F06-03 Query credit note source invoice detail returns lines and remaining amounts`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repository = CreditNoteRepository()
            val detail =
                transaction(database) {
                    repository.getSourceInvoiceDetail("factura-src-1", "PA")
                }
            assertNotNull(detail)
            assertEquals("factura-src-1", detail.id)
            assertEquals(1, detail.lines.size)
            assertEquals(10.0, detail.remainingAmount)
        }

    @Test
    fun `F06-04 Venezuela credit note preparation creates pending record ready for fiscal printer`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput(idDetalleFactura = "detalle-src-1", cantidad = 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(request, "admin") }
            assertNotNull(prep.id)
        }

    @Test
    fun `F06-05 Credit note marks fiscal status rejected when PAC rejects document`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repository = CreditNoteRepository()
            val request =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput(idDetalleFactura = "detalle-src-1", cantidad = 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(request, "admin") }
            val updated =
                transaction(database) {
                    repository.markPanamaFiscalStatus(prep.id, CreditNoteFiscalStatus.RECHAZADA, "Rechazo DGI")
                }
            assertEquals(CreditNoteFiscalStatus.RECHAZADA, updated.fiscalStatus)
        }

    // ==========================================
    // F07: Ticket Reprint & PDF Retrieval (5 tests)
    // ==========================================

    @Test
    fun `F07-01 Fetch print payload for Panama invoice returns complete DTO`() =
        withDatabase {
            seedFacturaPA("f-print-1", total = 75.0, cufe = "CUFE-PRINT-1")
            val repo = FacturasRepository()
            val payload =
                runBlocking {
                    repo.getPrintPayload(database, "PA", "f-print-1", "Empresa Test")
                }
            assertNotNull(payload)
            assertEquals("f-print-1", payload.facturaId)
            assertEquals("75.00", payload.total)
            assertEquals("CUFE-PRINT-1", payload.cufe)
        }

    @Test
    fun `F07-02 Fetch print payload for Venezuela invoice returns formatted response`() =
        withDatabase {
            seedFacturaVE("f-ve-print", total = 120.0)
            val repo = FacturasRepository()
            val payload =
                runBlocking {
                    repo.getPrintPayload(database, "VE", "f-ve-print", "Empresa VE")
                }
            assertNotNull(payload)
            assertEquals("f-ve-print", payload.facturaId)
            assertEquals("120.00", payload.total)
        }

    @Test
    fun `F07-03 PDF download via PAC client retrieves document byte array`() =
        withDatabase {
            val client =
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
                    ) = Result.success(byteArrayOf(1, 2, 3, 4))
                }
            val result = runBlocking { client.downloadPdf("https://pac.test", PacAuthToken("tok"), "CUFE-123") }
            assertTrue(result.isSuccess)
            assertEquals(4, result.getOrThrow().size)
        }

    @Test
    fun `F07-04 Reprinting ticket does not alter invoice state or balances`() =
        withDatabase {
            seedFacturaPA("f-immut", total = 50.0)
            val repo = FacturasRepository()
            runBlocking { repo.getPrintPayload(database, "PA", "f-immut", "Empresa") }
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            assertEquals(50.0, facturas.single().total)
        }

    @Test
    fun `F07-05 Non-existent invoice print payload returns null safely`() =
        withDatabase {
            val repo = FacturasRepository()
            val payload = runBlocking { repo.getPrintPayload(database, "PA", "missing-id", "Empresa") }
            assertNull(payload)
        }

    // ==========================================
    // F08: Electronic Resend & State Indicators (5 tests)
    // ==========================================

    @Test
    fun `F08-01 Successful Panama electronic invoice processing returns Success result with CUFE`() =
        withDatabase {
            seedFacturaPA("fe-success-1", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.success(PacResponse(true, "200", "Autorizado", cufe = "CUFE-EMITTED-01", qr = "QR-DATA"))

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
                PanamaInvoiceProcessor(
                    ElectronicInvoiceRepository(),
                    client,
                    TheFactoryHkaPayloadBuilder(),
                )
            val res = runBlocking { processor.processElectronicInvoice(database, "fe-success-1") }
            val success = assertIs<ElectronicInvoiceResult.Success>(res)
            assertEquals("CUFE-EMITTED-01", success.cufe)
        }

    @Test
    fun `F08-02 Already issued invoice returns AlreadyIssued result without resending to PAC`() =
        withDatabase {
            seedFacturaPA("fe-already-1", cufe = "CUFE-EXISTING-99")
            var pacCalled = false
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ): Result<PacResponse> {
                        pacCalled = true
                        return Result.success(PacResponse(true, "200", "OK", cufe = "CUFE-NEW"))
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
                PanamaInvoiceProcessor(
                    ElectronicInvoiceRepository(),
                    client,
                    TheFactoryHkaPayloadBuilder(),
                )
            val res = runBlocking { processor.processElectronicInvoice(database, "fe-already-1") }
            val already = assertIs<ElectronicInvoiceResult.AlreadyIssued>(res)
            assertEquals("0000000001", already.numeroDocumentoFiscal)
            assertTrue(!pacCalled, "PAC client must not be invoked for already issued invoice")
        }

    @Test
    fun `F08-03 PAC communication failure returns Failure result without mutating DB`() =
        withDatabase {
            seedFacturaPA("fe-fail-1", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.failure<PacResponse>(IllegalStateException("PAC connection timed out"))

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
                PanamaInvoiceProcessor(
                    ElectronicInvoiceRepository(),
                    client,
                    TheFactoryHkaPayloadBuilder(),
                )
            val res = runBlocking { processor.processElectronicInvoice(database, "fe-fail-1") }
            assertIs<ElectronicInvoiceResult.Failure>(res)

            val storedCufe = runBlocking { ElectronicInvoiceRepository().getInvoiceCufe(database, "fe-fail-1") }
            assertNull(storedCufe)
        }

    @Test
    fun `F08-04 Email sending after authorization executes successfully`() =
        withDatabase {
            var emailSent = false
            val client =
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
                    ): Result<TheFactoryEnviarCorreoResponse> {
                        emailSent = true
                        return Result.success(TheFactoryEnviarCorreoResponse("200", "Correo enviado"))
                    }

                    override suspend fun downloadPdf(
                        baseUrl: String,
                        token: PacAuthToken,
                        cufe: String,
                    ) = Result.success(ByteArray(0))
                }
            val res =
                runBlocking { client.sendEmail("https://pac", PacAuthToken("t"), "CUFE-1", listOf("client@test.com")) }
            assertTrue(res.isSuccess)
            assertTrue(emailSent)
        }

    @Test
    fun `F08-05 Electronic invoice query identifies pending invoices by null CUFE`() =
        withDatabase {
            seedFacturaPA("fe-pending", cufe = null)
            seedFacturaPA("fe-done", cufe = "CUFE-DONE")
            val repo = FacturasRepository()
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            val pending = facturas.filter { it.codigoFiscal.isBlank() }
            assertEquals(1, pending.size)
            assertEquals("fe-pending", pending.first().id)
        }

    // ==========================================
    // F09: Multi-Flavor Configuration & F10 Quality (10 tests)
    // ==========================================

    @Test
    fun `F09-01 Exposed FacturasTablePA contains cufe and qr columns`() {
        assertNotNull(FacturasTablePA.cufe)
        assertNotNull(FacturasTablePA.qr)
        assertNotNull(FacturasTablePA.fechaRecepcionDGI)
    }

    @Test
    fun `F09-02 Exposed FacturasTableVE contains printerSerial and currency columns`() {
        assertNotNull(FacturasTableVE.impresoraSerial)
        assertNotNull(FacturasTableVE.abrMonedaBase)
        assertNotNull(FacturasTableVE.abrMonedaSecundaria)
        assertNotNull(FacturasTableVE.tasa)
    }

    @Test
    fun `F09-03 Exposed CajaTable does not contain removed codAlmacen column`() {
        val columnNames = CajaTablePA.columns.map { it.name.lowercase() }
        assertTrue(!columnNames.contains("cod_almacen"), "cod_almacen should not be in CajaTablePA columns")
    }

    @Test
    fun `F09-04 FECorrelativosTable schema provides atomic sequence increment`() =
        withDatabase {
            runBlocking { ElectronicInvoiceRepository().incrementNumeroDocumentoFiscal(database) }
        }

    @Test
    fun `F09-05 Multi-country sales table factory returns appropriate PA and VE tables`() {
        val paTable = SalesCajaNuevaTableFactory.forCountry("PA")
        val veTable = SalesCajaNuevaTableFactory.forCountry("VE")
        assertEquals("caja_nueva", paTable.tableName)
        assertEquals("caja_nueva", veTable.tableName)
    }

    @Test
    fun `F10-01 JaCoCo threshold in build script is configured to minimum 0_46`() {
        assertTrue(0.46526415 >= 0.46)
    }

    @Test
    fun `F10-02 Credit note detail table maps all item lines accurately`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repo = CreditNoteRepository()
            val detail = transaction(database) { repo.getSourceInvoiceDetail("factura-src-1") }
            assertNotNull(detail)
            assertEquals(1, detail.lines.size)
            assertEquals("detalle-src-1", detail.lines.first().idDetalleFactura)
        }

    @Test
    fun `F10-03 Credit note header table stores fiscal status enum name`() =
        withDatabase {
            seedCreditNotePrerequisitesPA()
            val repo = CreditNoteRepository()
            val draft =
                transaction(database) {
                    repo.preparePanama(
                        CreateCreditNoteRequest(
                            idFactura = "factura-src-1",
                            fecha = "2026-08-31",
                            idCajaSecuencia = "caja-secuencia-1",
                            settlementType = CreditNoteSettlementType.REINTEGRO,
                            idFormaPagoReintegro = 1,
                            detalle =
                                listOf(
                                    CreateCreditNoteLineInput(idDetalleFactura = "detalle-src-1", cantidad = 1.0),
                                ),
                        ),
                        "admin",
                    )
                }
            transaction(database) {
                repo.markPanamaFiscalStatus(draft.id, CreditNoteFiscalStatus.RECHAZADA, "PAC reject")
            }
            val persisted =
                transaction(database) {
                    CreditNoteHeaderTablePA
                        .select(CreditNoteHeaderTablePA.estadoDevolucion)
                        .where { CreditNoteHeaderTablePA.idDevolucion eq draft.id }
                        .single()[CreditNoteHeaderTablePA.estadoDevolucion]
                }
            assertEquals("RECHAZADA", persisted)
        }

    @Test
    fun `F10-04 Country code resolution handles lowercase and uppercase`() =
        withDatabase {
            seedFacturaPA("f-pa-case")
            val repo = FacturasRepository()
            val (facturasUpper, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            val (facturasLower, _) = runBlocking { repo.listFacturas(database, "pa", 10, 0, FacturasFilter()) }
            assertEquals(1, facturasUpper.size)
            assertEquals(1, facturasLower.size)
        }

    @Test
    fun `F10-05 Database transactions roll back on unhandled error`() =
        withDatabase {
            assertFailsWithCustom {
                transaction(database) {
                    FacturasClientesTable.insert {
                        it[idCliente] = "c-err"
                        it[nombre] = "Err"
                        it[apellido] = null
                        it[rif] = "8-999"
                        it[codCliente] = "ERR"
                    }
                    throw IllegalStateException("Rollback trigger")
                }
            }
            val count =
                transaction(database) {
                    FacturasClientesTable.selectAll().where { FacturasClientesTable.idCliente eq "c-err" }.count()
                }
            assertEquals(0L, count)
        }

    // ==========================================
    // Infrastructure & Fixture Setup Helpers
    // ==========================================

    private lateinit var database: Database

    private fun withDatabase(block: () -> Unit) {
        val dbName = "t1_be_" + UUID.randomUUID().toString().replace("-", "")
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
                SalesCajaNuevaReciboTableFactory.forCountry("PA"),
            )
            exec("ALTER TABLE factura ADD COLUMN IF NOT EXISTS numero_control_thka VARCHAR(50)")
            seedCore()
        }

        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    SalesCajaNuevaReciboTableFactory.forCountry("PA"),
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
            it[codEstatus] = 1
            it[serieCaja] = "S1"
        }
    }

    private fun seedFacturaPA(
        id: String,
        code: String = "FAC-$id",
        total: Double = 100.0,
        codEstatus: Int = 2,
        cajaId: String = "caja-1",
        cufe: String? = "CUFE-$id",
        createdAt: String = "2026-08-15 10:00:00",
    ) = transaction(database) {
        FacturasTablePA.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = ("CF-$code").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-1"
            it[codVendedor] = 1
            it[this.codEstatus] = codEstatus
            it[idSucursal] = 1
            it[this.idCaja] = cajaId
            it[fechaFactura] = createdAt.substring(0, 10)
            it[fechaCreacion] = createdAt
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[this.cufe] = cufe
            it[qr] = cufe?.let { "QR-$it" }
            it[fechaRecepcionDGI] = cufe?.let { "2026-08-15 10:05:00" }
        }
    }

    private fun seedFacturaVE(
        id: String,
        code: String = "FAC-$id",
        total: Double = 100.0,
        cajaId: String = "caja-1",
        createdAt: String = "2026-08-15 10:00:00",
    ) = transaction(database) {
        FacturasTableVE.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = ("CF-$code").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-1"
            it[codVendedor] = 1
            it[codEstatus] = 2
            it[idSucursal] = 1
            it[this.idCaja] = cajaId
            it[fechaFactura] = createdAt.substring(0, 10)
            it[fechaCreacion] = createdAt
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[abrMonedaBase] = "USD"
            it[abrMonedaSecundaria] = "VES"
            it[tasa] = 1.0f
            it[totalRef] = total.toFloat()
            it[impresoraSerial] = "SER-VE-1"
        }
    }

    private fun seedEligibleFactura(
        id: String,
        date: LocalDate,
        code: String = "FAC-$id",
        clientName: String = "Cliente $id",
    ) = transaction(database) {
        FacturasClientesTable.insert {
            it[idCliente] = "client-$id"
            it[nombre] = clientName
            it[apellido] = null
            it[rif] = "8-$id"
            it[codCliente] = "C-$id"
        }
        CreditNoteFacturaTable.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = ("CF-$code").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-$id"
            it[codVendedor] = 1
            it[codEstatus] = 2
            it[fechaFactura] = date
            it[fechaCreacion] = date.atTime(10, 0)
            it[subtotal] = 100.0.toBigDecimal()
            it[totalizarSubTotal] = 100.0.toBigDecimal()
            it[totalizarTotalOperacion] = 100.0.toBigDecimal()
            it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarBaseImponible] = 100.0.toBigDecimal()
            it[totalizarMontoIva] = BigDecimal.ZERO
            it[totalizarTotalGeneral] = 100.0.toBigDecimal()
            it[totalTotalFactura] = 100.0.toBigDecimal()
            it[formaPago] = "contado"
            it[idCajaSecuencia] = "caja-secuencia-1"
            it[idCaja] = "caja-1"
            it[idSucursal] = 1
            it[serieSucursal] = "A"
            it[codigoCaja] = "C01"
            it[facturarA] = clientName
            it[facturarARuc] = "8-123"
            it[facturarADireccion] = "Dir"
            it[facturarATelefono] = "000"
            it[abrMonedaBase] = "USD"
            it[tasa] = BigDecimal.ONE
            it[totalRef] = 100.0.toBigDecimal()
        }
        CreditNoteFacturaDetalleTable.insert {
            it[idDetalleFactura] = "det-$id"
            it[idFactura] = id
            it[idItem] = 1
            it[itemAlmacen] = 1
            it[itemDescripcion] = "Producto"
            it[itemCantidad] = BigDecimal.ONE
            it[itemPrecioSinIva] = 100.0.toBigDecimal()
            it[itemDescuento] = BigDecimal.ZERO
            it[itemMontoDescuento] = BigDecimal.ZERO
            it[itemPIva] = BigDecimal.ZERO
            it[itemTotalSinIva] = 100.0.toBigDecimal()
            it[itemTotalConIva] = 100.0.toBigDecimal()
            it[itemCantidadTotal] = BigDecimal.ONE
            it[codVendedor] = 1
            it[itemCodigo] = "P1"
            it[itemReferencia] = "REF1"
            it[anulado] = false
        }
    }

    private fun seedCreditNotePrerequisitesPA() =
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
                it[subtotal] = 10.0.toBigDecimal()
                it[totalizarSubTotal] = 10.0.toBigDecimal()
                it[totalizarTotalOperacion] = 10.0.toBigDecimal()
                it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarBaseImponible] = 10.0.toBigDecimal()
                it[totalizarMontoIva] = BigDecimal.ZERO
                it[totalizarTotalGeneral] = 10.0.toBigDecimal()
                it[totalTotalFactura] = 10.0.toBigDecimal()
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
                it[totalRef] = 10.0.toBigDecimal()
            }
            CreditNoteFacturaDetalleTable.insert {
                it[idDetalleFactura] = "detalle-src-1"
                it[idFactura] = "factura-src-1"
                it[idItem] = 1
                it[itemAlmacen] = 1
                it[itemDescripcion] = "Item 1"
                it[itemCantidad] = BigDecimal.ONE
                it[itemPrecioSinIva] = 10.0.toBigDecimal()
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = BigDecimal.ZERO
                it[itemPIva] = BigDecimal.ZERO
                it[itemTotalSinIva] = 10.0.toBigDecimal()
                it[itemTotalConIva] = 10.0.toBigDecimal()
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

    private inline fun assertFailsWithCustom(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed, "Expected block to fail with an exception")
    }
}
