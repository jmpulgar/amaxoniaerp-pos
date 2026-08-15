package com.amaxoniaerp.features.facturas.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class FacturasRepositoryFilterTest {
    @Test
    fun searchMatchesInvoiceCode() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(search = "INV-001"))

            assertEquals(1L, total)
            assertEquals(listOf("factura-1"), facturas.map { it.id })
        }

    @Test
    fun usuarioFiltersInvoiceCreator() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(usuario = "alice"))

            assertEquals(2L, total)
            assertEquals(listOf("factura-3", "factura-1"), facturas.map { it.id })
        }

    @Test
    fun sucursalFiltersInvoiceBranch() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(sucursalId = 2))

            assertEquals(1L, total)
            assertEquals(listOf("factura-2"), facturas.map { it.id })
        }

    @Test
    fun onlyStartDateIncludesThatDayAndLater() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(fechaInicio = java.time.LocalDate.of(2026, 1, 2)))

            assertEquals(2L, total)
            assertEquals(listOf("factura-3", "factura-2"), facturas.map { it.id })
        }

    @Test
    fun onlyEndDateIncludesTheWholeDay() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(fechaFin = java.time.LocalDate.of(2026, 1, 2)))

            assertEquals(2L, total)
            assertEquals(listOf("factura-2", "factura-1"), facturas.map { it.id })
        }

    @Test
    fun dateRangeUsesInclusiveStartAndEnd() =
        withSeededDatabase {
            val (facturas, total) =
                list(
                    FacturasFilter(
                        fechaInicio = java.time.LocalDate.of(2026, 1, 2),
                        fechaFin = java.time.LocalDate.of(2026, 1, 3),
                    ),
                )

            assertEquals(2L, total)
            assertEquals(listOf("factura-3", "factura-2"), facturas.map { it.id })
        }

    @Test
    fun combinedFiltersNarrowTheSameInvoiceUniverse() =
        withSeededDatabase {
            val filter =
                FacturasFilter(
                    usuario = "alice",
                    sucursalId = 1,
                    fechaInicio = java.time.LocalDate.of(2026, 1, 3),
                    fechaFin = java.time.LocalDate.of(2026, 1, 3),
                )

            val (facturas, total) = list(filter)
            val resumen = repository.getResumen(database, "VE", filter)

            assertEquals(1L, total)
            assertEquals(listOf("factura-3"), facturas.map { it.id })
            assertEquals(facturas.size, resumen.totalFacturas)
        }

    @Test
    fun summaryAndListUseTheSameFilteredUniverse() =
        withSeededDatabase {
            val filter = FacturasFilter(usuario = "alice", sucursalId = 1)
            val (facturas, total) = list(filter)
            val resumen = repository.getResumen(database, "VE", filter)

            assertEquals(total.toInt(), facturas.size)
            assertEquals(facturas.size, resumen.totalFacturas)
            assertEquals(facturas.sumOf { it.total }, resumen.ventasNetas)
        }

    private val repository = FacturasRepository()
    private lateinit var database: Database

    private suspend fun list(filter: FacturasFilter) =
        repository.listFacturas(
            database = database,
            countryCode = "VE",
            limit = 100,
            offset = 0,
            filter = filter,
        )

    private fun withSeededDatabase(block: suspend FacturasRepositoryFilterTest.() -> Unit) {
        database =
            Database.connect(
                url = "jdbc:h2:mem:facturas_filter_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(FacturasTableVE, FacturasClientesTable, EstatusTable)
            seedData()
        }

        try {
            runBlocking { block() }
        } finally {
            transaction(database) {
                SchemaUtils.drop(FacturasTableVE, FacturasClientesTable, EstatusTable)
            }
        }
    }

    private fun seedData() {
        FacturasClientesTable.insert {
            it[idCliente] = "cliente-1"
            it[nombre] = "Alice"
            it[apellido] = null
            it[rif] = "V-111"
            it[codCliente] = "C-1"
        }
        FacturasClientesTable.insert {
            it[idCliente] = "cliente-2"
            it[nombre] = "Bob"
            it[apellido] = null
            it[rif] = "V-222"
            it[codCliente] = "C-2"
        }
        EstatusTable.insert {
            it[codEstatus] = 1
            it[descripcion] = "Pagada"
        }

        insertFactura(
            id = "factura-1",
            code = "INV-001",
            clientId = "cliente-1",
            user = "alice",
            branchId = 1,
            createdAt = "2026-01-01 10:00:00",
            total = 100.0,
        )
        insertFactura(
            id = "factura-2",
            code = "INV-002",
            clientId = "cliente-2",
            user = "bob",
            branchId = 2,
            createdAt = "2026-01-02 23:59:59",
            total = 200.0,
        )
        insertFactura(
            id = "factura-3",
            code = "INV-003",
            clientId = "cliente-1",
            user = "alice",
            branchId = 1,
            createdAt = "2026-01-03 12:00:00",
            total = 300.0,
        )
    }

    private fun insertFactura(
        id: String,
        code: String,
        clientId: String,
        user: String,
        branchId: Int,
        createdAt: String,
        total: Double,
    ) {
        FacturasTableVE.insert {
            it[idFactura] = id
            it[codFactura] = code
            it[codFacturaFiscal] = "CF-$code"
            it[numeroDocumentoFiscal] = null
            it[idCliente] = clientId
            it[codVendedor] = 1
            it[codEstatus] = 1
            it[idSucursal] = branchId
            it[idCaja] = "caja-1"
            it[fechaFactura] = createdAt.substringBefore(' ')
            it[fechaCreacion] = createdAt
            it[totalTotalFactura] = total.toBigDecimal()
            it[totalizarTotalGeneral] = total.toBigDecimal()
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = user
            it[abrMonedaBase] = "USD"
            it[abrMonedaSecundaria] = null
            it[tasa] = 1.0f
            it[totalRef] = total.toFloat()
            it[impresoraSerial] = null
        }
    }
}
