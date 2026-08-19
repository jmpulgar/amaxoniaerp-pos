package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.application.CloseCajaUseCase
import com.amaxoniaerp.features.caja.application.OpenCajaUseCase
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreDetalleRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTablePA
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTablePA
import com.amaxoniaerp.features.sales.data.SalesFacturaTablePA
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization tests (TASK-043): congelan el comportamiento actual de
 * apertura/cierre de caja (auto-close por nueva apertura, validaciones de
 * cierre y persistencia) antes de mover la orquestación a la capa application.
 * Los valores esperados reflejan el cálculo vigente, incluido el doble conteo
 * de devoluciones en el auto-close (monto de formaPago ya incluye la
 * devolución aplicada y buildAutoCloseFormaPagoTotals la vuelve a sumar).
 */
class CajaOpenCloseCharacterizationTest {
    private val repository = CajaRepository()
    private val closeCaja = CloseCajaUseCase(repository)
    private val openCaja = OpenCajaUseCase(repository, closeCaja)
    private lateinit var database: Database

    @Before
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:caja_open_close_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                CajaTable,
                VendedorTable,
                CajaSecuenciaTable,
                CajaDetalleAperturaTable,
                CajaDetalleCierreTable,
                CajaDetalleCierreFormaPagoTable,
                MonedaDenominacionTable,
                CajaMovimientoTable,
                FacturaDevolucionTable,
                CajaFormaTable,
                CajaFormaPagoTable,
                CajaFormaPagoGrupoTable,
                SalesCajaNuevaTablePA,
                SalesCajaNuevaDetalleTablePA,
                SalesFacturaTablePA,
            )
            seedFormasPago()
        }
    }

    @After
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                SalesFacturaTablePA,
                SalesCajaNuevaDetalleTablePA,
                SalesCajaNuevaTablePA,
                CajaFormaPagoGrupoTable,
                CajaFormaPagoTable,
                CajaFormaTable,
                FacturaDevolucionTable,
                CajaMovimientoTable,
                MonedaDenominacionTable,
                CajaDetalleCierreFormaPagoTable,
                CajaDetalleCierreTable,
                CajaDetalleAperturaTable,
                CajaSecuenciaTable,
                VendedorTable,
                CajaTable,
            )
        }
    }

    @Test
    fun `openCaja crea secuencia 000001 con detalle de apertura y retorna estado abierto`() =
        runBlocking {
            val result = openCaja.execute(database, PA, DB, apertura(), "alice")

            assertTrue(result.isSuccess)
            val status = result.getOrThrow()
            assertEquals(CAJA, status.idCaja)
            assertEquals(1, status.estatus)
            assertEquals("alice", status.usuarioApertura)
            assertEquals(50.0, status.montoApertura)
            assertNull(status.fechaCierre)

            val row = secuenciaRows().single()
            assertEquals("000001", row[CajaSecuenciaTable.secuencia])
            assertEquals("alice", row[CajaSecuenciaTable.usuario])
            assertEquals(BigDecimal("50.00"), row[CajaSecuenciaTable.montoEfectivoApertura])
            assertNull(row[CajaSecuenciaTable.fechaCierre])
            assertNotNull(row[CajaSecuenciaTable.fechaApertura])
            assertEquals("Apertura automática desde App POS", row[CajaSecuenciaTable.observacionApertura])
            assertEquals(0, row[CajaSecuenciaTable.contabilizado])
            assertEquals("", row[CajaSecuenciaTable.serialFiscal])
            assertEquals("", row[CajaSecuenciaTable.usuarioContabilizacion])
            assertEquals("A", row[CajaSecuenciaTable.serieSucursal])

            val detalle =
                transaction(database) {
                    CajaDetalleAperturaTable.selectAll().single()
                }
            assertEquals(1, detalle[CajaDetalleAperturaTable.cantidad])
            assertEquals(BigDecimal("50.00"), detalle[CajaDetalleAperturaTable.valor])
            assertEquals(BigDecimal("50.00"), detalle[CajaDetalleAperturaTable.monto])
            assertEquals("A", detalle[CajaDetalleAperturaTable.serieSucursal])
        }

    @Test
    fun `openCaja genera secuencias incrementales de seis digitos`() =
        runBlocking {
            openCaja.execute(database, PA, DB, apertura(), "alice")
            openCaja.execute(database, PA, DB, apertura(), "alice")

            val secuencias = secuenciaRows().mapNotNull { it[CajaSecuenciaTable.secuencia] }
            assertEquals(listOf("000001", "000002"), secuencias.sorted())
        }

    @Test
    fun `openCaja auto-cierra secuencia abierta con totales vigentes y abre la siguiente`() =
        runBlocking {
            seedSecuenciaAbierta(
                id = SEQ_OLD,
                secuencia = "000005",
                montoApertura = 10.0,
            )
            seedVentaCajaNueva("cn-1", FORMA_EF, 100.0)
            seedVentaCajaNueva("cn-2", FORMA_TDC, 40.0)
            seedMovimiento("mov-e", "E", 5.0)
            seedMovimiento("mov-s", "S", 3.0)
            seedDevolucion("dev-1", FORMA_EF, 2.0)

            val result = openCaja.execute(database, PA, DB, apertura(), "bob")

            assertTrue(result.isSuccess)
            val nueva = result.getOrThrow()
            assertEquals("000006", nuevaSecuencia(nueva.idCajaSecuencia))

            val cerrada =
                secuenciaRows().first { it[CajaSecuenciaTable.idCajaSecuencia] == SEQ_OLD }
            assertNotNull(cerrada[CajaSecuenciaTable.fechaCierre])
            assertEquals(BigDecimal("104.00"), cerrada[CajaSecuenciaTable.montoEfectivoVentas])
            assertEquals(BigDecimal("5.00"), cerrada[CajaSecuenciaTable.montoEfectivoEntrada])
            assertEquals(BigDecimal("3.00"), cerrada[CajaSecuenciaTable.montoEfectivoSalida])
            assertEquals(BigDecimal("116.00"), cerrada[CajaSecuenciaTable.montoEfectivoTotal])
            assertEquals(BigDecimal("116.00"), cerrada[CajaSecuenciaTable.montoEfectivoCierre])
            assertEquals(BigDecimal("0.00"), cerrada[CajaSecuenciaTable.montoEfectivoDiferencia])
            assertEquals(BigDecimal("40.00"), cerrada[CajaSecuenciaTable.montoOtrosTotal])
            assertEquals(BigDecimal("40.00"), cerrada[CajaSecuenciaTable.montoOtrosCierre])
            assertEquals(BigDecimal("0.00"), cerrada[CajaSecuenciaTable.montoOtrosDiferencia])
            assertEquals(BigDecimal("156.00"), cerrada[CajaSecuenciaTable.montoTotal])
            assertEquals(BigDecimal("156.00"), cerrada[CajaSecuenciaTable.montoCierre])
            assertEquals(BigDecimal("0.00"), cerrada[CajaSecuenciaTable.montoDiferencia])
            assertEquals("Cierre automático por nueva apertura", cerrada[CajaSecuenciaTable.observacionCierre])

            val formasCierre =
                transaction(database) {
                    CajaDetalleCierreFormaPagoTable
                        .selectAll()
                        .where { CajaDetalleCierreFormaPagoTable.idSecuencia eq SEQ_OLD }
                        .associateBy { it[CajaDetalleCierreFormaPagoTable.idFormaPago] }
                }
            assertEquals(setOf(FORMA_EF, FORMA_TDC), formasCierre.keys)
            assertEquals(BigDecimal("104.00"), formasCierre[FORMA_EF]!![CajaDetalleCierreFormaPagoTable.montoVentas])
            assertEquals(BigDecimal("40.00"), formasCierre[FORMA_TDC]!![CajaDetalleCierreFormaPagoTable.montoVentas])
            assertEquals(0, detalleCierreCount(SEQ_OLD))
        }

    @Test
    fun `saveCajaCierre falla si la secuencia no existe`() =
        runBlocking {
            val result = closeCaja.close(database, PA, cierreRequest(id = "missing"))

            assertTrue(result.isFailure)
            assertEquals("Secuencia de caja no encontrada", result.exceptionOrNull()!!.message)
        }

    @Test
    fun `saveCajaCierre falla si la secuencia ya esta cerrada`() =
        runBlocking {
            seedSecuenciaCerrada()

            val result = closeCaja.close(database, PA, cierreRequest(id = SEQ_CLOSED))

            assertTrue(result.isFailure)
            assertEquals("La secuencia de caja ya se encuentra cerrada", result.exceptionOrNull()!!.message)
        }

    @Test
    fun `saveCajaCierre falla con facturas temporales pendientes`() =
        runBlocking {
            seedSecuenciaAbierta(id = SEQ_OPEN, secuencia = "000001", montoApertura = 0.0)
            seedFactura("fac-temp", SEQ_OPEN, formaPago = "contado", codEstatus = 1)

            val result = closeCaja.close(database, PA, cierreRequest(id = SEQ_OPEN))

            assertTrue(result.isFailure)
            assertEquals("Existen facturas temporales pendientes por procesar", result.exceptionOrNull()!!.message)
        }

    @Test
    fun `saveCajaCierre ignora temporales de credito`() =
        runBlocking {
            seedSecuenciaAbierta(id = SEQ_OPEN, secuencia = "000001", montoApertura = 0.0)
            seedFactura("fac-cred", SEQ_OPEN, formaPago = "Credito", codEstatus = 1)

            val result = closeCaja.close(database, PA, cierreRequest(id = SEQ_OPEN))

            assertTrue(result.isSuccess)
            assertTrue(secuenciaRows().single()[CajaSecuenciaTable.fechaCierre] != null)
        }

    @Test
    fun `saveCajaCierre persiste montos redondeados y reescribe detalles`() =
        runBlocking {
            seedSecuenciaAbierta(id = SEQ_OPEN, secuencia = "000003", montoApertura = 20.0)
            seedDetalleCierreStale()

            val request =
                cierreRequest(id = SEQ_OPEN).copy(
                    montoEfectivoCierre = 12.346,
                    detalle =
                        listOf(
                            CajaCierreDetalleRequest(idMonedaDenominacion = 1, cantidad = 2, valor = 5.0, monto = 10.0),
                            CajaCierreDetalleRequest(idMonedaDenominacion = 2, cantidad = 0, valor = 1.0, monto = 0.0),
                        ),
                    detalleFormaPago =
                        listOf(
                            CajaCierreFormaPagoRequest(FORMA_EF, 100.0, 100.0, 0.0),
                            CajaCierreFormaPagoRequest(FORMA_TDC, 40.0, 38.0, 2.0),
                        ),
                    observacionCierre = "cierre manual",
                    numeroCierreFiscal = "Z-99",
                )

            val result = closeCaja.close(database, PA, request)

            val response = result.getOrThrow()
            assertTrue(response.success)
            assertEquals("Cierre de caja guardado correctamente", response.message)
            assertEquals(SEQ_OPEN, response.id)

            val row = secuenciaRows().single()
            assertNotNull(row[CajaSecuenciaTable.fechaCierre])
            assertEquals(BigDecimal("12.35"), row[CajaSecuenciaTable.montoEfectivoCierre])
            assertEquals("cierre manual", row[CajaSecuenciaTable.observacionCierre])
            assertEquals("Z-99", row[CajaSecuenciaTable.numeroCierreFiscal])

            val detalles =
                transaction(database) {
                    CajaDetalleCierreTable
                        .selectAll()
                        .where { CajaDetalleCierreTable.idSecuencia eq SEQ_OPEN }
                        .toList()
                }
            assertEquals(1, detalles.size)
            assertEquals(2, detalles.single()[CajaDetalleCierreTable.cantidad])
            assertEquals(BigDecimal("10.00"), detalles.single()[CajaDetalleCierreTable.monto])

            val formas =
                transaction(database) {
                    CajaDetalleCierreFormaPagoTable
                        .selectAll()
                        .where { CajaDetalleCierreFormaPagoTable.idSecuencia eq SEQ_OPEN }
                        .toList()
                }
            assertEquals(2, formas.size)
            assertFalse(formas.any { it[CajaDetalleCierreFormaPagoTable.idSecuencia] == "stale" })
        }

    private fun apertura() =
        AperturaRequest(
            idCaja = CAJA,
            montoApertura = 50.0,
            idVendedor = 1,
            serieSucursal = "A",
        )

    private fun cierreRequest(id: String) =
        CajaCierreSaveRequest(
            id = id,
            montoEfectivoVentas = 0.0,
            montoEfectivoEntrada = 0.0,
            montoEfectivoSalida = 0.0,
            montoEfectivoTotal = 0.0,
            montoEfectivoCierre = 0.0,
            montoEfectivoDiferencia = 0.0,
            montoOtrosTotal = 0.0,
            montoOtrosCierre = 0.0,
            montoOtrosDiferencia = 0.0,
            montoTotal = 0.0,
            montoCierre = 0.0,
            montoDiferencia = 0.0,
        )

    private fun secuenciaRows() =
        transaction(database) {
            CajaSecuenciaTable.selectAll().toList()
        }

    private fun nuevaSecuencia(idNueva: String) =
        secuenciaRows()
            .first { it[CajaSecuenciaTable.idCajaSecuencia] == idNueva }[CajaSecuenciaTable.secuencia]

    private fun detalleCierreCount(idSecuencia: String) =
        transaction(database) {
            CajaDetalleCierreTable
                .selectAll()
                .where { CajaDetalleCierreTable.idSecuencia eq idSecuencia }
                .count()
        }

    private fun seedFormasPago() {
        seedFormaPago(FORMA_EF, "EF", "EFECTIVO")
        seedFormaPago(FORMA_TDC, "TDC", "TARJETA")
        CajaFormaTable.insert {
            it[idCaja] = CAJA
            it[idFormaPago] = FORMA_EF
            it[activo] = 1
        }
        CajaFormaTable.insert {
            it[idCaja] = CAJA
            it[idFormaPago] = FORMA_TDC
            it[activo] = 1
        }
    }

    private fun seedFormaPago(
        id: Int,
        siglas: String,
        descripcion: String,
    ) {
        CajaFormaPagoTable.insert {
            it[idFormaPago] = id
            it[CajaFormaPagoTable.siglas] = siglas
            it[codigo] = id
            it[CajaFormaPagoTable.descripcion] = descripcion
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

    private fun seedSecuenciaAbierta(
        id: String,
        secuencia: String,
        montoApertura: Double,
    ) {
        transaction(database) {
            CajaSecuenciaTable.insert {
                it[idCajaSecuencia] = id
                it[idCaja] = CAJA
                it[idVendedor] = 1
                it[CajaSecuenciaTable.secuencia] = secuencia
                it[fechaApertura] = LocalDateTime.of(2026, 1, 1, 8, 0)
                it[montoEfectivoApertura] = BigDecimal.valueOf(montoApertura)
                it[usuario] = "seed"
                it[serieSucursal] = "A"
            }
        }
    }

    private fun seedSecuenciaCerrada() {
        seedSecuenciaAbierta(id = SEQ_CLOSED, secuencia = "000001", montoApertura = 0.0)
        transaction(database) {
            CajaSecuenciaTable.update({ CajaSecuenciaTable.idCajaSecuencia eq SEQ_CLOSED }) {
                it[fechaCierre] = LocalDateTime.of(2026, 1, 1, 20, 0)
            }
        }
    }

    private fun seedVentaCajaNueva(
        cajaId: String,
        formaPago: Int,
        monto: Double,
    ) {
        transaction(database) {
            SalesCajaNuevaTablePA.insert {
                it[SalesCajaNuevaTablePA.cajaId] = cajaId
                it[idTransaccion] = "t-$cajaId"
                it[fecha] = LocalDate.of(2026, 1, 1)
                it[ingEg] = CajaIngresoEgreso.I
                it[SalesCajaNuevaTablePA.monto] = BigDecimal.valueOf(monto)
                it[comprobante] = "FAC"
                it[comprobanteNumero] = "F-001"
                it[idFactura] = "fac-$cajaId"
                it[idCliente] = "cliente-1"
                it[status] = CajaStatus.Pagada
                it[sucursalId] = 1
                it[usuarioCreacion] = "seed"
                it[idCompra] = ""
                it[idProveedor] = ""
                it[idOrdenPago] = ""
                it[serieSucursal] = "A"
                it[idCajaSecuencia] = SEQ_OLD
                it[idPedido] = ""
                it[idAbono] = ""
                it[idNotaCredito] = ""
            }
            SalesCajaNuevaDetalleTablePA.insert {
                it[cajaDetalleId] = "det-$cajaId"
                it[SalesCajaNuevaDetalleTablePA.cajaId] = cajaId
                it[idFormaPago] = formaPago
                it[idTransaccion] = "t-$cajaId"
                it[cajaReciboId] = "rec-$cajaId"
                it[SalesCajaNuevaDetalleTablePA.monto] = BigDecimal.valueOf(monto)
                it[montoOriginal] = BigDecimal.valueOf(monto)
                it[concepto] = "Venta"
                it[usuarioCreacion] = "seed"
                it[retencionTipo] = ""
                it[retencionPorcentaje] = ""
                it[numero] = ""
                it[observacion] = ""
                it[retencionBaseCalculo] = ""
                it[serieSucursal] = "A"
                it[cajaSecuencia] = SEQ_OLD
                it[numeroControl] = ""
                it[numeroComprobante] = "F-001"
                it[retencionMonto] = ""
                it[retencionDetalleJson] = ""
            }
        }
    }

    private fun seedMovimiento(
        id: String,
        tipo: String,
        total: Double,
    ) {
        transaction(database) {
            CajaMovimientoTable.insert {
                it[CajaMovimientoTable.id] = id
                it[idSecuencia] = SEQ_OLD
                it[CajaMovimientoTable.tipo] = tipo
                it[CajaMovimientoTable.total] = BigDecimal.valueOf(total)
            }
        }
    }

    private fun seedDevolucion(
        id: String,
        formaPago: Int,
        total: Double,
    ) {
        transaction(database) {
            FacturaDevolucionTable.insert {
                it[FacturaDevolucionTable.id] = id
                it[idCajaSecuencia] = SEQ_OLD
                it[idFormaPago] = formaPago
                it[totalTotalFactura] = BigDecimal.valueOf(total)
            }
        }
    }

    private fun seedFactura(
        id: String,
        idCajaSecuencia: String,
        formaPago: String,
        codEstatus: Int,
    ) {
        transaction(database) {
            SalesFacturaTablePA.insert {
                it[idFactura] = id
                it[codFactura] = "F-001"
                it[codFacturaFiscal] = "CF-001"
                it[idCliente] = "cliente-1"
                it[codVendedor] = 1
                it[subtotal] = BigDecimal.ONE
                it[descuentosItemFactura] = BigDecimal.ZERO
                it[montoItemsFactura] = BigDecimal.ONE
                it[ivaTotalFactura] = BigDecimal.ZERO
                it[totalTotalFactura] = BigDecimal.ONE
                it[cantidadItems] = 1
                it[totalizarSubTotal] = BigDecimal.ONE
                it[totalizarDescuentoParcial] = BigDecimal.ZERO
                it[totalizarTotalOperacion] = BigDecimal.ONE
                it[totalizarPDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarDescuentoGlobal] = BigDecimal.ZERO
                it[totalizarBaseImponible] = BigDecimal.ONE
                it[totalizarMontoIva] = BigDecimal.ZERO
                it[totalizarTotalGeneral] = BigDecimal.ONE
                it[totalizarTotalRetencion] = BigDecimal.ZERO
                it[SalesFacturaTablePA.formaPago] = formaPago
                it[SalesFacturaTablePA.codEstatus] = codEstatus
                it[usuarioCreacion] = "seed"
                it[tipoFactura] = "POS"
                it[facturarA] = "Cliente"
                it[facturarARuc] = "V-123"
                it[facturarADireccion] = "Dir"
                it[facturarATelefono] = "000"
                it.fillOperacionDefaults(idCajaSecuencia)
            }
        }
    }

    private fun InsertStatement<Number>.fillOperacionDefaults(idCajaSecuencia: String) {
        this[SalesFacturaTablePA.validarStock] = "1"
        this[SalesFacturaTablePA.idShop] = 1
        this[SalesFacturaTablePA.servicioPeriodo] = ""
        this[SalesFacturaTablePA.servicioOrden] = ""
        this[SalesFacturaTablePA.observacion] = ""
        this[SalesFacturaTablePA.servicioAnio] = 2026
        this[SalesFacturaTablePA.servicioMes] = "01"
        this[SalesFacturaTablePA.idCajaSecuencia] = idCajaSecuencia
        this[SalesFacturaTablePA.numcomContabilizado] = 0
        this[SalesFacturaTablePA.fechaContabilizado] = LocalDate.of(2026, 1, 1)
        this[SalesFacturaTablePA.serieSucursal] = "A"
        this[SalesFacturaTablePA.cajaSecuencia] = "000001"
        this[SalesFacturaTablePA.idSucursal] = 1
        this[SalesFacturaTablePA.idCaja] = CAJA
        this[SalesFacturaTablePA.codigoCaja] = "CAJA"
        this[SalesFacturaTablePA.codCliente] = "C-1"
        this.fillFiscalDefaults()
    }

    private fun InsertStatement<Number>.fillFiscalDefaults() {
        this[SalesFacturaTablePA.nroz] = "0001"
        this[SalesFacturaTablePA.impresoraSerial] = ""
        this[SalesFacturaTablePA.multiMoneda] = "0"
        this[SalesFacturaTablePA.tasa] = 1f
        this[SalesFacturaTablePA.idTasa] = 1
        this[SalesFacturaTablePA.monedaBase] = 1
        this[SalesFacturaTablePA.abrMonedaBase] = "USD"
        this[SalesFacturaTablePA.monedaSecundaria] = 2
        this[SalesFacturaTablePA.abrMonedaSecundaria] = "USD"
        this[SalesFacturaTablePA.totalRef] = 1f
    }

    private fun seedDetalleCierreStale() {
        transaction(database) {
            CajaDetalleCierreTable.insert {
                it[id] = "stale-detalle"
                it[idSecuencia] = "stale"
                it[idMonedaDenominacion] = 1
                it[cantidad] = 9
                it[valor] = BigDecimal.ONE
                it[monto] = BigDecimal.ONE
                it[serieSucursal] = "A"
            }
            CajaDetalleCierreFormaPagoTable.insert {
                it[id] = "stale-forma"
                it[idSecuencia] = "stale"
                it[idFormaPago] = FORMA_EF
                it[montoVentas] = BigDecimal.ONE
                it[montoCierre] = BigDecimal.ONE
                it[montoDiferencia] = BigDecimal.ZERO
                it[serieSucursal] = "A"
            }
        }
    }

    private companion object {
        const val PA = "PA"
        const val DB = "testdb"
        const val CAJA = "caja-1"
        const val SEQ_OLD = "seq-old"
        const val SEQ_OPEN = "seq-open"
        const val SEQ_CLOSED = "seq-closed"
        const val FORMA_EF = 1
        const val FORMA_TDC = 2
    }
}
