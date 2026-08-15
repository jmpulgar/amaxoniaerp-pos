package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the `descuento` aggregation in [FacturasRepository.getPrintPayload].
 *
 * Validates:
 * - The total line-item discount is computed in BigDecimal and exposed as a non-null money string.
 * - The value matches `SUM(_item_montodescuento)` exactly (no IEEE-754 residue).
 * - Multiple items are aggregated correctly, including when the discount is zero.
 */
class FacturasPrintPayloadDiscountTest {
    private val repository = FacturasRepository()
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                url = "jdbc:h2:mem:facturas_print_${UUID.randomUUID().toString().replace(
                    "-",
                    "",
                )};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(FacturasTableVE, FacturasClientesTable, EstatusTable, SalesFacturaDetalleTable)
            // The raw SQL in `getPrintPayload` references legacy string-typed columns and tables
            // that are not modeled in `BaseFacturasTable` (they exist only in the production
            // schema). Add them here so the in-memory H2 mirrors what the real MySQL DB exposes.
            exec("ALTER TABLE factura ADD COLUMN subtotal DECIMAL(20, 2) DEFAULT 0")
            exec("ALTER TABLE factura ADD COLUMN totalizar_base_imponible DECIMAL(20, 2) DEFAULT 0")
            exec("ALTER TABLE factura ADD COLUMN totalizar_monto_iva DECIMAL(20, 2) DEFAULT 0")
            exec("ALTER TABLE factura ADD COLUMN facturar_a VARCHAR(255) DEFAULT ''")
            exec("ALTER TABLE factura ADD COLUMN facturar_a_ruc VARCHAR(50) DEFAULT ''")
            exec("ALTER TABLE factura ADD COLUMN facturar_a_direccion VARCHAR(255) DEFAULT ''")
            exec("ALTER TABLE factura ADD COLUMN facturar_a_telefono VARCHAR(50) DEFAULT ''")
            exec("ALTER TABLE factura ADD COLUMN cliente_sucursal_id VARCHAR(36)")
            // Venezuela persisted after HKA issuance; raw SELECT reads it directly.
            exec("ALTER TABLE factura ADD COLUMN numero_control_thka VARCHAR(50)")
            // Auxiliary tables LEFT JOINed by the print-payload query. Empty is fine for the
            // discount aggregation path under test.
            exec("CREATE TABLE IF NOT EXISTS parametros_generales (id INT, rif VARCHAR(50))")
            exec("CREATE TABLE IF NOT EXISTS caja (id VARCHAR(36), descripcion VARCHAR(100), codigo VARCHAR(40))")
            exec(
                "CREATE TABLE IF NOT EXISTS sucursal (id INT, sucursal VARCHAR(100), descripcion VARCHAR(255), codigo_sucursal_emisor VARCHAR(40))",
            )
            exec("CREATE TABLE IF NOT EXISTS cliente_sucursal (sucursal_id INT, direccion VARCHAR(255), nombre_sucursal VARCHAR(255))")
            exec("CREATE TABLE IF NOT EXISTS caja_nueva (caja_id VARCHAR(36), id_factura VARCHAR(36))")
            exec(
                "CREATE TABLE IF NOT EXISTS caja_nueva_detalle (caja_detalle_id INT, caja_id VARCHAR(36), id_forma_pago INT, monto DECIMAL(20,2))",
            )
            exec("CREATE TABLE IF NOT EXISTS caja_forma_pago (id_forma_pago INT, descripcion VARCHAR(100))")
            exec("CREATE TABLE IF NOT EXISTS factura_detalle_formapago (id_factura VARCHAR(36), totalizar_cambio DECIMAL(20,2))")
            seedClient()
            seedEstatus()
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(FacturasTableVE, FacturasClientesTable, EstatusTable, SalesFacturaDetalleTable)
        }
    }

    @Test
    fun aggregate_item_discounts_into_print_payload() =
        runBlocking {
            val invoice = invoiceId()
            seedFactura(invoice, subtotal = "100.00", baseImponible = "100.00", tax = "16.00", total = "116.00")
            seedDetail(invoice, montoDescuento = "2.50")
            seedDetail(invoice, montoDescuento = "0.75")
            seedDetail(invoice, montoDescuento = "0.00")

            val payload = repository.getPrintPayload(database, "VE", invoice, "TEST COMPANY")

            assertNotNull(payload)
            // 2.50 + 0.75 + 0.00 = 3.25 — exactly, with no IEEE-754 residue.
            assertEquals("3.25", payload.descuento)
        }

    @Test
    fun print_payload_exposes_zero_discount_when_no_item_has_discount() =
        runBlocking {
            val invoice = invoiceId()
            seedFactura(invoice, subtotal = "50.00", baseImponible = "50.00", tax = "8.00", total = "58.00")
            seedDetail(invoice, montoDescuento = "0.00")

            val payload = repository.getPrintPayload(database, "VE", invoice, "TEST COMPANY")

            assertNotNull(payload)
            assertEquals("0.00", payload.descuento)
        }

    @Test
    fun print_payload_discount_is_money_formatted_two_decimals() =
        runBlocking {
            val invoice = invoiceId()
            seedFactura(invoice, subtotal = "200.00", baseImponible = "200.00", tax = "32.00", total = "232.00")
            seedDetail(invoice, montoDescuento = "10.00")

            val payload = repository.getPrintPayload(database, "VE", invoice, "TEST COMPANY")

            assertNotNull(payload)
            // Always two decimals so the receipt line aligns with Subtotal / Impuesto / Total.
            assertTrue(payload.descuento!!.matches(Regex("""\d+\.\d{2}""")))
        }

    private fun seedClient() {
        FacturasClientesTable.insert {
            it[idCliente] = "cliente-1"
            it[nombre] = "Alice"
            it[apellido] = null
            it[rif] = "V-111"
            it[codCliente] = "C-1"
        }
    }

    private fun seedEstatus() {
        EstatusTable.insert {
            it[codEstatus] = 1
            it[descripcion] = "Pagada"
        }
    }

    private fun seedFactura(
        id: String,
        subtotal: String,
        baseImponible: String,
        tax: String,
        total: String,
    ) {
        transaction(database) {
            FacturasTableVE.insert {
                it[idFactura] = id
                it[codFactura] = "F1"
                it[codFacturaFiscal] = "CF1"
                it[numeroDocumentoFiscal] = null
                it[idCliente] = "cliente-1"
                it[codVendedor] = 1
                it[codEstatus] = 1
                it[idSucursal] = 1
                it[idCaja] = "caja-1"
                it[fechaFactura] = "2026-01-01"
                it[fechaCreacion] = "2026-01-01 10:00:00"
                it[totalTotalFactura] = total.toBigDecimal()
                it[totalizarTotalGeneral] = total.toBigDecimal()
                it[formaPago] = "contado"
                it[tipoFactura] = "VENTA"
                it[usuarioCreacion] = "alice"
                it[abrMonedaBase] = "USD"
                it[abrMonedaSecundaria] = null
                it[tasa] = 1.0f
                it[totalRef] = total.toFloat()
                it[impresoraSerial] = null
            }
            // Mirror the legacy string-typed columns read by the raw SQL in getPrintPayload.
            exec(
                """
                UPDATE factura
                SET subtotal = $subtotal,
                    totalizar_base_imponible = $baseImponible,
                    totalizar_monto_iva = $tax,
                    TotalTotalFactura = $total,
                    totalizar_total_general = $total
                WHERE id_factura = '$id'
                """.trimIndent(),
            )
        }
    }

    private fun seedDetail(
        facturaId: String,
        montoDescuento: String,
    ) {
        transaction(database) {
            SalesFacturaDetalleTable.insert {
                it[idDetalleFactura] = UUID.randomUUID().toString()
                it[idFactura] = facturaId
                it[idItem] = 1
                it[itemAlmacen] = 1
                it[itemDescripcion] = "Item"
                it[itemCantidad] = BigDecimal.ONE
                it[itemPrecioSinIva] = BigDecimal("10.00")
                it[itemDescuento] = BigDecimal.ZERO
                it[itemMontoDescuento] = montoDescuento.toBigDecimal()
                it[itemPiva] = BigDecimal("16.00")
                it[itemTotalSinIva] = BigDecimal("10.00")
                it[itemTotalConIva] = BigDecimal("11.60")
                it[poseeSerial] = "NO"
                it[serialesSeleccionados] = ""
                it[usuarioCreacion] = "alice"
                it[itemListaPrecio] = "1"
                it[itemUnidadEmpaque] = "UND"
                it[itemCantidadTotal] = BigDecimal.ONE
                it[promocionId] = ""
                it[promocionTipo] = ""
                it[promocionCodigo] = ""
                it[promocionNombre] = ""
                it[promocionGrupo] = ""
                it[promocionDetalleId] = ""
                it[promocionCantidad] = BigDecimal.ZERO
                it[grupo] = 1
                it[descuentoAutorizacion] = ""
                it[codVendedor] = 1
                it[itemCodigo] = "P0001"
                it[itemReferencia] = ""
            }
        }
    }

    private fun invoiceId(): String = UUID.randomUUID().toString()
}
