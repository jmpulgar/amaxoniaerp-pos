package com.amaxoniaerp.features.facturas.domain

import com.amaxoniaerp.features.facturas.data.BaseFacturasTable
import com.amaxoniaerp.features.facturas.data.EstatusTable
import com.amaxoniaerp.features.facturas.data.FacturasClientesTable
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import com.amaxoniaerp.features.facturas.data.FacturasTableVE
import com.amaxoniaerp.features.facturas.data.buildFacturasResumen
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Characterization tests for [buildFacturasResumen] establishing baseline Double
 * values before migrating to BigDecimal (TASK-140 / B1).
 */
class FacturasResumenCalculatorTest {
    @Test
    fun `empty rows returns all zero summary`() {
        withDatabase(FacturasTablePA) {
            val rows = queryRows(FacturasTablePA)
            val resumen = buildFacturasResumen(rows, FacturasTablePA)

            assertEquals(0.0, resumen.ventasBrutas, "ventasBrutas")
            assertEquals(0.0, resumen.ventasNetas, "ventasNetas")
            assertEquals(0.0, resumen.descuentos, "descuentos")
            assertEquals(0.0, resumen.cancelaciones, "cancelaciones")
            assertEquals(0, resumen.totalFacturas, "totalFacturas")
            assertEquals(0, resumen.totalFacturasPagadas, "totalFacturasPagadas")
            assertEquals(0, resumen.totalFacturasAnuladas, "totalFacturasAnuladas")
            assertEquals(0.0, resumen.ticketPromedio, "ticketPromedio")
            assertEquals("USD", resumen.moneda, "moneda")
            assertNull(resumen.ventasBrutasRef, "ventasBrutasRef")
            assertNull(resumen.ventasNetasRef, "ventasNetasRef")
            assertNull(resumen.cancelacionesRef, "cancelacionesRef")
            assertNull(resumen.ticketPromedioRef, "ticketPromedioRef")
            assertNull(resumen.abrMonedaSecundaria, "abrMonedaSecundaria")
        }
    }

    @Test
    fun `single paid invoice without discount calculates totals and ticket promedio`() {
        withDatabase(FacturasTablePA) {
            transaction(database) {
                insertEstatus(1, "Pagada")
                insertPA("fac-1", "001", 1, BigDecimal("150.00"), BigDecimal("150.00"))
            }

            val rows = queryRows(FacturasTablePA)
            val resumen = buildFacturasResumen(rows, FacturasTablePA)

            assertEquals(150.0, resumen.ventasBrutas, "ventasBrutas")
            assertEquals(150.0, resumen.ventasNetas, "ventasNetas")
            assertEquals(0.0, resumen.descuentos, "descuentos")
            assertEquals(0.0, resumen.cancelaciones, "cancelaciones")
            assertEquals(1, resumen.totalFacturas, "totalFacturas")
            assertEquals(1, resumen.totalFacturasPagadas, "totalFacturasPagadas")
            assertEquals(0, resumen.totalFacturasAnuladas, "totalFacturasAnuladas")
            assertEquals(150.0, resumen.ticketPromedio, "ticketPromedio")
        }
    }

    @Test
    fun `invoices with discounts and cancellations calculate aggregates correctly`() {
        withDatabase(FacturasTablePA) {
            transaction(database) {
                insertEstatus(1, "Pagada")
                insertEstatus(2, "Anulada")
                insertPA("fac-1", "001", 1, BigDecimal("180.00"), BigDecimal("200.00"))
                insertPA("fac-2", "002", 2, BigDecimal("50.00"), BigDecimal("50.00"))
            }

            val rows = queryRows(FacturasTablePA)
            val resumen = buildFacturasResumen(rows, FacturasTablePA)

            assertEquals(200.0, resumen.ventasBrutas, "ventasBrutas")
            assertEquals(180.0, resumen.ventasNetas, "ventasNetas")
            assertEquals(20.0, resumen.descuentos, "descuentos")
            assertEquals(50.0, resumen.cancelaciones, "cancelaciones")
            assertEquals(2, resumen.totalFacturas, "totalFacturas")
            assertEquals(1, resumen.totalFacturasPagadas, "totalFacturasPagadas")
            assertEquals(1, resumen.totalFacturasAnuladas, "totalFacturasAnuladas")
            assertEquals(180.0, resumen.ticketPromedio, "ticketPromedio")
        }
    }

