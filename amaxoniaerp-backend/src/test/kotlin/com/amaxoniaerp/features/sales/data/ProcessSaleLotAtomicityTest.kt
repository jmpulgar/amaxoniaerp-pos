package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SaleLotInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ProcessSaleLotAtomicityTest {
    private lateinit var database: Database
    private val repository = ProcessSaleTransactionalRepository()

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) {
            transaction(database) {
                SchemaUtils.drop(
                    SalesCajaNuevaDetalleFormaPagoTable,
                    SalesCajaNuevaReciboTablePA,
                    SalesCajaNuevaDetalleTablePA,
                    SalesCajaNuevaTablePA,
                    SalesFacturaDetalleFormaPagoTablePA,
                    SalesFacturaDetalleTable,
                    SalesFacturaTablePA,
                    SalesKardexDetalleTablePA,
                    SalesKardexTablePA,
                    FacturaDetalleProductoLoteTable,
                    ItemLoteTable,
                    SalesStockTable,
                    SalesCajaSecuenciaTable,
                    SalesCajaTable,
                    ParametrosGeneralesTablePA,
                    ClientSucursalTable,
                )
            }
        }
    }

    @Test
    fun `lote suficiente se descuenta una sola vez`() {
        database = createDatabase(available = 2)

        process(request(idFactura = "invoice-sufficient", lotQuantity = 2))

        val lot = lotRow()
        assertEquals(BigDecimal.ZERO.setScale(2), lot[ItemLoteTable.disponibilidad])
        assertEquals(BigDecimal("2.00"), lot[ItemLoteTable.venta])
        assertEquals(1L, transaction(database) { FacturaDetalleProductoLoteTable.selectAll().count() })
    }

    @Test
    fun `lote insuficiente revierte factura detalle y trazabilidad`() {
        database = createDatabase(available = 1)

        assertFailsWith<InsufficientStockException> {
            process(request(idFactura = "invoice-insufficient", lotQuantity = 2))
        }

        transaction(database) {
            assertEquals(0L, SalesFacturaTablePA.selectAll().count())
            assertEquals(0L, SalesFacturaDetalleTable.selectAll().count())
            assertEquals(0L, FacturaDetalleProductoLoteTable.selectAll().count())
            assertEquals(BigDecimal("1.00"), lotRow()[ItemLoteTable.disponibilidad])
            assertEquals(BigDecimal.ZERO.setScale(2), lotRow()[ItemLoteTable.venta])
        }
    }

    @Test
    fun `dos intentos sobre disponibilidad limite solo permiten uno`() {
        database = createDatabase(available = 1)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures =
            listOf("invoice-race-a", "invoice-race-b").map { idFactura ->
                executor.submit<LotAttempt> {
                    start.await()
                    runCatching {
                        transaction(database) {
                            repository.process("PA", request(idFactura = idFactura, lotQuantity = 1))
                        }
                    }.fold(
                        onSuccess = { LotAttempt.Success },
                        onFailure = { error -> LotAttempt.Failure(error) },
                    )
                }
            }

        start.countDown()
        val results = futures.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it is LotAttempt.Success })
        val failures = results.filterIsInstance<LotAttempt.Failure>()
        assertEquals(1, failures.size)
        assertTrue(failures.single().error is InsufficientStockException)
        transaction(database) {
            assertEquals(BigDecimal.ZERO.setScale(2), lotRow()[ItemLoteTable.disponibilidad])
            assertEquals(1L, SalesFacturaTablePA.selectAll().count())
        }
    }

    private fun createDatabase(available: Int): Database {
        val db = Database.connect(
            "jdbc:h2:mem:sales_lots_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(
                ParametrosGeneralesTablePA,
                ClientSucursalTable,
                SalesCajaTable,
                SalesCajaSecuenciaTable,
                SalesFacturaTablePA,
                SalesFacturaDetalleTable,
                SalesFacturaDetalleFormaPagoTablePA,
                SalesCajaNuevaTablePA,
                SalesCajaNuevaDetalleTablePA,
                SalesCajaNuevaDetalleFormaPagoTable,
                SalesCajaNuevaReciboTablePA,
                SalesStockTable,
                ItemLoteTable,
                FacturaDetalleProductoLoteTable,
                SalesKardexTablePA,
                SalesKardexDetalleTablePA,
            )
            ParametrosGeneralesTablePA.insert {
                it[codEmpresa] = 1
                it[defaultCodClienteFactura] = ""
                it[defaultIdFormaPagoFactura] = 1
                it[porcentajeImpuestoPrincipal] = BigDecimal.ZERO.setScale(2)
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
            SalesCajaTable.insert {
                it[id] = "caja-1"
                it[idSucursal] = null
                it[codAlmacen] = 1
                it[codigo] = "CJ01"
                it[facturaCorrelativo] = 0
            }
            SalesCajaSecuenciaTable.insert {
                it[id] = "seq-1"
                it[secuencia] = "0001"
            }
            SalesStockTable.insert {
                it[codAlmacen] = 1
                it[idItem] = 1
                it[cantidad] = 100f
                it[cantidadMuestra] = BigDecimal.ZERO.setScale(4)
                it[minimo] = 0L
                it[maximo] = 0L
            }
            ItemLoteTable.insert {
                it[idLoteItem] = 1
                it[codAlmacen] = 1
                it[idItem] = 1
                it[codigoLoteItem] = "LOT-1"
                it[vencimiento] = null
                it[ItemLoteTable.disponibilidad] = BigDecimal.valueOf(available.toLong()).setScale(2)
                it[procesamiento] = BigDecimal.ZERO.setScale(2)
                it[venta] = BigDecimal.ZERO.setScale(2)
            }
        }
        return db.also { database = it }
    }

    private fun process(request: ProcessSaleRequest) {
        transaction(database) {
            repository.process("PA", request)
        }
    }

    private fun request(
        idFactura: String,
        lotQuantity: Int,
    ): ProcessSaleRequest =
        ProcessSaleRequest(
            idFactura = idFactura,
            factura = SaleInvoiceInput(
                idCliente = "client-1",
                codCliente = "C001",
                codVendedor = 1,
                idShop = 1,
                idSucursal = 1,
                idCaja = "caja-1",
                codigoCaja = "CJ01",
                idCajaSecuencia = "seq-1",
                serieSucursal = "S01",
                formaPago = "contado",
                subtotal = 10.0,
                ivaTotalFactura = 0.0,
                totalTotalFactura = 10.0,
                montoItemsFactura = 10.0,
                totalizarSubTotal = 10.0,
                totalizarTotalOperacion = 10.0,
                totalizarBaseImponible = 10.0,
                totalizarMontoIva = 0.0,
                totalizarTotalGeneral = 10.0,
                usuarioCreacion = "TEST",
            ),
            items = listOf(
                SaleItemInput(
                    idItem = 1,
                    itemAlmacen = 1,
                    itemDescripcion = "ITEM LOT TEST",
                    itemCantidad = 1.0,
                    itemPrecioSinIva = 10.0,
                    itemPIva = 0.0,
                    itemTotalSinIva = 10.0,
                    itemTotalConIva = 10.0,
                    itemCantidadTotal = 1.0,
                    esProductoFisico = true,
                    poseeConfiguracionLote = "si",
                    codigosLote = listOf(
                        SaleLotInput(
                            idLoteItem = 1,
                            codigoLoteItem = "LOT-1",
                            cantidad = lotQuantity,
                            idAlmacen = 1,
                        ),
                    ),
                ),
            ),
            pagoResumen = SalePaymentSummaryInput(
                totalizarMontoCancelar = 10.0,
                totalizarMontoEfectivo = 10.0,
                totalizarCambio = 0.0,
                totalizarSaldoPendiente = 0.0,
            ),
            pagos = listOf(
                SalePaymentInput(
                    idFormaPago = 1,
                    tipoMovimiento = "CASH",
                    monto = 10.0,
                    montoRecibido = 10.0,
                ),
            ),
            moneda = SaleCurrencyInput(),
        )

    private fun lotRow() = transaction(database) {
        ItemLoteTable.selectAll().single()
    }

    private sealed interface LotAttempt {
        data object Success : LotAttempt

        data class Failure(val error: Throwable) : LotAttempt
    }
}
