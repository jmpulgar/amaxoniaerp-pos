package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteLineInput
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

class CreditNoteCajaBehaviorTest {
    @Test
    fun `PA cent partial marks invoice and records negative reversal`() =
        withDatabase(
            countryCode = "PA",
            includeOriginalCash = true,
        ) {
            createNote("PA", 0.01)

            val invoiceStatus =
                transaction(database) {
                    CreditNoteFacturaTable
                        .selectAll()
                        .where { CreditNoteFacturaTable.idFactura eq SOURCE_INVOICE_ID }
                        .single()[CreditNoteFacturaTable.codEstatus]
                }
            val reversal =
                transaction(database) {
                    SalesCajaNuevaDetalleTableFactory
                        .forCountry("PA")
                        .selectAll()
                        .where { SalesCajaNuevaDetalleTableFactory.forCountry("PA").idFormaPago eq 31 }
                        .single()
                }

            assertEquals(3, invoiceStatus)
            assertEquals(BigDecimal("-0.01"), reversal[SalesCajaNuevaDetalleTableFactory.forCountry("PA").monto])
            assertEquals(BigDecimal("-0.01"), reversal[SalesCajaNuevaDetalleTableFactory.forCountry("PA").montoOriginal])
        }

    @Test
    fun `PA second partial remains allowed after first marks status 3`() =
        withDatabase(
            countryCode = "PA",
            includeOriginalCash = true,
        ) {
            createNote("PA", 0.01)
            createNote("PA", 0.99)

            val notes = transaction(database) { CreditNoteHeaderTablePA.selectAll().toList() }
            assertEquals(2, notes.size)
            assertEquals(
                3,
                transaction(database) {
                    CreditNoteFacturaTable.selectAll().single()[CreditNoteFacturaTable.codEstatus]
                },
            )
        }

    @Test
    fun `PA total return cancels cash and marks receipt AN`() =
        withDatabase(
            countryCode = "PA",
            includeOriginalCash = true,
        ) {
            createNote("PA", 1.0)

            val cash =
                transaction(database) {
                    SalesCajaNuevaTableFactory.forCountry("PA").selectAll().single()
                }
            val receipt =
                transaction(database) {
                    SalesCajaNuevaReciboTableFactory.forCountry("PA").selectAll().single()
                }

            assertEquals(CajaStatus.Anulada, cash[SalesCajaNuevaTableFactory.forCountry("PA").status])
            assertEquals("AN", receipt[SalesCajaNuevaReciboTableFactory.forCountry("PA").status])
        }

    @Test
    fun `missing active NC payment form fails configuration`() =
        withDatabase(
            countryCode = "PA",
            includeOriginalCash = false,
            includePaymentForm = false,
        ) {
            assertFailsWith<CreditNoteValidationException> {
                createNote("PA", 0.01)
            }
        }

    @Test
    fun `VE partial keeps legacy invoice status`() =
        withDatabase(
            countryCode = "VE",
            includeOriginalCash = false,
        ) {
            createNote("VE", 0.01)

            val status =
                transaction(database) {
                    CreditNoteFacturaTable.selectAll().single()[CreditNoteFacturaTable.codEstatus]
                }
            assertEquals(2, status)
        }

    private lateinit var database: Database
    private val repository = CreditNoteRepository()

    private fun createNote(
        countryCode: String,
        quantity: Double,
    ) = transaction(database) {
        repository.create(
            countryCode = countryCode,
            request =
                CreateCreditNoteRequest(
                    idFactura = SOURCE_INVOICE_ID,
                    fecha = LocalDate.of(2026, 1, 10).toString(),
                    detalle = listOf(CreateCreditNoteLineInput(SOURCE_DETAIL_ID, quantity)),
                    devolverStock = false,
                    idCajaSecuencia = SOURCE_CAJA_SEQUENCE_ID,
                    settlementType = CreditNoteSettlementType.NINGUNO,
                ),
            username = "tester",
        )
    }

