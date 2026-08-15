package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteLineInput
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.electronicinvoice.data.FECorrelativosTable
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreditNotePanamaStagedFlowTest {
    @Test
    fun `accepted applies effects once and finalization is idempotent`() =
        withDatabase {
            val prepared = prepare()
            val response = finalizeAccepted(prepared.id, prepared.numeroDocumentoFiscal)
            val retry = finalizeAccepted(prepared.id, prepared.numeroDocumentoFiscal)

            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, response.fiscalStatus)
            assertEquals(CreditNoteFiscalStatus.CONFIRMADA, retry.fiscalStatus)
            assertEquals(
                3,
                transaction(database) {
                    CreditNoteFacturaTable.selectAll().single()[CreditNoteFacturaTable.codEstatus]
                },
            )
            assertEquals(1, transaction(database) { CreditNoteHeaderTablePA.selectAll().count() })
            assertEquals(1, transaction(database) { CreditNoteDetailTable.selectAll().count() })
            assertEquals(0, transaction(database) { SalesCajaNuevaTableFactory.forCountry("PA").selectAll().count() })
        }

    @Test
    fun `rejected releases reservation without commercial effects`() =
        withDatabase {
            val prepared = prepare()

            val response =
                transaction(database) {
                    repository.markPanamaFiscalStatus(
                        id = prepared.id,
                        status = CreditNoteFiscalStatus.RECHAZADA,
                        message = "PAC rechazó el documento",
                    )
                }
            val source =
                transaction(database) {
                    repository.getSourceInvoiceDetail(SOURCE_INVOICE_ID, "PA")
                }

            assertEquals(CreditNoteFiscalStatus.RECHAZADA, response.fiscalStatus)
            assertEquals(1.0, source?.remainingAmount ?: -1.0, 0.001)
            assertEquals(
                2,
                transaction(database) {
                    CreditNoteFacturaTable.selectAll().single()[CreditNoteFacturaTable.codEstatus]
                },
            )
            assertEquals(0, transaction(database) { SalesCajaNuevaTableFactory.forCountry("PA").selectAll().count() })
        }

    @Test
    fun `uncertain keeps the reserved quantity`() =
        withDatabase {
            val prepared = prepare()

            val response =
                transaction(database) {
                    repository.markPanamaFiscalStatus(
                        id = prepared.id,
                        status = CreditNoteFiscalStatus.INCIERTA,
                        message = "Timeout del PAC",
                    )
                }
            val source =
                transaction(database) {
                    repository.getSourceInvoiceDetail(SOURCE_INVOICE_ID, "PA")
                }

            assertEquals(CreditNoteFiscalStatus.INCIERTA, response.fiscalStatus)
            assertEquals(0.0, source?.remainingAmount ?: -1.0, 0.001)
            assertEquals(0, transaction(database) { SalesCajaNuevaTableFactory.forCountry("PA").selectAll().count() })
        }

    @Test
    fun `database failure after PAC acceptance can be marked uncertain without effects`() =
        withDatabase {
            val prepared = prepare()

            assertFailsWith<Exception> {
                transaction(database) {
                    repository.finalizePanamaAccepted(
                        id = prepared.id,
                        request = request().copy(settlementType = CreditNoteSettlementType.ABONO),
                        pacResponse =
                            PacResponse(
                                exitoso = true,
                                codigo = "200",
                                mensaje = "OK",
                                cufe = "A".repeat(66),
                            ),
                        numeroDocumentoFiscal = prepared.numeroDocumentoFiscal,
                    )
                }
            }

            val response =
                transaction(database) {
                    repository.markPanamaFiscalStatus(
                        id = prepared.id,
                        status = CreditNoteFiscalStatus.INCIERTA,
                        message = "PAC aceptó, pero falló la persistencia local",
                    )
                }

            assertEquals(CreditNoteFiscalStatus.INCIERTA, response.fiscalStatus)
            assertEquals(
                2,
                transaction(database) {
                    CreditNoteFacturaTable.selectAll().single()[CreditNoteFacturaTable.codEstatus]
                },
            )
            assertEquals(
                false,
                transaction(database) {
                    CreditNoteFacturaDetalleTable.selectAll().single()[CreditNoteFacturaDetalleTable.anulado]
                },
            )
        }

    @Test
    fun `concurrent prepares cannot reserve more than the source quantity`() =
        withDatabase {
            val request = request()
            val results =
                runBlocking {
                    listOf(
                        async(Dispatchers.Default) {
                            runCatching { transaction(database) { repository.preparePanama(request, "one") } }
                        },
                        async(Dispatchers.Default) {
                            runCatching { transaction(database) { repository.preparePanama(request, "two") } }
                        },
                    ).awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, transaction(database) { CreditNoteHeaderTablePA.selectAll().count() })
            assertTrue(results.any { it.isFailure })
        }

    private val repository = CreditNoteRepository()
    private lateinit var database: Database

    private fun prepare() =
        transaction(database) {
            repository.preparePanama(request(), "tester")
        }

    private fun finalizeAccepted(
        id: String,
        number: String,
    ) = transaction(database) {
        repository.finalizePanamaAccepted(
            id = id,
            request = request(),
            pacResponse =
                PacResponse(
                    exitoso = true,
                    codigo = "200",
                    mensaje = "OK",
                    cufe = "A".repeat(66),
                ),
            numeroDocumentoFiscal = number,
        )
    }

    private fun request() =
        CreateCreditNoteRequest(
            idFactura = SOURCE_INVOICE_ID,
            fecha = LocalDate.of(2026, 1, 10).toString(),
            detalle = listOf(CreateCreditNoteLineInput(SOURCE_DETAIL_ID, 1.0)),
            devolverStock = false,
            idCajaSecuencia = SOURCE_CAJA_SEQUENCE_ID,
            settlementType = CreditNoteSettlementType.NINGUNO,
        )

    private fun withDatabase(block: CreditNotePanamaStagedFlowTest.() -> Unit) {
        database =
            Database.connect(
                url = "jdbc:h2:mem:credit_note_staged_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                ClientsTable,
                CreditNoteFacturaTable,
                CreditNoteFacturaDetalleTable,
                CreditNoteDetailTable,
                CreditNoteHeaderTablePA,
                CreditNoteCajaTable,
                CreditNoteCajaSecuenciaTable,
                CajaFormaPagoTable,
                FECorrelativosTable,
                SalesCajaNuevaTableFactory.forCountry("PA"),
                SalesCajaNuevaDetalleTableFactory.forCountry("PA"),
                SalesCajaNuevaReciboTableFactory.forCountry("PA"),
            )
            seedInvoice()
        }

        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    SalesCajaNuevaReciboTableFactory.forCountry("PA"),
                    SalesCajaNuevaDetalleTableFactory.forCountry("PA"),
                    SalesCajaNuevaTableFactory.forCountry("PA"),
                    FECorrelativosTable,
                    CajaFormaPagoTable,
                    CreditNoteCajaSecuenciaTable,
                    CreditNoteCajaTable,
                    CreditNoteHeaderTablePA,
                    CreditNoteDetailTable,
                    CreditNoteFacturaDetalleTable,
                    CreditNoteFacturaTable,
                    ClientsTable,
                )
            }
        }
    }

    private fun seedInvoice() {
        ClientsTable.insert {
            it[idCliente] = CLIENT_ID
            it[codCliente] = "CLIENT-1"
            it[rif] = "8-123-456"
            it[dv] = "1"
            it[nombre] = "Cliente"
            it[apellido] = "Prueba"
            it[direccion] = "Direccion"
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
            it[idFactura] = SOURCE_INVOICE_ID
            it[codFactura] = "F-001"
            it[codFacturaFiscal] = "CF-001"
            it[numeroDocumentoFiscal] = "0000000001"
            it[idCliente] = CLIENT_ID
            it[codVendedor] = 1
            it[codEstatus] = 2
            it[fechaFactura] = LocalDate.of(2026, 1, 1)
            it[fechaCreacion] = LocalDateTime.of(2026, 1, 1, 10, 0)
            it[subtotal] = BigDecimal.ONE
            it[totalizarSubTotal] = BigDecimal.ONE
            it[totalizarTotalOperacion] = BigDecimal.ONE
            it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarBaseImponible] = BigDecimal.ONE
            it[totalizarMontoIva] = BigDecimal.ZERO
            it[totalizarTotalGeneral] = BigDecimal.ONE
            it[totalTotalFactura] = BigDecimal.ONE
            it[formaPago] = "contado"
            it[idCajaSecuencia] = SOURCE_CAJA_SEQUENCE_ID
            it[idCaja] = SOURCE_CAJA_ID
            it[idSucursal] = 1
            it[serieSucursal] = "A"
            it[codigoCaja] = "CAJA"
            it[facturarA] = "Cliente Prueba"
            it[facturarARuc] = "8-123-456"
            it[facturarADireccion] = "Direccion"
            it[facturarATelefono] = "000"
            it[abrMonedaBase] = "USD"
            it[tasa] = BigDecimal.ONE
            it[totalRef] = BigDecimal.ONE
        }
        CreditNoteFacturaDetalleTable.insert {
            it[idDetalleFactura] = SOURCE_DETAIL_ID
            it[idFactura] = SOURCE_INVOICE_ID
            it[idItem] = 1
            it[itemAlmacen] = 1
            it[itemDescripcion] = "Producto"
            it[itemCantidad] = BigDecimal.ONE
            it[itemPrecioSinIva] = BigDecimal.ONE
            it[itemDescuento] = BigDecimal.ZERO
            it[itemMontoDescuento] = BigDecimal.ZERO
            it[itemPIva] = BigDecimal.ZERO
            it[itemTotalSinIva] = BigDecimal.ONE
            it[itemTotalConIva] = BigDecimal.ONE
            it[itemCantidadTotal] = BigDecimal.ONE
            it[codVendedor] = 1
            it[itemCodigo] = "P-1"
            it[itemReferencia] = "REF-1"
            it[anulado] = false
        }
        CreditNoteCajaTable.insert {
            it[idCaja] = SOURCE_CAJA_ID
            it[codigo] = "CAJA"
            it[idSucursal] = 1
            it[notacreditoCorrelativo] = 0
        }
        CreditNoteCajaSecuenciaTable.insert {
            it[idCajaSecuencia] = SOURCE_CAJA_SEQUENCE_ID
            it[idCaja] = SOURCE_CAJA_ID
            it[secuencia] = "000001"
            it[serieSucursal] = "A"
        }
        FECorrelativosTable.insert {
            it[id] = 1
            it[campo] = "numeroDocumentoFiscal"
            it[contador] = 0
        }
    }

    private companion object {
        const val CLIENT_ID = "cliente-1"
        const val SOURCE_INVOICE_ID = "factura-1"
        const val SOURCE_DETAIL_ID = "detalle-1"
        const val SOURCE_CAJA_ID = "caja-1"
        const val SOURCE_CAJA_SEQUENCE_ID = "caja-secuencia-1"
    }
}