    @Test
    fun `multi currency VE invoices accumulate secondary reference amounts`() {
        withDatabase(FacturasTableVE) {
            transaction(database) {
                insertEstatus(1, "Pagada")
                insertEstatus(2, "Anulado")
                insertVE("fac-ve-1", "001", 1, BigDecimal("100.00"), 4000.0f)
                insertVE("fac-ve-2", "002", 2, BigDecimal("20.00"), 800.0f)
            }

            val rows = queryRows(FacturasTableVE)
            val resumen = buildFacturasResumen(rows, FacturasTableVE)

            assertEquals(100.0, resumen.ventasBrutas, "ventasBrutas")
            assertEquals(100.0, resumen.ventasNetas, "ventasNetas")
            assertEquals(20.0, resumen.cancelaciones, "cancelaciones")
            assertEquals(4000.0, resumen.ventasBrutasRef, "ventasBrutasRef")
            assertEquals(4000.0, resumen.ventasNetasRef, "ventasNetasRef")
            assertEquals(800.0, resumen.cancelacionesRef, "cancelacionesRef")
            assertEquals(4000.0, resumen.ticketPromedioRef, "ticketPromedioRef")
            assertEquals("VES", resumen.abrMonedaSecundaria, "abrMonedaSecundaria")
        }
    }

    private lateinit var database: Database

    private fun queryRows(table: BaseFacturasTable) =
        transaction(database) {
            table
                .join(EstatusTable, JoinType.LEFT, table.codEstatus, EstatusTable.codEstatus)
                .selectAll()
                .toList()
        }

    private fun withDatabase(
        table: BaseFacturasTable,
        block: () -> Unit,
    ) {
        val databaseName = UUID.randomUUID().toString().replace("-", "")
        database =
            Database.connect(
                url = "jdbc:h2:mem:facturas_resumen_$databaseName;MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(table, FacturasClientesTable, EstatusTable)
        }
        try {
            block()
        } finally {
            transaction(database) {
                SchemaUtils.drop(table, FacturasClientesTable, EstatusTable)
            }
        }
    }

    private fun insertEstatus(
        code: Int,
        desc: String,
    ) {
        EstatusTable.insert {
            it[codEstatus] = code
            it[descripcion] = desc
        }
    }

    private fun insertPA(
        id: String,
        code: String,
        status: Int,
        total: BigDecimal,
        general: BigDecimal,
    ) {
        FacturasTablePA.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = "CF-$code"
            it[idCliente] = "cli-1"
            it[codVendedor] = 1
            it[codEstatus] = status
            it[idSucursal] = 1
            it[idCaja] = "caja-1"
            it[fechaFactura] = "2026-01-01"
            it[fechaCreacion] = "2026-01-01 10:00:00"
            it[totalTotalFactura] = total
            it[totalizarTotalGeneral] = general
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
        }
    }

    private fun insertVE(
        id: String,
        code: String,
        status: Int,
        total: BigDecimal,
        ref: Float,
    ) {
        FacturasTableVE.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = "CF-$code"
            it[idCliente] = "cli-1"
            it[codVendedor] = 1
            it[codEstatus] = status
            it[idSucursal] = 1
            it[idCaja] = "caja-1"
            it[fechaFactura] = "2026-01-01"
            it[fechaCreacion] = "2026-01-01 10:00:00"
            it[totalTotalFactura] = total
            it[totalizarTotalGeneral] = total
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = "admin"
            it[abrMonedaBase] = "USD"
            it[abrMonedaSecundaria] = "VES"
            it[tasa] = 40.0f
            it[totalRef] = ref
        }
    }
}
