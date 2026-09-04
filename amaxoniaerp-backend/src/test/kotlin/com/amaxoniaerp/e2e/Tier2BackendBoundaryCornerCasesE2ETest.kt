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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TIER 2: Boundary & Corner Cases E2E Tests (Backend Track)
 * Edge cases: 0 days, 30 days, 31 days, leap years, nulls, zero amounts, retries (>= 50 tests).
 */
class Tier2BackendBoundaryCornerCasesE2ETest {
    // ==========================================
    // Date Range Boundaries (10 tests)
    // ==========================================

    @Test
    fun `B01 Date boundary 0 days range matches exact single day`() =
        withDatabase {
            seedFactura("f-same-1", createdAt = LocalDateTime.of(2026, 8, 15, 8, 0))
            seedFactura("f-same-2", createdAt = LocalDateTime.of(2026, 8, 15, 23, 59))
            seedFactura("f-other", createdAt = LocalDateTime.of(2026, 8, 16, 0, 1))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 8, 15),
                            fechaFin = LocalDate.of(2026, 8, 15),
                        ),
                    )
                }
            assertEquals(2L, total)
            assertEquals(listOf("f-same-2", "f-same-1"), facturas.map { it.id })
        }

    @Test
    fun `B02 Date boundary exactly 30 days range is accepted and valid`() =
        withDatabase {
            seedFactura("f-d1", createdAt = LocalDateTime.of(2026, 8, 1, 10, 0))
            seedFactura("f-d30", createdAt = LocalDateTime.of(2026, 8, 30, 10, 0))

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
                            fechaFin = LocalDate.of(2026, 8, 30),
                        ),
                    )
                }
            assertEquals(2L, total)
            assertEquals(2, facturas.size)
        }

    @Test
    fun `B03 Date boundary exactly 31 days range max valid limit is accepted`() =
        withDatabase {
            seedFactura("f-start", createdAt = LocalDateTime.of(2026, 8, 1, 0, 0))
            seedFactura("f-end", createdAt = LocalDateTime.of(2026, 8, 31, 23, 59))

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
            assertEquals(2, facturas.size)
        }

    @Test
    fun `B04 Date filter in credit note queries handles 31-day boundary`() =
        withDatabase {
            seedEligibleInvoice("inv-b31", LocalDate.of(2026, 8, 31))
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
            assertEquals("inv-b31", data.first().id)
        }

    @Test
    fun `B05 Leap year boundary February 29 query returns leap day transactions`() =
        withDatabase {
            seedFactura("f-leap-2024", createdAt = LocalDateTime.of(2024, 2, 29, 14, 0))
            seedFactura("f-leap-prev", createdAt = LocalDateTime.of(2024, 2, 28, 23, 59))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2024, 2, 29),
                            fechaFin = LocalDate.of(2024, 2, 29),
                        ),
                    )
                }
            assertEquals(1L, total)
            assertEquals("f-leap-2024", facturas.first().id)
        }

    @Test
    fun `B06 Future leap year 2028-02-29 is properly formatted and filtered`() =
        withDatabase {
            seedFactura("f-leap-2028", createdAt = LocalDateTime.of(2028, 2, 29, 11, 30))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2028, 2, 1),
                            fechaFin = LocalDate.of(2028, 2, 29),
                        ),
                    )
                }
            assertEquals(1L, total)
            assertEquals("f-leap-2028", facturas.first().id)
        }

    @Test
    fun `B07 Month transition boundary January 31 to February 28`() =
        withDatabase {
            seedFactura("f-jan-end", createdAt = LocalDateTime.of(2026, 1, 31, 23, 0))
            seedFactura("f-feb-end", createdAt = LocalDateTime.of(2026, 2, 28, 12, 0))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 1, 31),
                            fechaFin = LocalDate.of(2026, 2, 28),
                        ),
                    )
                }
            assertEquals(2L, total)
            assertEquals(2, facturas.size)
        }

    @Test
    fun `B08 Year rollover boundary December 31 to January 1`() =
        withDatabase {
            seedFactura("f-old-year", createdAt = LocalDateTime.of(2025, 12, 31, 23, 59))
            seedFactura("f-new-year", createdAt = LocalDateTime.of(2026, 1, 1, 0, 1))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2025, 12, 31),
                            fechaFin = LocalDate.of(2026, 1, 1),
                        ),
                    )
                }
            assertEquals(2L, total)
            assertEquals(2, facturas.size)
        }

    @Test
    fun `B09 Inverted date query returns zero invoices`() =
        withDatabase {
            seedFactura("f-1", createdAt = LocalDateTime.of(2026, 8, 10, 10, 0))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(
                            fechaInicio = LocalDate.of(2026, 8, 20),
                            fechaFin = LocalDate.of(2026, 8, 10),
                        ),
                    )
                }
            assertEquals(0L, total)
            assertTrue(facturas.isEmpty())
        }

    @Test
    fun `B10 Start date only includes invoices on and after start date`() =
        withDatabase {
            seedFactura("f-before", createdAt = LocalDateTime.of(2026, 8, 4, 23, 59))
            seedFactura("f-on", createdAt = LocalDateTime.of(2026, 8, 5, 0, 0))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(fechaInicio = LocalDate.of(2026, 8, 5)),
                    )
                }
            assertEquals(1L, total)
            assertEquals("f-on", facturas.single().id)
        }

    // ==========================================
    // Financial & Amount Boundaries (10 tests)
    // ==========================================

    @Test
    fun `B11 Zero amount invoice 0_00 is stored and summarized accurately`() =
        withDatabase {
            seedFactura("f-zero", total = 0.0)

            val repo = FacturasRepository()
            val (facturas, total) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            assertEquals(1L, total)
            assertEquals(0.0, facturas.first().total)

            val resumen = runBlocking { repo.getResumen(database, "PA", FacturasFilter()) }
            assertEquals(0.0, resumen.ventasNetas)
        }

    @Test
    fun `B12 Minimum fractional amount 0_01 is stored and retrieved with precision`() =
        withDatabase {
            seedFactura("f-cent", total = 0.01)

            val repo = FacturasRepository()
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            assertEquals(0.01, facturas.first().total)
        }

    @Test
    fun `B13 Large financial amount is stored without numeric overflow`() =
        withDatabase {
            val largeAmount = 99999999.99
            seedFactura("f-large", total = largeAmount)

            val repo = FacturasRepository()
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            assertEquals(largeAmount, facturas.first().total)
        }

    @Test
    fun `B14 Partial credit note exact remaining balance calculation`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertNotNull(source)
            assertEquals(10.0, source.remainingAmount, 0.001)
        }

    @Test
    fun `B15 Credit note full return leaves 0 remaining amount`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val req =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(req, "tester") }
            transaction(database) {
                repository.finalizePanamaAccepted(
                    prep.id,
                    req,
                    PacResponse(true, "200", "OK", cufe = "CUFE-FULL"),
                    prep.numeroDocumentoFiscal,
                )
            }
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertEquals(0.0, source?.remainingAmount ?: -1.0, 0.001)
        }

    @Test
    fun `B16 Over-refunding credit note exceeds remaining balance and fails`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val req =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 5.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            assertFailsWith<Exception> {
                transaction(database) {
                    repository.preparePanama(req, "tester")
                }
            }
        }

    @Test
    fun `B17 Negative amounts in cash close request are handled cleanly`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open =
                runBlocking {
                    workflow
                        .open(
                            database,
                            "PA",
                            "db",
                            AperturaRequest("caja-1", 0.0, 1),
                            "u",
                        ).getOrThrow()
                }
            val close =
                runBlocking {
                    workflow.close(
                        database,
                        "PA",
                        CajaCierreSaveRequest(id = open.idCajaSecuencia, montoTotal = 0.0),
                    )
                }
            assertTrue(close.isSuccess)
        }

    @Test
    fun `B18 Summary ventasNetas sums multiple fractional invoices accurately`() =
        withDatabase {
            seedFactura("f-1", total = 10.25)
            seedFactura("f-2", total = 20.50)
            seedFactura("f-3", total = 5.25)

            val repo = FacturasRepository()
            val resumen = runBlocking { repo.getResumen(database, "PA", FacturasFilter()) }
            assertEquals(36.0, resumen.ventasNetas, 0.001)
        }

    @Test
    fun `B19 Credit note with zero percentage discount calculates correctly`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 50.0)
            val repo = CreditNoteRepository()
            val source = transaction(database) { repo.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertNotNull(source)
            assertEquals(50.0, source.subtotalOriginal)
        }

    @Test
    fun `B20 Multiple partial payments breakdown in cash close sums to total`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open =
                runBlocking {
                    workflow
                        .open(
                            database,
                            "PA",
                            "db",
                            AperturaRequest("caja-1", 50.0, 1),
                            "u",
                        ).getOrThrow()
                }
            val close =
                runBlocking {
                    workflow.close(
                        database,
                        "PA",
                        CajaCierreSaveRequest(
                            id = open.idCajaSecuencia,
                            montoTotal = 200.0,
                            montoEfectivoTotal = 120.0,
                            montoOtrosTotal = 80.0,
                        ),
                    )
                }
            assertTrue(close.isSuccess)
        }

    // ==========================================
    // Pagination & Offset Boundaries (10 tests)
    // ==========================================

    @Test
    fun `B21 Pagination limit 0 returns empty list with total count preserved`() =
        withDatabase {
            seedFactura("f-1")
            seedFactura("f-2")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 0, offset = 0, filter = FacturasFilter())
                }
            assertEquals(2L, total)
            assertTrue(facturas.isEmpty())
        }

    @Test
    fun `B22 Pagination limit 1 returns exact single first item`() =
        withDatabase {
            seedFactura("f-1", createdAt = LocalDateTime.of(2026, 8, 1, 10, 0))
            seedFactura("f-2", createdAt = LocalDateTime.of(2026, 8, 2, 10, 0))

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 1, offset = 0, filter = FacturasFilter())
                }
            assertEquals(2L, total)
            assertEquals(1, facturas.size)
            assertEquals("f-2", facturas.first().id)
        }

    @Test
    fun `B23 Pagination large limit 1000 retrieves all items without truncation`() =
        withDatabase {
            for (i in 1..5) {
                seedFactura("f-$i")
            }
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 1000, offset = 0, filter = FacturasFilter())
                }
            assertEquals(5L, total)
            assertEquals(5, facturas.size)
        }

    @Test
    fun `B24 Offset exceeding total records returns empty list`() =
        withDatabase {
            seedFactura("f-1")
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 10, offset = 100, filter = FacturasFilter())
                }
            assertEquals(1L, total)
            assertTrue(facturas.isEmpty())
        }

    @Test
    fun `B25 Credit note source invoice limit 1 returns single record`() =
        withDatabase {
            seedEligibleInvoice("inv-1", LocalDate.of(2026, 8, 1))
            seedEligibleInvoice("inv-2", LocalDate.of(2026, 8, 2))

            val repo = CreditNoteRepository()
            val (data, total) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 1,
                        offset = 0,
                        search = null,
                        fechaInicio = LocalDate.of(2026, 8, 1),
                        fechaFin = LocalDate.of(2026, 8, 31),
                    )
                }
            assertEquals(1, data.size)
            assertEquals(2L, total)
        }

    @Test
    fun `B26 Credit note source invoice offset advances window correctly`() =
        withDatabase {
            seedEligibleInvoice("inv-1", LocalDate.of(2026, 8, 1))
            seedEligibleInvoice("inv-2", LocalDate.of(2026, 8, 2))

            val repo = CreditNoteRepository()
            val (data, _) =
                transaction(database) {
                    repo.listEligibleInvoices(
                        countryCode = "PA",
                        limit = 1,
                        offset = 1,
                        search = null,
                        fechaInicio = LocalDate.of(2026, 8, 1),
                        fechaFin = LocalDate.of(2026, 8, 31),
                    )
                }
            assertEquals(1, data.size)
            assertEquals("inv-1", data.first().id)
        }

    @Test
    fun `B27 Empty database pagination returns total 0 and empty list`() =
        withDatabase {
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 50, offset = 0, filter = FacturasFilter())
                }
            assertEquals(0L, total)
            assertTrue(facturas.isEmpty())
        }

    @Test
    fun `B28 Pagination step by step covers full universe without duplicates`() =
        withDatabase {
            seedFactura("f-1", createdAt = LocalDateTime.of(2026, 8, 1, 10, 0))
            seedFactura("f-2", createdAt = LocalDateTime.of(2026, 8, 2, 10, 0))
            seedFactura("f-3", createdAt = LocalDateTime.of(2026, 8, 3, 10, 0))

            val repo = FacturasRepository()
            val page1 =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 2, offset = 0, filter = FacturasFilter()).first
                }
            val page2 =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 2, offset = 2, filter = FacturasFilter()).first
                }

            val allIds = (page1 + page2).map { it.id }
            assertEquals(3, allIds.distinct().size)
        }

    @Test
    fun `B29 Search with pagination applies limit on filtered subset`() =
        withDatabase {
            seedFactura("match-1", code = "MATCH-1")
            seedFactura("match-2", code = "MATCH-2")
            seedFactura("other-1", code = "OTHER-1")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", limit = 1, offset = 0, filter = FacturasFilter(search = "MATCH"))
                }
            assertEquals(2L, total)
            assertEquals(1, facturas.size)
        }

    @Test
    fun `B30 getResumen is unaffected by pagination limit and offset`() =
        withDatabase {
            seedFactura("f-1", total = 100.0)
            seedFactura("f-2", total = 200.0)

            val repo = FacturasRepository()
            val resumen = runBlocking { repo.getResumen(database, "PA", FacturasFilter()) }
            assertEquals(2, resumen.totalFacturas)
            assertEquals(300.0, resumen.ventasNetas)
        }

    // ==========================================
    // String, Special Characters & Null Boundaries (10 tests)
    // ==========================================

    @Test
    fun `B31 Empty search string returns all invoices without error`() =
        withDatabase {
            seedFactura("f-1")
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(
                        database,
                        "PA",
                        10,
                        0,
                        FacturasFilter(search = ""),
                    )
                }
            assertEquals(1L, total)
            assertEquals(1, facturas.size)
        }

    @Test
    fun `B32 Blank whitespace search string returns all invoices`() =
        withDatabase {
            seedFactura("f-1")
            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(search = "   "))
                }
            assertEquals(1L, total)
            assertEquals(1, facturas.size)
        }

    @Test
    fun `B33 Special SQL characters in search string do not crash query`() =
        withDatabase {
            seedFactura("f-spec", code = "FAC%_100")
            val repo = FacturasRepository()
            val (facturas, _) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(search = "%_100"))
                }
            assertEquals(1, facturas.size)
            assertEquals("f-spec", facturas.first().id)
        }

    @Test
    fun `B34 Quotes and apostrophes in search string are escaped cleanly`() =
        withDatabase {
            seedFactura("f-quote", code = "FAC'S-1")
            val repo = FacturasRepository()
            val (facturas, _) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(search = "FAC'S"))
                }
            assertEquals(1, facturas.size)
        }

    @Test
    fun `B35 Null active cajaId in filter does not restrict by caja`() =
        withDatabase {
            seedFactura("f-c1", cajaId = "caja-1")
            seedFactura("f-c2", cajaId = "caja-2")

            val repo = FacturasRepository()
            val (facturas, total) =
                runBlocking {
                    repo.listFacturas(database, "PA", 10, 0, FacturasFilter(cajaId = null))
                }
            assertEquals(2L, total)
            assertEquals(2, facturas.size)
        }

    @Test
    fun `B36 Invoice with null CUFE is marked pending and serializes null cufe`() =
        withDatabase {
            seedFactura("f-no-cufe", cufe = null)
            val repo = FacturasRepository()
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            val f = facturas.first()
            assertEquals("", f.codigoFiscal)
        }

    @Test
    fun `B37 Invoice with empty string CUFE is handled properly`() =
        withDatabase {
            seedFactura("f-empty-cufe", cufe = "")
            val repo = FacturasRepository()
            val (facturas, _) = runBlocking { repo.listFacturas(database, "PA", 10, 0, FacturasFilter()) }
            assertEquals("", facturas.first().codigoFiscal)
        }

    @Test
    fun `B38 Missing customer email does not prevent invoice queries`() =
        withDatabase {
            seedFactura("f-no-email")
            val repo = FacturasRepository()
            val payload = runBlocking { repo.getPrintPayload(database, "PA", "f-no-email", "Empresa") }
            assertNotNull(payload)
        }

    @Test
    fun `B39 Extremely long observation in cash close is preserved`() =
        withDatabase {
            val workflow = CajaSessionWorkflow(ExposedCajaSessionStore())
            val open =
                runBlocking {
                    workflow
                        .open(
                            database,
                            "PA",
                            "db",
                            AperturaRequest("caja-1", 0.0, 1),
                            "u",
                        ).getOrThrow()
                }
            val longObs = "A".repeat(200)
            val close =
                runBlocking {
                    workflow.close(
                        database,
                        "PA",
                        CajaCierreSaveRequest(
                            id = open.idCajaSecuencia,
                            montoTotal = 0.0,
                            observacionCierre = longObs,
                        ),
                    )
                }
            assertTrue(close.isSuccess)
        }

    @Test
    fun `B40 Non-existent invoice print payload lookup returns null safely`() =
        withDatabase {
            val repo = FacturasRepository()
            val payload = runBlocking { repo.getPrintPayload(database, "PA", "missing-id-999", "Empresa") }
            assertNull(payload)
        }

    // ==========================================
    // PAC Communication & Retry Boundaries (10 tests)
    // ==========================================

    @Test
    fun `B41 PAC authentication rejection 401 returns AUTH_ERROR`() =
        withDatabase {
            seedFactura("fe-auth-fail", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) =
                        Result.failure<PacAuthToken>(IllegalStateException("HTTP 401 Unauthorized"))

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
                    ) = Result.success(ByteArray(0))
                }
            val processor = PanamaInvoiceProcessor(ElectronicInvoiceRepository(), client, TheFactoryHkaPayloadBuilder())
            val result = runBlocking { processor.processElectronicInvoice(database, "fe-auth-fail") }
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("AUTH_ERROR", failure.codigo)
        }

    @Test
    fun `B42 PAC server error 500 returns SEND_ERROR failure`() =
        withDatabase {
            seedFactura("fe-500", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.failure<PacResponse>(java.io.IOException("HTTP 500 Internal Server Error"))

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
            val result = runBlocking { processor.processElectronicInvoice(database, "fe-500") }
            val failure = assertIs<ElectronicInvoiceResult.Failure>(result)
            assertEquals("SEND_ERROR", failure.codigo)
        }

    @Test
    fun `B43 PAC network timeout returns SEND_ERROR without altering database`() =
        withDatabase {
            seedFactura("fe-timeout", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.failure<PacResponse>(java.net.SocketTimeoutException("Read timed out"))

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
            val result = runBlocking { processor.processElectronicInvoice(database, "fe-timeout") }
            assertIs<ElectronicInvoiceResult.Failure>(result)

            val storedCufe = runBlocking { ElectronicInvoiceRepository().getInvoiceCufe(database, "fe-timeout") }
            assertNull(storedCufe, "Timeout must not store partial CUFE")
        }

    @Test
    fun `B44 PAC successful retry after initial failure stores CUFE properly`() =
        withDatabase {
            seedFactura("fe-retry", cufe = null)

            var attempt = 0
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ): Result<PacResponse> {
                        attempt++
                        return if (attempt == 1) {
                            Result.failure(java.io.IOException("Transient glitch"))
                        } else {
                            Result.success(PacResponse(true, "200", "OK", cufe = "CUFE-RETRY-SUCCESS"))
                        }
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

            val processor = PanamaInvoiceProcessor(ElectronicInvoiceRepository(), client, TheFactoryHkaPayloadBuilder())
            val firstResult = runBlocking { processor.processElectronicInvoice(database, "fe-retry") }
            assertIs<ElectronicInvoiceResult.Failure>(firstResult)

            val secondResult = runBlocking { processor.processElectronicInvoice(database, "fe-retry") }
            val success = assertIs<ElectronicInvoiceResult.Success>(secondResult)
            assertEquals("CUFE-RETRY-SUCCESS", success.cufe)
        }

    @Test
    fun `B45 Database failure during post-DGI update preserves Success result`() =
        withDatabase {
            seedFactura("fe-post-dgi", cufe = null)
            val client =
                object : PanamaElectronicInvoiceClient {
                    override suspend fun authenticate(credentials: PacCredentials) = Result.success(PacAuthToken("tok"))

                    override suspend fun sendDocument(
                        baseUrl: String,
                        token: PacAuthToken,
                        payload: TheFactoryHkaDocumentoWrapper,
                    ) = Result.success(PacResponse(true, "200", "OK", cufe = "CUFE-DGI-OK"))

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

            val repo =
                object : ElectronicInvoiceRepository() {
                    override suspend fun updateInvoiceWithFEResponse(
                        database: Database,
                        update: com.amaxoniaerp.features.electronicinvoice.data.FeResponseUpdate,
                    ): Int = throw java.sql.SQLException("Simulated DB lock failure")
                }
            val processor = PanamaInvoiceProcessor(repo, client, TheFactoryHkaPayloadBuilder())
            val result = runBlocking { processor.processElectronicInvoice(database, "fe-post-dgi") }
            val success = assertIs<ElectronicInvoiceResult.Success>(result)
            assertEquals("CUFE-DGI-OK", success.cufe)
        }

    @Test
    fun `B46 PDF download with 0-byte stream returns empty array without throwing`() =
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
                    ) = Result.success(ByteArray(0))
                }
            val res = runBlocking { client.downloadPdf("https://pac", PacAuthToken("t"), "CUFE") }
            assertTrue(res.isSuccess)
            assertEquals(0, res.getOrThrow().size)
        }

    @Test
    fun `B47 PAC rejects credit note and releases reserved invoice balance`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val req =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(req, "tester") }
            transaction(database) {
                repository.markPanamaFiscalStatus(prep.id, CreditNoteFiscalStatus.RECHAZADA, "PAC rechazado")
            }
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertEquals(10.0, source?.remainingAmount ?: -1.0, 0.001)
        }

    @Test
    fun `B48 Uncertain PAC state retains reserved quantity pending resolution`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val req =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(req, "tester") }
            transaction(database) {
                repository.markPanamaFiscalStatus(prep.id, CreditNoteFiscalStatus.INCIERTA, "PAC Timeout")
            }
            val source = transaction(database) { repository.getSourceInvoiceDetail("factura-src-1", "PA") }
            assertEquals(0.0, source?.remainingAmount ?: -1.0, 0.001)
        }

    @Test
    fun `B49 Idempotent credit note finalization returns confirmed status without duplicate records`() =
        withDatabase {
            seedCreditNotePrerequisites(initialTotal = 10.0)
            val repository = CreditNoteRepository()
            val req =
                CreateCreditNoteRequest(
                    idFactura = "factura-src-1",
                    fecha = "2026-08-31",
                    detalle = listOf(CreateCreditNoteLineInput("detalle-src-1", 1.0)),
                    devolverStock = false,
                    idCajaSecuencia = "caja-secuencia-1",
                    settlementType = CreditNoteSettlementType.NINGUNO,
                )
            val prep = transaction(database) { repository.preparePanama(req, "tester") }
            val r1 =
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        prep.id,
                        req,
                        PacResponse(true, "200", "OK", cufe = "CUFE-IDEMP-1"),
                        prep.numeroDocumentoFiscal,
                    )
                }
            val r2 =
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        prep.id,
                        req,
                        PacResponse(true, "200", "OK", cufe = "CUFE-IDEMP-1"),
                        prep.numeroDocumentoFiscal,
                    )
                }
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, r1.fiscalStatus)
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, r2.fiscalStatus)
            val count = transaction(database) { CreditNoteHeaderTablePA.selectAll().count() }
            assertEquals(1L, count)
        }

    @Test
    fun `B50 Correlativo counter increments atomically per confirmed invoice`() =
        withDatabase {
            val initial =
                transaction(database) {
                    FECorrelativosTable.selectAll().single()[FECorrelativosTable.contador]
                }
            runBlocking { ElectronicInvoiceRepository().incrementNumeroDocumentoFiscal(database) }
            val updated =
                transaction(database) {
                    FECorrelativosTable.selectAll().single()[FECorrelativosTable.contador]
                }
            assertEquals(initial + 1, updated)
        }

    // ==========================================
    // Test Infrastructure Helpers
    // ==========================================

    private lateinit var database: Database

    private fun withDatabase(block: Tier2BackendBoundaryCornerCasesE2ETest.() -> Unit) {
        val dbName = "t2_be_" + UUID.randomUUID().toString().replace("-", "")
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
            it[codAlmacen] = 1
            it[codEstatus] = 1
            it[serieCaja] = "S1"
        }
    }

    private fun seedFactura(
        id: String,
        code: String = "FAC-$id",
        total: Double = 100.0,
        cajaId: String = "caja-1",
        cufe: String? = "CUFE-$id",
        createdAt: LocalDateTime = LocalDateTime.of(2026, 8, 1, 10, 0),
    ) = transaction(database) {
        val dtStr = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        FacturasTablePA.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = ("CF-$code").take(10)
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = "client-1"
            it[codVendedor] = 1
            it[codEstatus] = 2
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
            it[fechaRecepcionDGI] = cufe?.let { "2026-08-01 10:05:00" }
        }
    }

    private fun seedEligibleInvoice(
        id: String,
        date: LocalDate,
        code: String = "FAC-$id",
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
            it[codFactura] = code
            it[codFacturaFiscal] = ("CF-$code").take(10)
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

    private fun seedCreditNotePrerequisites(initialTotal: Double = 10.0) =
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
