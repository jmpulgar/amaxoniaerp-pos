package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.mapRowToProductSync
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sync.domain.clientContentHash
import com.amaxoniaerp.features.sync.domain.productContentHash
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRowMappersTest {
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:sync_mappers_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
    }

    @AfterTest
    fun tearDown() {
        transaction(database) { SchemaUtils.drop(ItemsTableFactory.getTableForCountry("VE")) }
    }

    @Test
    fun `mapea item VE a dto slim reutilizando la derivacion de precios`() {
        val table = ItemsTableFactory.getTableForCountry("VE")
        val itemId = 123
        val departmentId = 3
        val brandId = 7
        val expectedTaxRate = 7.0
        val expectedPrice = 0.55
        val expectedWithTax = 0.59
        val expectedUnitPrice = 0.03
        val percentBase = 100.0
        val expectedUnitWithTax = expectedUnitPrice * (1.0 + expectedTaxRate / percentBase)
        transaction(database) {
            SchemaUtils.create(table)
            table.insert {
                it[idItem] = itemId
                it[codItem] = "A123"
                it[descripcion1] = "AGUA MINERAL 600ML"
                it[codigoBarras] = "7450000123456"
                it[codDepartamento] = departmentId
                it[departamentoId] = 0
                it[iva] = java.math.BigDecimal("7.00")
                it[montoExento] = false
                it[marcaId] = brandId
                it[costoActual] = java.math.BigDecimal("0.35")
                it[cantidadBulto] = null
                it[unidadOEmpaque] = null
                it[estatus] = "A"
                it[precio1] = java.math.BigDecimal("0.55")
                it[utilidad1] = java.math.BigDecimal("20.00")
                it[coniva1] = java.math.BigDecimal("0.59")
                it[descuento1] = java.math.BigDecimal("0.00")
                it[precio1Extra] = java.math.BigDecimal("0.03")
            }
        }

        val dto =
            transaction(database) {
                table
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapRowToProductSync(it, "VE") }
            }

        assertEquals(itemId.toString(), dto.id)
        assertEquals("A123", dto.code)
        assertEquals("AGUA MINERAL 600ML", dto.description)
        assertEquals(departmentId, dto.department)
        assertEquals("A", dto.estatus)
        assertEquals(1.0, dto.bulkQuantity)
        assertEquals("UNIDAD", dto.unitOrPackage)
        assertEquals(expectedTaxRate, dto.taxRate)
        assertEquals(5, dto.prices.size)
        val levelA = dto.prices.first()
        assertEquals("A", levelA.label)
        assertEquals(expectedPrice, levelA.price)
        assertEquals(expectedWithTax, levelA.pricePlusTax)
        assertEquals(expectedUnitPrice, levelA.unitPrice)
        assertEquals(expectedUnitWithTax, levelA.unitPricePlusTax)
        assertTrue(productContentHash(dto) != 0L)
    }

    @Test
    fun `item exento usa el precio sin iva como pricePlusTax`() {
        val table = ItemsTableFactory.getTableForCountry("VE")
        val itemId = 200
        transaction(database) {
            SchemaUtils.create(table)
            table.insert {
                it[idItem] = itemId
                it[codItem] = "EXENTO"
                it[descripcion1] = "MEDICINA"
                it[marcaId] = 7
                it[iva] = java.math.BigDecimal("0.00")
                it[montoExento] = true
                it[estatus] = "A"
                it[precio1] = java.math.BigDecimal("1.00")
                it[utilidad1] = java.math.BigDecimal("0.00")
                it[coniva1] = java.math.BigDecimal("1.00")
                it[descuento1] = java.math.BigDecimal("0.00")
            }
        }

        val dto =
            transaction(database) {
                table
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapRowToProductSync(it, "VE") }
            }

        assertTrue(dto.isExempt)
        val expectedPricePlusTax = 1.00
        assertEquals(expectedPricePlusTax, dto.prices.first().pricePlusTax)
    }

    @Test
    fun `mapea cliente con sucursal propietaria y estado activo`() {
        val sucursalId = 12
        transaction(database) {
            SchemaUtils.create(ClientsTable)
            ClientsTable.insert {
                it[idCliente] = "CLI-1"
                it[codCliente] = "CLI-000001"
                it[rif] = "J-12345678-9"
                it[nombre] = "COMERCIAL LA ESQUINA"
                it[estado] = "1"
                it[permiteCredito] = true
                it[limite] = 5000.0
                it[dias] = 15
                it[idSucursal] = sucursalId
            }
        }

        val dto =
            transaction(database) {
                ClientsTable
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapClientSync(it) }
            }

        assertEquals("CLI-1", dto.id)
        assertTrue(dto.activo)
        assertTrue(dto.permiteCredito)
        assertEquals(sucursalId, dto.idSucursal)
        assertTrue(clientContentHash(dto) != 0L)
    }

    @Test
    fun `cliente con estado inactivo mapea activo false`() {
        transaction(database) {
            SchemaUtils.create(ClientsTable)
            ClientsTable.insert {
                it[idCliente] = "CLI-2"
                it[codCliente] = "CLI-000002"
                it[rif] = "V-87654321-0"
                it[nombre] = "INACTIVO C.A."
                it[estado] = "0"
                it[idSucursal] = null
            }
        }

        val dto =
            transaction(database) {
                ClientsTable
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapClientSync(it) }
            }

        assertFalse(dto.activo)
        assertEquals(null, dto.idSucursal)
    }

    @Test
    fun `mapea sucursal de cliente y formas de pago`() {
        val branchId = 9
        val formaPagoId = 1
        transaction(database) {
            SchemaUtils.create(ClientSucursalTable, CajaFormaPagoTable, CajaFormaTable)
            ClientSucursalTable.insert {
                it[sucursalId] = branchId
                it[clienteCodigo] = "CLI-00001"
                it[nombreSucursal] = "SUCURSAL ESTE"
            }
            CajaFormaPagoTable.insert {
                it[idFormaPago] = formaPagoId
                it[siglas] = "EF"
                it[descripcion] = "EFECTIVO"
                it[activo] = 1
                it[pos] = 1
            }
            CajaFormaTable.insert {
                it[idCaja] = "CAJA-1"
                it[idFormaPago] = formaPagoId
                it[activo] = 1
            }
        }

        transaction(database) {
            val branch =
                ClientSucursalTable
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapClientBranchSync(it) }
            assertEquals(branchId, branch.sucursalId)
            assertEquals("CLI-00001", branch.clienteCodigo)

            val payment =
                CajaFormaPagoTable
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapPaymentMethodSync(it) }
            assertEquals(formaPagoId, payment.idFormaPago)
            assertEquals("EFECTIVO", payment.descripcion)

            val cajaPayment =
                CajaFormaTable
                    .selectAll()
                    .limit(1)
                    .single()
                    .let { mapCajaPaymentMethodSync(it) }
            assertEquals("CAJA-1", cajaPayment.idCaja)
            assertEquals(formaPagoId, cajaPayment.idFormaPago)
        }
    }
}