    private fun withDatabase(
        countryCode: String,
        includeOriginalCash: Boolean,
        includePaymentForm: Boolean = true,
        block: CreditNoteCajaBehaviorTest.() -> Unit,
    ) {
        database =
            Database.connect(
                url = "jdbc:h2:mem:credit_note_caja_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val cashTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
        val cashDetailTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
        val receiptTable = SalesCajaNuevaReciboTableFactory.forCountry(countryCode)
        transaction(database) {
            SchemaUtils.create(
                ClientsTable,
                CreditNoteFacturaTable,
                CreditNoteFacturaDetalleTable,
                CreditNoteDetailTable,
                headerTable,
                CreditNoteCajaTable,
                CreditNoteCajaSecuenciaTable,
                cashTable,
                cashDetailTable,
                receiptTable,
                CajaFormaPagoTable,
            )
            seedInvoice()
            if (includeOriginalCash) seedOriginalCash(countryCode)
            if (includePaymentForm) seedPaymentForm()
        }

        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    CajaFormaPagoTable,
                    receiptTable,
                    cashDetailTable,
                    cashTable,
                    CreditNoteCajaSecuenciaTable,
                    CreditNoteCajaTable,
                    headerTable,
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
            it[rif] = "V-123"
            it[dv] = ""
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
            it[numeroDocumentoFiscal] = ""
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
            it[facturarARuc] = "V-123"
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
    }

    private fun seedOriginalCash(countryCode: String) {
        val cashTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
        val cashDetailTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
        val receiptTable = SalesCajaNuevaReciboTableFactory.forCountry(countryCode)
        cashTable.insert {
            it[cajaId] = SOURCE_CASH_ID
            it[idTransaccion] = "transaccion-1"
            it[fecha] = LocalDate.of(2026, 1, 1)
            it[ingEg] = CajaIngresoEgreso.I
            it[monto] = BigDecimal.ONE
            it[comprobante] = "FAC"
            it[comprobanteNumero] = "F-001"
            it[idFactura] = SOURCE_INVOICE_ID
            it[idCliente] = CLIENT_ID
            it[status] = CajaStatus.Pagada
            it[sucursalId] = 1
            it[usuarioCreacion] = "tester"
            it[fechaCreacion] = LocalDateTime.of(2026, 1, 1, 10, 0)
            it[idCompra] = ""
            it[idProveedor] = ""
            it[concepto] = "Venta"
            it[idOrdenPago] = ""
            it[serieSucursal] = "A"
            it[idCajaSecuencia] = SOURCE_CAJA_SEQUENCE_ID
            it[idPedido] = ""
            it[idAbono] = ""
            it[idNotaCredito] = ""
        }
        cashDetailTable.insert {
            it[cajaDetalleId] = "cash-detail-1"
            it[cajaId] = SOURCE_CASH_ID
            it[idFormaPago] = 1
            it[idTransaccion] = "transaccion-1"
            it[cajaReciboId] = SOURCE_RECEIPT_ID
            it[monto] = BigDecimal.ONE
            it[montoOriginal] = BigDecimal.ONE
            it[concepto] = "Venta"
            it[usuarioCreacion] = "tester"
            it[fechaCreacion] = LocalDateTime.of(2026, 1, 1, 10, 0)
            it[retencionTipo] = ""
            it[retencionPorcentaje] = ""
            it[numero] = ""
            it[observacion] = ""
            it[retencionBaseCalculo] = ""
            it[serieSucursal] = "A"
            it[cajaSecuencia] = SOURCE_CAJA_SEQUENCE_ID
            it[numeroControl] = ""
            it[numeroComprobante] = "F-001"
            it[retencionMonto] = ""
            it[retencionDetalleJson] = ""
            if (cashDetailTable is SalesCajaNuevaDetalleTableVE) {
                it[cashDetailTable.montoRecibido] = BigDecimal.ONE
                it[cashDetailTable.montoMonedaPrincipal] = BigDecimal.ONE
            }
        }
        receiptTable.insert {
            it[cajaReciboId] = SOURCE_RECEIPT_ID
            it[tipoRecibo] = "FAC"
            it[nroRecibo] = "1"
            it[fecha] = LocalDate.of(2026, 1, 1)
            it[monto] = BigDecimal.ONE
            it[observacion] = ""
            it[codVendedor] = 1
            it[idCliente] = CLIENT_ID
            it[idProveedor] = ""
            it[usuarioCreacion] = "tester"
            it[fechaCreacion] = LocalDateTime.of(2026, 1, 1, 10, 0)
            it[status] = "AC"
            it[contabilizado] = 0
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = LocalDate.of(2026, 1, 1)
            it[idFactura] = SOURCE_INVOICE_ID
            it[idPedido] = ""
            it[idAbono] = ""
            it[idTransaccion] = "transaccion-1"
            it[nroReferencia] = ""
            it[tipoPagoSubtipo] = 0
        }
    }

    private fun seedPaymentForm() {
        CajaFormaPagoTable.insert {
            it[idFormaPago] = 31
            it[siglas] = " NC "
            it[codigo] = 31
            it[descripcion] = "Nota de Crédito"
            it[idCajaTpConcepto] = null
            it[cuentaContable] = null
            it[idCajaTpRegistro] = null
            it[formaPagoFact] = null
            it[activo] = 1
            it[pos] = 0
            it[imagen] = ""
            it[grupo] = 0
            it[orden] = 0
            it[idBancoCuenta] = 0
            it[idBancoOperacion] = 0
            it[tipoMoneda] = "B"
        }
    }

    private companion object {
        const val CLIENT_ID = "cliente-1"
        const val SOURCE_INVOICE_ID = "factura-1"
        const val SOURCE_DETAIL_ID = "detalle-1"
        const val SOURCE_CAJA_ID = "caja-1"
        const val SOURCE_CAJA_SEQUENCE_ID = "caja-secuencia-1"
        const val SOURCE_CASH_ID = "cash-1"
        const val SOURCE_RECEIPT_ID = "receipt-1"
    }
}
