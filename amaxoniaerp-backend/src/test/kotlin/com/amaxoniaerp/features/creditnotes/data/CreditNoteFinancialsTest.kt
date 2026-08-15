package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteLineInput
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTablePA
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class CreditNoteFinancialsTest {
    @Test
    fun `credit note without global discount preserves original amounts`() =
        withSeededDatabase(
            lines = listOf(LineSpec("detail-1", 1.0, 50.0, 20.0), LineSpec("detail-2", 1.0, 50.0, 0.0)),
            globalDiscount = 0.0,
            originalTax = 10.0,
            originalTotal = 110.0,
        ) {
            val response = createNote(listOf("detail-1" to 1.0, "detail-2" to 1.0))

            assertEquals(100.0, response.subtotal, 0.001)
            assertEquals(10.0, response.impuesto, 0.001)
            assertEquals(110.0, response.total, 0.001)
            assertEquals(0.0, headers().single().globalDiscount, 0.001)
        }

    @Test
    fun `global discount is allocated across lines with different tax rates`() =
        withSeededDatabase(
            lines =
                listOf(
                    LineSpec("detail-1", 1.0, 50.0, 20.0),
                    LineSpec("detail-2", 1.0, 30.0, 10.0),
                    LineSpec("detail-3", 1.0, 20.0, 0.0),
                ),
            globalDiscount = 10.0,
            originalTax = 11.70,
            originalTotal = 101.70,
        ) {
            val response =
                createNote(
                    listOf("detail-1" to 1.0, "detail-2" to 1.0, "detail-3" to 1.0),
                )

            assertEquals(90.0, response.subtotal, 0.001)
            assertEquals(11.70, response.impuesto, 0.001)
            assertEquals(101.70, response.total, 0.001)
            assertEquals(10.0, headers().single().globalDiscount, 0.001)
            assertEquals(10.0, headers().single().globalDiscountPercent, 0.001)
        }

    @Test
    fun `partial return applies only the proportional global discount`() =
        withSeededDatabase(
            lines = listOf(LineSpec("detail-1", 10.0, 100.0, 20.0)),
            globalDiscount = 10.0,
            originalTax = 18.0,
            originalTotal = 108.0,
        ) {
            val response = createNote(listOf("detail-1" to 5.0))

            assertEquals(45.0, response.subtotal, 0.001)
            assertEquals(9.0, response.impuesto, 0.001)
            assertEquals(54.0, response.total, 0.001)
            assertEquals(5.0, headers().single().globalDiscount, 0.001)
        }

    @Test
    fun `multiple partial returns exhaust the original discount and total`() =
        withSeededDatabase(
            lines = listOf(LineSpec("detail-1", 10.0, 100.0, 20.0)),
            globalDiscount = 10.0,
            originalTax = 18.0,
            originalTotal = 108.0,
        ) {
            createNote(listOf("detail-1" to 3.0))
            createNote(listOf("detail-1" to 4.0))
            createNote(listOf("detail-1" to 3.0))

            val notes = headers()
            assertEquals(3, notes.size)
            assertEquals(10.0, notes.sumOf { it.globalDiscount }, 0.001)
            assertEquals(108.0, notes.sumOf { it.total }, 0.001)
        }

    @Test
    fun `cent residual is absorbed by the remaining return`() =
        withSeededDatabase(
            lines = listOf(LineSpec("detail-1", 10.0, 100.0, 0.0)),
            globalDiscount = 10.01,
            originalTax = 0.0,
            originalTotal = 89.99,
        ) {
            createNote(listOf("detail-1" to 3.0))
            createNote(listOf("detail-1" to 3.0))
            createNote(listOf("detail-1" to 4.0))

            val discounts = headers().map { it.globalDiscount }
            assertEquals(10.01, discounts.sum(), 0.001)
            assertEquals(4.01, headers().last().globalDiscount, 0.001)
        }

    @Test
    fun `exempt and taxed lines recalculate tax without inventing exempt tax`() =
        withSeededDatabase(
            lines = listOf(LineSpec("detail-1", 1.0, 50.0, 20.0), LineSpec("detail-2", 1.0, 50.0, 0.0)),
            globalDiscount = 10.0,
            originalTax = 9.0,
            originalTotal = 99.0,
        ) {
            val response = createNote(listOf("detail-1" to 1.0, "detail-2" to 1.0))

            assertEquals(90.0, response.subtotal, 0.001)
            assertEquals(9.0, response.impuesto, 0.001)
            assertEquals(99.0, response.total, 0.001)
        }

    private lateinit var database: Database
    private val repository = CreditNoteRepository()

    private fun createNote(lines: List<Pair<String, Double>>) =
        transaction(database) {
            repository.create(
                countryCode = "PA",
                request =
                    CreateCreditNoteRequest(
                        idFactura = SOURCE_INVOICE_ID,
                        fecha = LocalDate.of(2026, 1, 10).toString(),
                        detalle = lines.map { (id, quantity) -> CreateCreditNoteLineInput(id, quantity) },
                        devolverStock = false,
                        idCajaSecuencia = SOURCE_CAJA_SEQUENCE_ID,
                        settlementType = CreditNoteSettlementType.NINGUNO,
                    ),
                username = "tester",
            )
        }

    private fun headers() =
        transaction(database) {
            CreditNoteHeaderTablePA
                .selectAll()
                .orderBy(CreditNoteHeaderTablePA.fechaCreacion)
                .map {
                    HeaderAmounts(
                        globalDiscount = it[CreditNoteHeaderTablePA.descuentoGlobal]?.toDouble() ?: 0.0,
                        globalDiscountPercent = it[CreditNoteHeaderTablePA.pdescuentoGlobal]?.toDouble() ?: 0.0,
                        total = it[CreditNoteHeaderTablePA.total].toDouble(),
                    )
                }
        }

    private fun withSeededDatabase(
        lines: List<LineSpec>,
        globalDiscount: Double,
        originalTax: Double,
        originalTotal: Double,
        block: CreditNoteFinancialsTest.() -> Unit,
    ) {
        database =
            Database.connect(
                url = "jdbc:h2:mem:credit_note_financials_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",
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
                SalesCajaNuevaTablePA,
                CajaFormaPagoTable,
            )
            seedInvoice(lines, globalDiscount, originalTax, originalTotal)
        }

        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    SalesCajaNuevaTablePA,
                    CreditNoteCajaSecuenciaTable,
                    CreditNoteCajaTable,
                    CreditNoteHeaderTablePA,
                    CreditNoteDetailTable,
                    CreditNoteFacturaDetalleTable,
                    CreditNoteFacturaTable,
                    ClientsTable,
                    CajaFormaPagoTable,
                )
            }
        }
    }

    private fun seedInvoice(
        lines: List<LineSpec>,
        globalDiscount: Double,
        originalTax: Double,
        originalTotal: Double,
    ) {
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
        val base = lines.sumOf { it.base }
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
            it[subtotal] = base.toBigDecimal()
            it[totalizarSubTotal] = base.toBigDecimal()
            it[totalizarTotalOperacion] = base.toBigDecimal()
            it[totalizarPDescuentoGlobal] = percentage(globalDiscount, base)
            it[totalizarDescuentoGlobal] = globalDiscount.toBigDecimal()
            it[totalizarBaseImponible] = (base - globalDiscount).toBigDecimal()
            it[totalizarMontoIva] = originalTax.toBigDecimal()
            it[totalizarTotalGeneral] = originalTotal.toBigDecimal()
            it[totalTotalFactura] = originalTotal.toBigDecimal()
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
            it[totalRef] = originalTotal.toBigDecimal()
        }
        lines.forEachIndexed { index, line ->
            val detailTax =
                line.base
                    .toBigDecimal()
                    .multiply(line.taxRate.toBigDecimal())
                    .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            CreditNoteFacturaDetalleTable.insert {
                it[idDetalleFactura] = line.id
                it[idFactura] = SOURCE_INVOICE_ID
                it[idItem] = index + 1
                it[itemAlmacen] = 1
                it[itemDescripcion] = "Producto ${index + 1}"
                it[itemCantidad] = line.quantity.toBigDecimal()
                it[itemPrecioSinIva] = line.base.toBigDecimal().divide(line.quantity.toBigDecimal(), 2, RoundingMode.HALF_UP)
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = BigDecimal.ZERO
                it[itemPIva] = line.taxRate.toBigDecimal()
                it[itemTotalSinIva] = line.base.toBigDecimal()
                it[itemTotalConIva] = line.base.toBigDecimal() + detailTax
                it[itemCantidadTotal] = line.quantity.toBigDecimal()
                it[codVendedor] = 1
                it[itemCodigo] = "P-${index + 1}"
                it[itemReferencia] = "REF-${index + 1}"
                it[anulado] = false
            }
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
        CajaFormaPagoTable.insert {
            it[idFormaPago] = 31
            it[siglas] = "NC"
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

    private fun percentage(
        amount: Double,
        base: Double,
    ): BigDecimal =
        if (base ==
            0.0
        ) {
            BigDecimal.ZERO
        } else {
            amount.toBigDecimal().multiply(BigDecimal("100")).divide(base.toBigDecimal(), 2, RoundingMode.HALF_UP)
        }

    private data class LineSpec(
        val id: String,
        val quantity: Double,
        val base: Double,
        val taxRate: Double,
    )

    private data class HeaderAmounts(
        val globalDiscount: Double,
        val globalDiscountPercent: Double,
        val total: Double,
    )

    private companion object {
        const val CLIENT_ID = "cliente-1"
        const val SOURCE_INVOICE_ID = "factura-1"
        const val SOURCE_CAJA_ID = "caja-1"
        const val SOURCE_CAJA_SEQUENCE_ID = "caja-secuencia-1"
    }
}
