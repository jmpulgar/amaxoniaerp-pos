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
    fun cajaFiltersInvoiceCashRegister() =
        withSeededDatabase {
            val (facturas, total) = list(FacturasFilter(cajaId = "caja-2"))

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
                    cajaId = "caja-1",
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
            val filter = FacturasFilter(usuario = "alice", cajaId = "caja-1")
            val (facturas, total) = list(filter)
            val resumen = repository.getResumen(database, "VE", filter)

            assertEquals(total.toInt(), facturas.size)
            assertEquals(facturas.size, resumen.totalFacturas)
            assertEquals(facturas.sumOf { it.total }, resumen.ventasNetas)
        }

    @Test
    fun `orphan invoice without client in clientes table maps successfully with facturar_a fallback and search matches`() =
        withSeededDatabase {
            transaction(database) {
                insertFactura(
                    facturaSeed {
                        id = "factura-orphan"
                        code = "INV-099"
                        clientId = "cliente-inexistente"
                        user = "cajero"
                        cajaId = "caja-1"
                        createdAt = "2026-01-04 10:00:00"
                        total = 150.0
                        facturarA = "Juan Perez Offline"
                        facturarARuc = "8-123-456"
                    },
                )
            }

            val (facturas, total) = list(FacturasFilter(search = "Juan Perez"))
            assertEquals(1L, total)
            val orphanSummary = facturas.first()
            assertEquals("factura-orphan", orphanSummary.id)
            assertEquals("JUAN PEREZ OFFLINE", orphanSummary.clienteNombre)
            assertEquals("8-123-456", orphanSummary.clienteIdentificacion)

            val (byRuc, totalRuc) = list(FacturasFilter(search = "8-123-456"))
            assertEquals(1L, totalRuc)
            assertEquals("factura-orphan", byRuc.first().id)
        }

    @Test
    fun `panama schema does not require or query numero_control_thka`() {
        val databaseName = UUID.randomUUID().toString().replace("-", "")
        val panamaDb =
            Database.connect(
                url = "jdbc:h2:mem:facturas_pa_$databaseName;MODE=MySQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        transaction(panamaDb) {
            SchemaUtils.create(FacturasTablePA, FacturasClientesTable, EstatusTable)
            FacturasClientesTable.insert {
                it[idCliente] = "cliente-pa"
                it[nombre] = "Empresa Panamá"
                it[apellido] = null
                it[rif] = "155123456"
                it[codCliente] = "C-PA"
            }
            EstatusTable.insert {
                it[codEstatus] = 1
                it[descripcion] = "Pagada"
            }
            FacturasTablePA.insert {
                it[idFactura] = "factura-pa-1"
                it[codFactura] = "INV-PA-001"
                it[codFacturaFiscal] = "CF-PA-001"
                it[numeroDocumentoFiscal] = null
                it[idCliente] = "cliente-pa"
                it[codVendedor] = 1
                it[codEstatus] = 1
                it[idSucursal] = 1
                it[idCaja] = "caja-pa"
                it[fechaFactura] = "2026-01-01"
                it[fechaCreacion] = "2026-01-01 10:00:00"
                it[totalTotalFactura] = 150.0.toBigDecimal()
                it[totalizarTotalGeneral] = 150.0.toBigDecimal()
                it[formaPago] = "contado"
                it[tipoFactura] = "VENTA"
                it[usuarioCreacion] = "admin"
                it[cufe] = "CUFE-12345"
            }
        }

        try {
            runBlocking {
                val (facturas, total) =
                    repository.listFacturas(
                        database = panamaDb,
                        countryCode = "PA",
                        limit = 100,
                        offset = 0,
                        filter = FacturasFilter(),
                    )
                assertEquals(1L, total)
                assertEquals("factura-pa-1", facturas.first().id)
                assertEquals("CUFE-12345", facturas.first().codigoFiscal)

                val resumen = repository.getResumen(panamaDb, "PA", FacturasFilter())
                assertEquals(1, resumen.totalFacturas)
            }
        } finally {
            transaction(panamaDb) {
                SchemaUtils.drop(FacturasTablePA, FacturasClientesTable, EstatusTable)
            }
        }
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
        val databaseName = UUID.randomUUID().toString().replace("-", "")
        database =
            Database.connect(
                url = "jdbc:h2:mem:facturas_filter_$databaseName;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
            facturaSeed {
                id = "factura-1"
                code = "INV-001"
                clientId = "cliente-1"
                user = "alice"
                cajaId = "caja-1"
                createdAt = "2026-01-01 10:00:00"
                total = 100.0
            },
        )
        insertFactura(
            facturaSeed {
                id = "factura-2"
                code = "INV-002"
                clientId = "cliente-2"
                user = "bob"
                cajaId = "caja-2"
                createdAt = "2026-01-02 23:59:59"
                total = 200.0
            },
        )
        insertFactura(
            facturaSeed {
                id = "factura-3"
                code = "INV-003"
                clientId = "cliente-1"
                user = "alice"
                cajaId = "caja-1"
                createdAt = "2026-01-03 12:00:00"
                total = 300.0
            },
        )
    }

    private class FacturaSeed {
        var id: String = ""
        var code: String = ""
        var clientId: String = ""
        var user: String = ""
        var cajaId: String = "caja-1"
        var createdAt: String = ""
        var total: Double = 0.0
        var facturarA: String? = null
        var facturarARuc: String? = null
    }

    private fun facturaSeed(configure: FacturaSeed.() -> Unit): FacturaSeed = FacturaSeed().apply(configure)

    private fun insertFactura(seed: FacturaSeed) {
        FacturasTableVE.insert {
            it[idFactura] = seed.id
            it[codFactura] = seed.code
            it[codFacturaFiscal] = "CF-${seed.code}"
            it[numeroDocumentoFiscal] = null
            it[idCliente] = seed.clientId
            it[codVendedor] = 1
            it[codEstatus] = 1
            it[idSucursal] = 1
            it[idCaja] = seed.cajaId
            it[fechaFactura] = seed.createdAt.substringBefore(' ')
            it[fechaCreacion] = seed.createdAt
            it[totalTotalFactura] = seed.total.toBigDecimal()
            it[totalizarTotalGeneral] = seed.total.toBigDecimal()
            it[formaPago] = "contado"
            it[tipoFactura] = "VENTA"
            it[usuarioCreacion] = seed.user
            it[abrMonedaBase] = "USD"
            it[abrMonedaSecundaria] = null
            it[tasa] = 1.0f
            it[totalRef] = seed.total.toFloat()
            it[impresoraSerial] = null
            it[facturarA] = seed.facturarA
            it[facturarARuc] = seed.facturarARuc
        }
    }
}
