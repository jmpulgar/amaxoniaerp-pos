package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteLineInput
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreditNoteRepositoryEligibilityTest {
    @Test
    fun `PA invoice status 3 remains eligible while balance remains`() =
        withSeededDatabase {
            updateSourceInvoiceStatus(3)
            addReturnedQuantity(2.0, "return-1")

            val (data, total) = listEligible("PA")

            assertEquals(1L, total)
            assertEquals(36.0, data.single().remainingAmount, 0.001)
        }

    @Test
    fun `VE invoice status 3 remains excluded`() =
        withSeededDatabase {
            updateSourceInvoiceStatus(3)

            val (data, total) = listEligible("VE")

            assertEquals(0L, total)
            assertEquals(emptyList(), data)
        }

    @Test
    fun `two sequential returns consume only the available balance`() =
        withSeededDatabase {
            addReturnedQuantity(2.0, "return-1")
            val afterFirst = listEligible("PA").first.single()
            assertEquals(36.0, afterFirst.remainingAmount, 0.001)

            addReturnedQuantity(3.0, "return-2")
            val afterSecond = listEligible("PA")
            assertEquals(0L, afterSecond.second)
            assertEquals(emptyList(), afterSecond.first)
        }

    @Test
    fun `return greater than remaining is rejected before persistence work`() =
        withSeededDatabase {
            addReturnedQuantity(4.0, "return-1")

            val exception =
                assertFailsWith<CreditNoteValidationException> {
                    transaction(database) {
                        repository.create(
                            countryCode = "PA",
                            request =
                                CreateCreditNoteRequest(
                                    idFactura = SOURCE_INVOICE_ID,
                                    fecha = LocalDate.now().toString(),
                                    detalle = listOf(CreateCreditNoteLineInput(SOURCE_DETAIL_ID, 2.0)),
                                    idCajaSecuencia = "caja-secuencia-1",
                                    settlementType = CreditNoteSettlementType.NINGUNO,
                                ),
                            username = "tester",
                        )
                    }
                }

            assertEquals(true, exception.message?.contains("excede lo disponible"))
        }

    private val repository = CreditNoteRepository()
    private lateinit var database: Database

    private fun withSeededDatabase(block: suspend CreditNoteRepositoryEligibilityTest.() -> Unit) {
        database =
            Database.connect(
                url =
                    "jdbc:h2:mem:credit_note_eligibility_${UUID.randomUUID().toString().replace("-", "")};" +
                        "MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                ClientsTable,
                CreditNoteFacturaTable,
                CreditNoteFacturaDetalleTable,
                CreditNoteDetailTable,
            )
            seedSourceInvoice()
        }

        try {
            runBlocking { block() }
        } finally {
            transaction(database) {
                SchemaUtils.drop(
                    CreditNoteDetailTable,
                    CreditNoteFacturaDetalleTable,
                    CreditNoteFacturaTable,
                    ClientsTable,
                )
            }
        }
    }

    private fun seedSourceInvoice() {
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
            it[subtotal] = BigDecimal("50.00")
            it[totalizarSubTotal] = BigDecimal("50.00")
            it[totalizarTotalOperacion] = BigDecimal("50.00")
            it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarDescuentoGlobal] = BigDecimal.ZERO
            it[totalizarBaseImponible] = BigDecimal("50.00")
            it[totalizarMontoIva] = BigDecimal("10.00")
            it[totalizarTotalGeneral] = BigDecimal("60.00")
            it[totalTotalFactura] = BigDecimal("60.00")
            it[formaPago] = "contado"
            it[idCajaSecuencia] = "caja-secuencia-1"
            it[idCaja] = "caja-1"
            it[idSucursal] = 1
            it[serieSucursal] = "A"
            it[codigoCaja] = "CAJA"
            it[facturarA] = "Cliente Prueba"
            it[facturarARuc] = "V-123"
            it[facturarADireccion] = "Direccion"
            it[facturarATelefono] = "000"
            it[abrMonedaBase] = "USD"
            it[tasa] = BigDecimal.ONE
            it[totalRef] = BigDecimal("60.00")
        }
        CreditNoteFacturaDetalleTable.insert {
            it[idDetalleFactura] = SOURCE_DETAIL_ID
            it[idFactura] = SOURCE_INVOICE_ID
            it[idItem] = 1
            it[itemAlmacen] = 1
            it[itemDescripcion] = "Producto"
            it[itemCantidad] = BigDecimal("5.000")
            it[itemPrecioSinIva] = BigDecimal("10.00")
            it[itemDescuento] = BigDecimal.ZERO
            it[itemMontoDescuento] = BigDecimal.ZERO
            it[itemPIva] = BigDecimal("20.00")
            it[itemTotalSinIva] = BigDecimal("50.00")
            it[itemTotalConIva] = BigDecimal("60.00")
            it[itemCantidadTotal] = BigDecimal("5.000")
            it[codVendedor] = 1
            it[itemCodigo] = "P-1"
            it[itemReferencia] = "REF-1"
            it[anulado] = false
        }
    }

    private fun addReturnedQuantity(
        quantity: Double,
        id: String,
    ) {
        transaction(database) {
            CreditNoteDetailTable.insert {
                it[idDevolucionDetalle] = id
                it[idDevolucion] = "devolucion-$id"
                it[idDetalleFactura] = SOURCE_DETAIL_ID
                it[idItem] = 1
                it[itemAlmacen] = 1
                it[itemCantidad] = BigDecimal.valueOf(quantity)
                it[itemPrecioSinIva] = BigDecimal("10.00")
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = BigDecimal.ZERO
                it[itemPIva] = BigDecimal("20.00")
                it[itemTotalSinIva] = BigDecimal.valueOf(quantity * 10)
                it[itemTotalConIva] = BigDecimal.valueOf(quantity * 12)
                it[codVendedor] = 1
                it[itemCodigo] = "P-1"
                it[itemReferencia] = "REF-1"
            }
        }
    }

    private fun listEligible(countryCode: String) =
        transaction(database) {
            repository.listEligibleInvoices(countryCode, 50, 0, null)
        }

    private fun updateSourceInvoiceStatus(status: Int) {
        transaction(database) {
            CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq SOURCE_INVOICE_ID }) {
                it[codEstatus] = status
            }
        }
    }

    private companion object {
        const val CLIENT_ID = "cliente-1"
        const val SOURCE_INVOICE_ID = "factura-1"
        const val SOURCE_DETAIL_ID = "detalle-1"
    }
}
