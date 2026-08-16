package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProcessSaleCreditTest {
    private lateinit var database: Database
    private var schemaCountry: String = ""
    private val repository = ProcessSaleTransactionalRepository()

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) {
            transaction(database) {
                if (schemaCountry == "PA") {
                    SchemaUtils.drop(
                        SalesCajaNuevaDetalleFormaPagoTable,
                        SalesCajaNuevaReciboTablePA,
                        SalesCajaNuevaDetalleTablePA,
                        SalesCajaNuevaTablePA,
                        SalesFacturaDetalleFormaPagoTablePA,
                        SalesFacturaDetalleTable,
                        SalesFacturaTablePA,
                        SalesCajaSecuenciaTable,
                        SalesCajaTable,
                        ParametrosGeneralesTablePA,
                        ClientSucursalTable,
                        ClientsTable,
                    )
                } else {
                    SchemaUtils.drop(
                        SalesCajaNuevaDetalleFormaPagoTable,
                        SalesCajaNuevaReciboTableVE,
                        SalesCajaNuevaDetalleTableVE,
                        SalesCajaNuevaTableVE,
                        SalesFacturaDetalleFormaPagoTableVE,
                        SalesFacturaDetalleTable,
                        SalesFacturaTableVE,
                        SalesCajaSecuenciaTable,
                        SalesCajaTable,
                        ParametrosGeneralesTableVE,
                        ClientsTable,
                    )
                }
            }
        }
    }

    @Test
    fun `contado completo queda contado y caja pagada`() {
        database = createDatabase("PA")

        process("PA", request(payments = listOf(cashPayment(100.0))))

        assertEquals("contado", invoiceFormaPago())
        assertEquals(CajaStatus.Pagada, cajaStatus())
    }

    @Test
    fun `CXC explicito activa credito y deja caja pendiente`() {
        database = createDatabase("PA")

        process("PA", request(payments = listOf(cxcPayment(100.0))))

        assertEquals("credito", invoiceFormaPago())
        assertEquals(CajaStatus.Pendiente, cajaStatus())
        assertEquals(100.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarMontoCxc].toDouble(), 0.0)
        assertEquals(
            100.0,
            paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarSaldoPendiente].toDouble(),
            0.0,
        )
    }

    @Test
    fun `forma credito con CXC y abono inicial usa el mismo resultado canonico`() {
        database = createDatabase("PA")

        process(
            "PA",
            request(
                formaPago = "credito",
                payments = listOf(cashPayment(50.0), cxcPayment(50.0)),
                saldoPendiente = 50.0,
            ),
        )

        assertEquals("credito", invoiceFormaPago())
        assertEquals(CajaStatus.Pendiente, cajaStatus())
        assertEquals(50.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarMontoCxc].toDouble(), 0.0)
        assertEquals(50.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarSaldoPendiente].toDouble(), 0.0)
    }

    @Test
    fun `saldo positivo activa credito aunque no haya CXC`() {
        database = createDatabase("PA")

        process("PA", request(payments = listOf(cashPayment(80.0)), saldoPendiente = 20.0))

        assertEquals("credito", invoiceFormaPago())
        assertEquals(CajaStatus.Pendiente, cajaStatus())
        assertEquals(0.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarMontoCxc].toDouble(), 0.0)
        assertEquals(20.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarSaldoPendiente].toDouble(), 0.0)
    }

    @Test
    fun `cliente que no permite credito es rechazado`() {
        database = createDatabase("PA")
        setClientCredit(permiteCredito = false, dias = 30)

        assertFailsWith<InvalidSaleRequestException> {
            process("PA", request(payments = listOf(cxcPayment(100.0))))
        }
        assertEquals(0L, transaction(database) { SalesFacturaTablePA.selectAll().count() })
    }

    @Test
    fun `dias cero vence hoy`() {
        database = createDatabase("PA")
        setClientCredit(permiteCredito = true, dias = 0)
        val today = BusinessClock.todayForCountry("PA")

        process("PA", request(payments = listOf(cxcPayment(100.0))))

        assertEquals(today, invoiceDueDate())
        assertEquals(today, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.fechaVencimiento])
    }

    @Test
    fun `dias treinta vence treinta dias despues`() {
        database = createDatabase("PA")
        setClientCredit(permiteCredito = true, dias = 30)
        val today = BusinessClock.todayForCountry("PA")

        process("PA", request(payments = listOf(cxcPayment(100.0))))

        assertEquals(today.plusDays(30), invoiceDueDate())
        assertEquals(today.plusDays(30), paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.fechaVencimiento])
    }

    @Test
    fun `CXC se conserva y no termina en OT`() {
        database = createDatabase("PA")

        process("PA", request(payments = listOf(cxcPayment(100.0))))

        assertEquals(100.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarMontoCxc].toDouble(), 0.0)
        assertEquals(0.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarMontoOtros].toDouble(), 0.0)
        assertEquals(listOf("CXC"), cajaPaymentTypes())
    }

    @Test
    fun `saldo declarado incoherente con pagos es rechazado`() {
        database = createDatabase("PA")

        assertFailsWith<InvalidSaleRequestException> {
            process("PA", request(payments = listOf(cashPayment(100.0)), saldoPendiente = 20.0))
        }
        assertEquals(0L, transaction(database) { SalesFacturaTablePA.selectAll().count() })
    }

    @Test
    fun `reintento del mismo idFactura conserva rechazo por duplicado`() {
        database = createDatabase("PA")
        val idFactura = "invoice-fixed"
        val request = request(idFactura = idFactura, payments = listOf(cashPayment(100.0)))

        process("PA", request)

        assertFailsWith<DuplicateInvoiceException> {
            process("PA", request)
        }
    }

    @Test
    fun `VE contado conserva forma de pago y caja pagada`() {
        val veDatabase = createDatabase("VE")
        database = veDatabase

        process("VE", request(payments = listOf(cashPayment(100.0))))

        assertEquals(
            "contado",
            transaction(database) {
                SalesFacturaTableVE
                    .select(SalesFacturaTableVE.formaPago)
                    .single()[SalesFacturaTableVE.formaPago]
            },
        )
        assertEquals(
            CajaStatus.Pagada,
            transaction(database) {
                SalesCajaNuevaTableVE
                    .select(SalesCajaNuevaTableVE.status)
                    .single()[SalesCajaNuevaTableVE.status]
            },
        )
    }

    private fun createDatabase(countryCode: String): Database {
        schemaCountry = countryCode
        val db =
            Database.connect(
                "jdbc:h2:mem:sales_credit_${countryCode}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(db) {
            if (countryCode == "PA") {
                SchemaUtils.create(
                    ParametrosGeneralesTablePA,
                    ClientsTable,
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
            } else {
                SchemaUtils.create(
                    ParametrosGeneralesTableVE,
                    ClientsTable,
                    SalesCajaTable,
                    SalesCajaSecuenciaTable,
                    SalesFacturaTableVE,
                    SalesFacturaDetalleTable,
                    SalesFacturaDetalleFormaPagoTableVE,
                    SalesCajaNuevaTableVE,
                    SalesCajaNuevaDetalleTableVE,
                    SalesCajaNuevaDetalleFormaPagoTable,
                    SalesCajaNuevaReciboTableVE,
                )
                ParametrosGeneralesTableVE.insert {
                    it[codEmpresa] = 1
                    it[defaultCodClienteFactura] = ""
                    it[defaultIdFormaPagoFactura] = 1
                    it[porcentajeImpuestoPrincipal] = BigDecimal.ZERO.setScale(2)
                    it[validarStock] = "NO"
                    it[diasVencimiento] = 90
                    it[codAlmacen] = 1
                    it[abrMonedaBase] = "VES"
                    it[monedaBase] = 1
                    it[multiMoneda] = "No"
                    it[monedaSecundaria] = 1
                    it[abrMonedaSecundaria] = "VES"
                    it[igtf] = null
                    it[impresionDirecta] = "No"
                }
            }

            ClientsTable.insert {
                it[idCliente] = "client-1"
                it[codCliente] = "C001"
                it[rif] = "ID-1"
                it[dv] = ""
                it[nombre] = "CLIENTE"
                it[apellido] = "PRUEBA"
                it[direccion] = "DIRECCION"
                it[telefonos] = "0000"
                it[email] = "client@example.invalid"
                it[estado] = "A"
                it[pais] = 170
                it[codTipoCliente] = 1
                it[tipoContribuyente] = 1
                it[permiteCredito] = true
                it[limite] = 0.0
                it[dias] = 30
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
        }
        return db
    }

    private fun setClientCredit(
        permiteCredito: Boolean,
        dias: Int,
    ) {
        transaction(database) {
            ClientsTable.update({ ClientsTable.idCliente eq "client-1" }) {
                it[ClientsTable.permiteCredito] = permiteCredito
                it[ClientsTable.dias] = dias
            }
        }
    }

    private fun process(
        countryCode: String,
        request: ProcessSaleRequest,
    ) {
        transaction(database) {
            repository.process(countryCode, request)
        }
    }

    private fun request(
        formaPago: String = "contado",
        payments: List<SalePaymentInput>,
        saldoPendiente: Double = 0.0,
        idFactura: String = UUID.randomUUID().toString(),
    ): ProcessSaleRequest {
        val cashAmount =
            payments
                .filter { it.tipoMovimiento.equals("CASH", ignoreCase = true) }
                .sumOf { it.monto }
        return ProcessSaleRequest(
            idFactura = idFactura,
            factura =
                SaleInvoiceInput(
                    idCliente = "client-1",
                    codCliente = "C001",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = "caja-1",
                    codigoCaja = "CJ01",
                    idCajaSecuencia = "seq-1",
                    serieSucursal = "S01",
                    formaPago = formaPago,
                    subtotal = 100.0,
                    ivaTotalFactura = 0.0,
                    totalTotalFactura = 100.0,
                    montoItemsFactura = 100.0,
                    totalizarSubTotal = 100.0,
                    totalizarTotalOperacion = 100.0,
                    totalizarBaseImponible = 100.0,
                    totalizarMontoIva = 0.0,
                    totalizarTotalGeneral = 100.0,
                    usuarioCreacion = "TEST",
                ),
            items =
                listOf(
                    SaleItemInput(
                        idItem = 1,
                        itemAlmacen = 1,
                        itemDescripcion = "SERVICIO TEST",
                        itemCantidad = 1.0,
                        itemPrecioSinIva = 100.0,
                        itemPIva = 0.0,
                        itemTotalSinIva = 100.0,
                        itemTotalConIva = 100.0,
                        itemCantidadTotal = 1.0,
                        esProductoFisico = false,
                    ),
                ),
            pagoResumen =
                SalePaymentSummaryInput(
                    totalizarMontoCancelar = 100.0,
                    totalizarMontoEfectivo = cashAmount,
                    totalizarCambio = 0.0,
                    totalizarSaldoPendiente = saldoPendiente,
                ),
            pagos = payments,
            moneda = SaleCurrencyInput(),
        )
    }

    private fun cashPayment(amount: Double) =
        SalePaymentInput(idFormaPago = 1, tipoMovimiento = "CASH", monto = amount, montoRecibido = amount)

    private fun cxcPayment(amount: Double) =
        SalePaymentInput(idFormaPago = 2, tipoMovimiento = "CXC", monto = amount, montoRecibido = amount)

    private fun invoiceFormaPago(): String =
        transaction(database) {
            SalesFacturaTablePA
                .select(SalesFacturaTablePA.formaPago)
                .single()[SalesFacturaTablePA.formaPago]
        }

    private fun invoiceDueDate(): LocalDate? =
        transaction(database) {
            SalesFacturaTablePA
                .select(SalesFacturaTablePA.fechaVencimiento)
                .single()[SalesFacturaTablePA.fechaVencimiento]
        }

    private fun cajaStatus(): CajaStatus =
        transaction(database) {
            SalesCajaNuevaTablePA
                .select(SalesCajaNuevaTablePA.status)
                .single()[SalesCajaNuevaTablePA.status]
        }

    private fun paymentDetail() =
        transaction(database) {
            SalesFacturaDetalleFormaPagoTablePA
                .selectAll()
                .single()
        }

    private fun cajaPaymentTypes(): List<String?> =
        transaction(database) {
            SalesCajaNuevaDetalleFormaPagoTable
                .select(SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento)
                .map { it[SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento] }
        }
}
