package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.mesas.data.AbrirSesionScope
import com.amaxoniaerp.features.mesas.data.CuentaMesaDetalleTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaIdempotenciaTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.CuentaMesaTable
import com.amaxoniaerp.features.mesas.data.MesasTable
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaTable
import com.amaxoniaerp.features.mesas.data.PlantasTable
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import com.amaxoniaerp.features.sales.domain.CuentaMesaVentaInput
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Infraestructura compartida de los tests de cuenta de mesa: base H2 en modo MySQL,
 * esquema, seeds y helpers de creación de pedidos/ventas. Los casos de uso se agrupan
 * en subclases por dominio (creación/cancelación y facturación/idempotencia).
 */
abstract class CuentaMesaRepositoryTestBase {
    protected lateinit var database: Database
    protected val pedidoRepository = PedidoMesaRepository()
    protected val sesionRepository = SesionMesaRepository(pedidoRepository::tieneOperaciones)
    protected val cuentaRepository = CuentaMesaRepository()

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:cuenta_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                SucursalTable,
                CajaTable,
                UsersTable,
                PlantasTable,
                MesasTable,
                SesionMesaTable,
                PedidoMesaTable,
                CuentaMesaTable,
                CuentaMesaDetalleTable,
                CuentaMesaIdempotenciaTable,
            )
            seedSucursales()
            seedCajas()
            seedUsuarios()
            seedAreas()
            seedMesas()
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                CuentaMesaIdempotenciaTable,
                CuentaMesaDetalleTable,
                CuentaMesaTable,
                PedidoMesaTable,
                SesionMesaTable,
                MesasTable,
                PlantasTable,
                UsersTable,
                CajaTable,
                SucursalTable,
            )
        }
    }

    protected suspend fun abrirSesion(mesaId: Int): Int {
        val scope =
            AbrirSesionScope(
                cajaId = CAJA_A,
                areaId = 100,
                mesaId = mesaId,
                usuarioId = 10,
                cantidadPersonas = 4,
            )
        val opened = sesionRepository.abrir(database, scope) as SesionMesaResult.Opened
        return opened.sesion.id
    }

    /**
     * Crea un pedido entregado directamente con estado `ENTREGADA` (salta la transición normal
     * para aislar el SUT de la cuenta). Retorna el id del pedido.
     *
     * `iva` es la TASA (0.10 = 10%); se calcula `totalCon = totalSin * (1 + iva)` para que el
     * impuesto absoluto sea consistente con `item_piva` (tasa multiplicativa).
     */
    protected fun crearPedidoEntregado(
        sesionId: Int,
        productoId: Int,
        cantidad: Double,
        precioSinIva: Double,
        iva: Double,
    ): Int {
        val totalSin = precioSinIva * cantidad
        val totalCon = totalSin * (1.0 + iva)
        return transaction(database) {
            PedidoMesaTable.insert {
                it[PedidoMesaTable.sesionMesaId] = sesionId
                it[PedidoMesaTable.comandaSecuencia] = 1
                it[PedidoMesaTable.productoId] = productoId
                it[PedidoMesaTable.itemAlmacen] = 1
                it[PedidoMesaTable.itemCodigo] = "P$productoId"
                it[PedidoMesaTable.itemDescripcion] = "Producto $productoId"
                it[PedidoMesaTable.itemCantidad] = cantidad.toBigDecimal()
                it[PedidoMesaTable.itemPrecioSinIva] = precioSinIva.toBigDecimal()
                it[PedidoMesaTable.itemMontoDescuento] = BigDecimal.ZERO
                it[PedidoMesaTable.itemPIva] = iva.toBigDecimal()
                it[PedidoMesaTable.itemTotalSinIva] = totalSin.toBigDecimal()
                it[PedidoMesaTable.itemTotalConIva] = totalCon.toBigDecimal()
                it[PedidoMesaTable.estado] = EstadoPedidoMesa.ENTREGADA.codigo
                it[PedidoMesaTable.fechaCreacion] = java.time.LocalDateTime.now()
                it[PedidoMesaTable.fechaEnvio] = java.time.LocalDateTime.now()
                it[PedidoMesaTable.fechaEntrega] = java.time.LocalDateTime.now()
            }[PedidoMesaTable.id]
        }
    }

    protected suspend fun crearPedidoPendiente(
        sesionId: Int,
        productoId: Int,
    ) {
        pedidoRepository.crear(
            database,
            sesionId,
            mesaId = 1001,
            request =
                CrearPedidoMesaRequest(
                    items =
                        listOf(
                            CrearPedidoMesaItemRequest(
                                productoId = productoId,
                                itemAlmacen = 1,
                                itemCodigo = "P$productoId",
                                itemDescripcion = "Producto $productoId",
                                itemCantidad = 1.0,
                                itemPrecioSinIva = 1.0,
                                itemPIva = 0.0,
                                itemTotalSinIva = 1.0,
                                itemTotalConIva = 1.0,
                            ),
                        ),
                    enviarInmediato = false,
                ),
        )
    }

    protected fun crearPedidoRequestConItems(
        productoId: Int,
        cantidad: Double,
    ) = CrearPedidoMesaRequest(
        items =
            listOf(
                CrearPedidoMesaItemRequest(
                    productoId = productoId,
                    itemAlmacen = 1,
                    itemCodigo = "P$productoId",
                    itemDescripcion = "Producto $productoId",
                    itemCantidad = cantidad,
                    itemPrecioSinIva = 5.0,
                    itemPIva = 0.0,
                    itemTotalSinIva = 5.0 * cantidad,
                    itemTotalConIva = 5.0 * cantidad,
                ),
            ),
        enviarInmediato = false,
    )

    protected fun ventaParaCuenta(
        sesionId: Int,
        cuenta: CuentaMesaResponse,
    ): ProcessSaleRequest =
        ProcessSaleRequest(
            factura =
                SaleInvoiceInput(
                    idCliente = "CF",
                    codCliente = "CF",
                    codVendedor = 10,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = CAJA_A,
                    codigoCaja = "1",
                    idCajaSecuencia = "SEQ-1",
                    serieSucursal = "1",
                    formaPago = "CONTADO",
                    subtotal = cuenta.subtotal,
                    ivaTotalFactura = cuenta.impuesto,
                    totalTotalFactura = cuenta.total,
                    montoItemsFactura = cuenta.total,
                    totalizarBaseImponible = cuenta.subtotal,
                    totalizarMontoIva = cuenta.impuesto,
                    totalizarTotalGeneral = cuenta.total,
                    usuarioCreacion = "u10",
                ),
            items =
                cuenta.detalle.map { detalle ->
                    SaleItemInput(
                        idItem = detalle.productoId,
                        itemAlmacen = detalle.itemAlmacen,
                        itemDescripcion = detalle.itemDescripcion,
                        itemCantidad = detalle.cantidad,
                        itemPrecioSinIva = detalle.itemPrecioSinIva,
                        itemPIva = detalle.itemPIva,
                        itemTotalSinIva = detalle.itemTotalSinIva,
                        itemTotalConIva = detalle.itemTotalConIva,
                        itemCantidadTotal = detalle.cantidad,
                        itemCodigo = detalle.itemCodigo,
                    )
                },
            pagoResumen =
                SalePaymentSummaryInput(
                    totalizarMontoCancelar = cuenta.total,
                    totalizarMontoEfectivo = cuenta.total,
                    totalizarCambio = 0.0,
                    totalizarSaldoPendiente = 0.0,
                ),
            cuentaMesa =
                CuentaMesaVentaInput(
                    areaId = 100,
                    mesaId = 1001,
                    sesionMesaId = sesionId,
                    cuentaMesaId = cuenta.id,
                ),
        )

    private fun seedSucursales() {
        SucursalTable.insert {
            it[idSucursal] = 1
            it[codigo] = "S1"
            it[serie] = "1"
            it[sucursal] = "Sucursal A"
        }
    }

    private fun seedCajas() {
        CajaTable.insert {
            it[idCaja] = CAJA_A
            it[codCaja] = CAJA_A
            it[caja] = CAJA_A
            it[idSucursal] = 1
            it[serieCaja] = "1"
        }
    }

    private fun seedUsuarios() {
        UsersTable.insert {
            it[codUsuario] = 10
            it[usuario] = "u10"
            it[clave] = "x"
            it[status] = "1"
        }
    }

    private fun seedAreas() {
        PlantasTable.insert {
            it[PlantasTable.id] = 100
            it[sucursalId] = 1
            it[PlantasTable.nombre] = "Salón principal"
            it[PlantasTable.orden] = 1
            it[activo] = 1
        }
    }

    private fun seedMesas() {
        listOf(1001 to true, 1002 to true, 1003 to false).forEach { (id, activa) ->
            MesasTable.insert {
                it[MesasTable.id] = id
                it[plantaId] = 100
                it[MesasTable.codigo] = "M$id"
                it[MesasTable.nombre] = "Mesa $id"
                it[capacidad] = 4
                it[forma] = "rectangular"
                it[activo] = if (activa) 1 else 0
            }
        }
    }

    protected companion object {
        const val CAJA_A = "caja-a"
    }
}
