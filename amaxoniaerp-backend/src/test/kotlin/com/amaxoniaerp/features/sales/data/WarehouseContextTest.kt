package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WarehouseContextTest {
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:warehouse_context_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                SalesCajaTablePA,
                SalesSucursalTable,
                SalesSucursalAlmacenTable,
                ParametrosGeneralesTablePA,
            )

            SalesSucursalTable.insert {
                it[id] = 1
                it[serie] = "001"
            }

            SalesSucursalAlmacenTable.insert {
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[defaultVentas] = 1
            }

            ParametrosGeneralesTablePA.insert {
                it[codEmpresa] = 1
                it[defaultCodClienteFactura] = ""
                it[defaultIdFormaPagoFactura] = 1
                it[porcentajeImpuestoPrincipal] = BigDecimal.ZERO
                it[validarStock] = "NO"
                it[diasVencimiento] = 90
                it[codAlmacen] = 1
                it[abrMonedaBase] = "USD"
                it[monedaBase] = 1
                it[bloquearItbms] = "NO"
                it[facturarCero] = false
                it[impresionDirecta] = false
                it[tipoFacturacion] = 0
            }

            // Caja con id_almacenes = "3"
            SalesCajaTablePA.insert {
                it[id] = "caja-almacen-3"
                it[idSucursal] = 1
                it[codigo] = "CAJA03"
                it[facturaCorrelativo] = 0
                it[idAlmacenes] = "3"
            }

            // Caja con múltiples almacenes = "1,3"
            SalesCajaTablePA.insert {
                it[id] = "caja-multi-almacen"
                it[idSucursal] = 1
                it[codigo] = "CAJAMULTI"
                it[facturaCorrelativo] = 0
                it[idAlmacenes] = "1, 3"
            }

            // Caja sin almacenes explícitos (fallback a sucursal = 1)
            SalesCajaTablePA.insert {
                it[id] = "caja-sin-almacenes"
                it[idSucursal] = 1
                it[codigo] = "CAJADEF"
                it[facturaCorrelativo] = 0
                it[idAlmacenes] = null
            }
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                ParametrosGeneralesTablePA,
                SalesSucursalAlmacenTable,
                SalesSucursalTable,
                SalesCajaTablePA,
            )
        }
    }

    @Test
    fun `caja con id_almacenes 3 permite ventas del almacen 3 y resuelve default en 3`() {
        transaction(database) {
            val context = resolveWarehouseContext("PA", "caja-almacen-3")

            assertEquals(3, context.defaultWarehouseId)
            assertTrue(3 in context.allowedWarehouseIds, "El almacén 3 debe estar permitido para la caja")
            assertTrue(1 in context.allowedWarehouseIds, "El almacén de sucursal también debe estar permitido")

            val item =
                SaleItemInput(
                    idItem = 10,
                    codVendedor = 1,
                    itemAlmacen = 3,
                    itemDescripcion = "Producto de almacén 3",
                    itemCantidad = 1.0,
                    itemPrecioSinIva = 10.0,
                    itemPIva = 7.0,
                    itemTotalSinIva = 10.0,
                    itemTotalConIva = 10.7,
                    itemCantidadTotal = 1.0,
                    esProductoFisico = true,
                )

            // No debe lanzar excepción
            validateWarehouseOwnership(
                request = dummyRequest("caja-almacen-3"),
                normalizedItems = listOf(item),
                context = context,
            )
        }
    }

    @Test
    fun `caja con multiples almacenes permite items de cualquiera de ellos`() {
        transaction(database) {
            val context = resolveWarehouseContext("PA", "caja-multi-almacen")

            assertTrue(1 in context.allowedWarehouseIds)
            assertTrue(3 in context.allowedWarehouseIds)

            val items =
                listOf(
                    SaleItemInput(
                        idItem = 10,
                        codVendedor = 1,
                        itemAlmacen = 1,
                        itemDescripcion = "Item 1",
                        itemCantidad = 1.0,
                        itemPrecioSinIva = 5.0,
                        itemPIva = 7.0,
                        itemTotalSinIva = 5.0,
                        itemTotalConIva = 5.35,
                        itemCantidadTotal = 1.0,
                        esProductoFisico = true,
                    ),
                    SaleItemInput(
                        idItem = 20,
                        codVendedor = 1,
                        itemAlmacen = 3,
                        itemDescripcion = "Item 3",
                        itemCantidad = 1.0,
                        itemPrecioSinIva = 5.0,
                        itemPIva = 7.0,
                        itemTotalSinIva = 5.0,
                        itemTotalConIva = 5.35,
                        itemCantidadTotal = 1.0,
                        esProductoFisico = true,
                    ),
                )

            validateWarehouseOwnership(
                request = dummyRequest("caja-multi-almacen"),
                normalizedItems = items,
                context = context,
            )
        }
    }

    @Test
    fun `almacen no permitido lanza InvalidSaleRequestException con detalles exactos`() {
        transaction(database) {
            val context = resolveWarehouseContext("PA", "caja-almacen-3")

            val itemInvalido =
                SaleItemInput(
                    idItem = 99,
                    codVendedor = 1,
                    itemAlmacen = 99,
                    itemDescripcion = "Item de almacén ajeno",
                    itemCantidad = 1.0,
                    itemPrecioSinIva = 10.0,
                    itemPIva = 7.0,
                    itemTotalSinIva = 10.0,
                    itemTotalConIva = 10.7,
                    itemCantidadTotal = 1.0,
                    esProductoFisico = true,
                )

            val ex =
                assertFailsWith<InvalidSaleRequestException> {
                    validateWarehouseOwnership(
                        request = dummyRequest("caja-almacen-3"),
                        normalizedItems = listOf(itemInvalido),
                        context = context,
                    )
                }

            assertTrue(ex.message!!.contains("Almacen(es) no permitidos para caja=caja-almacen-3: 99"))
        }
    }

    @Test
    fun `servicios no son bloqueados por validacion de almacen fisico`() {
        transaction(database) {
            val context = resolveWarehouseContext("PA", "caja-almacen-3")

            val servicio =
                SaleItemInput(
                    idItem = 50,
                    codVendedor = 1,
                    itemAlmacen = 99, // Almacén arbitrario para un servicio
                    itemDescripcion = "Servicio de transporte",
                    itemCantidad = 1.0,
                    itemPrecioSinIva = 20.0,
                    itemPIva = 0.0,
                    itemTotalSinIva = 20.0,
                    itemTotalConIva = 20.0,
                    itemCantidadTotal = 1.0,
                    esProductoFisico = false, // Es servicio
                )

            validateWarehouseOwnership(
                request = dummyRequest("caja-almacen-3"),
                normalizedItems = listOf(servicio),
                context = context,
            )
        }
    }

    private fun dummyRequest(cajaId: String) =
        ProcessSaleRequest(
            idFactura = "inv-1",
            factura =
                SaleInvoiceInput(
                    idCliente = "cli-1",
                    codCliente = "C001",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = cajaId,
                    codigoCaja = "CAJA",
                    idCajaSecuencia = "seq-1",
                    serieSucursal = "001",
                    formaPago = "contado",
                    subtotal = 10.0,
                    ivaTotalFactura = 0.7,
                    totalTotalFactura = 10.7,
                    montoItemsFactura = 10.0,
                    totalizarBaseImponible = 10.0,
                    totalizarMontoIva = 0.7,
                    totalizarTotalGeneral = 10.7,
                    usuarioCreacion = "cajero",
                ),
            items = emptyList(),
            pagoResumen =
                com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput(
                    totalizarMontoCancelar = 10.7,
                    totalizarMontoEfectivo = 10.7,
                    totalizarCambio = 0.0,
                    totalizarSaldoPendiente = 0.0,
                ),
        )
}
