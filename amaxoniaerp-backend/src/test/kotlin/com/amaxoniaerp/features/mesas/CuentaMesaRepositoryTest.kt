package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.mesas.data.AbrirSesionScope
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.CuentaMesaDetalleTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaIdempotenciaTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaTable
import com.amaxoniaerp.features.mesas.data.MesasTable
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaTable
import com.amaxoniaerp.features.mesas.data.PlantasTable
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.CrearCuentaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import com.amaxoniaerp.features.sales.domain.CuentaMesaVentaInput
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobertura del ciclo de cuenta de mesa: creación (completa y por división), idempotencia
 * del marcar-facturada (doble-intento), cierre de sesión cuando se liquida todo, y bloqueos
 * de negocio (cantidad superior al saldo, cuenta no activa, sesión no encontrada).
 *
 * Se ejecuta sobre H2 en modo MySQL para reproducir el dialecto productivo de Exposed sin
 * necesidad de un servidor MySQL levantado.
 */
class CuentaMesaRepositoryTest {
    private lateinit var database: Database
    private val pedidoRepository = PedidoMesaRepository()
    private val sesionRepository = SesionMesaRepository(pedidoRepository::tieneOperaciones)
    private val cuentaRepository = CuentaMesaRepository(sesionRepository, pedidoRepository)

    @BeforeTest
    fun setUp() {
        database = Database.connect("jdbc:h2:mem:cuenta_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
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

    // ---------- Creación de cuenta ----------

    @Test
    fun `crear cuenta completa agrega todos los pedidos ENTREGADOS con saldo`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 10.0, iva = 0.10)

        val result =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true),
            )
        assertTrue(result is CuentaMesaResult.Creada)
        val cuenta = result.cuenta
        assertEquals(1, cuenta.detalle.size)
        assertEquals(ped, cuenta.detalle.first().pedidoMesaId)
        assertEquals(2.0, cuenta.detalle.first().cantidad)
        // subtotal = 2 * 10 = 20; impuesto = 0.10 * 20 = 2.0; total = 22.0
        assertEquals(20.0, cuenta.subtotal, 0.001)
        assertEquals(2.0, cuenta.impuesto, 0.001)
        assertEquals(22.0, cuenta.total, 0.001)
        assertEquals(22.0, cuenta.saldoRestante, 0.001)
    }

    @Test
    fun `crear cuenta sin pedidos entregados devuelve SinItemsParaCrear`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        // Solo pedido PENDIENTE: no facturable.
        crearPedidoPendiente(sesionId, productoId = 501)

        val result =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            )
        assertEquals(CuentaMesaResult.SinItemsParaCrear, result)
    }

    @Test
    fun `crear cuenta por cantidad divide correctamente el saldo del pedido`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 4.0, precioSinIva = 5.0, iva = 0.0)

        // Pedimos solo 1.5 unidades de las 4.
        val result =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request =
                    CrearCuentaRequest(
                        items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped, cantidad = 1.5)),
                        incluirTodoPendiente = false,
                    ),
            )
        assertTrue(result is CuentaMesaResult.Creada)
        val cuenta = result.cuenta
        assertEquals(1.5, cuenta.detalle.first().cantidad, 0.001)
        // total = 1.5 * 5 = 7.5 (iva 0)
        assertEquals(7.5, cuenta.total, 0.001)
    }

    @Test
    fun `crear cuenta por cantidad mayor al saldo devuelve CantidadSuperaSaldo`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)

        val result =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request =
                    CrearCuentaRequest(
                        items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped, cantidad = 3.0)),
                        incluirTodoPendiente = false,
                    ),
            )
        assertEquals(CuentaMesaResult.CantidadSuperaSaldo, result)
    }

    @Test
    fun `cuentas activas reservan cantidades y una cuenta posterior recibe solo el remanente`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val pedido = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 4.0, precioSinIva = 5.0, iva = 0.0)
        val primera =
            cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(
                    items = listOf(CrearCuentaItemRequest(pedidoMesaId = pedido, cantidad = 1.5)),
                    incluirTodoPendiente = false,
                ),
            )
        assertTrue(primera is CuentaMesaResult.Creada)

        val segunda =
            cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada

        assertEquals(2.5, segunda.cuenta.detalle.single().cantidad, 0.001)
    }

    @Test
    fun `una division con una linea inexistente se rechaza completa sin crear cuenta parcial`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val pedido = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)

        val result =
            cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(
                    items =
                        listOf(
                            CrearCuentaItemRequest(pedidoMesaId = pedido, cantidad = 1.0),
                            CrearCuentaItemRequest(pedidoMesaId = 999_999, cantidad = 1.0),
                        ),
                    incluirTodoPendiente = false,
                ),
            )

        assertEquals(CuentaMesaResult.PedidoNoEncontrado, result)
        val cuentas = cuentaRepository.listarCuentas(database, sesionId, 1001) as CuentaMesaResult.Listada
        assertTrue(cuentas.cuentas.isEmpty())
    }

    // ---------- Solicitud de cuenta en sesión ----------

    @Test
    fun `solicitar cuenta transiciona sesion a CUENTA_SOLICITADA y sigue admitiendo pedidos`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val result = sesionRepository.solicitarCuenta(database, sesionId)
        assertTrue(result is SesionMesaResult.Closed)
        assertEquals(EstadoSesionMesa.CUENTA_SOLICITADA.codigo, result.sesion.estado)

        // Tras CUENTA_SOLICITADA todavía podemos crear pedido (modo cuenta abierta):
        val pedido =
            pedidoRepository.crear(
                database,
                sesionId,
                1001,
                crearPedidoRequestConItems(productoId = 505, cantidad = 1.0),
            )
        assertTrue(pedido is com.amaxoniaerp.features.mesas.domain.PedidoMesaResult.Creado)
    }

    @Test
    fun `cancelar solicitud revierte CUENTA_SOLICITADA a ABIERTA`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        sesionRepository.solicitarCuenta(database, sesionId)
        val result = sesionRepository.cancelarSolicitudCuenta(database, sesionId)
        assertTrue(result is SesionMesaResult.Closed)
        assertEquals(EstadoSesionMesa.ABIERTA.codigo, result.sesion.estado)
    }

    // ---------- Cancelar cuenta ----------

    @Test
    fun `cancelar cuenta ACTIVA elimina sus detalles y libera saldo`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)

        val creada =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada
        val cuentaId = creada.cuenta.id

        val cancelada = cuentaRepository.cancelarCuenta(database, sesionId, 1001, cuentaId)
        assertTrue(cancelada is CuentaMesaResult.Creada)
        assertEquals(EstadoCuentaMesa.CANCELADA.codigo, cancelada.cuenta.estado)

        // Tras cancelar, podemos crear una nueva cuenta con el mismo saldo.
        val segunda =
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada
        assertEquals(2.0, segunda.cuenta.detalle.single { it.pedidoMesaId == ped }.cantidad, 0.001)
        Unit
    }

    // ---------- Idempotencia de marcar-facturada ----------

    @Test
    fun `marcar facturada marca lineas y decrementa cantidad_facturada del pedido`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta

        val result =
            cuentaRepository.marcarFacturada(
                database = database,
                sesionId = sesionId,
                mesaId = 1001,
                cuentaId = cuenta.id,
                idempotencyKey = "key-1",
                idFactura = "F-0001",
                codFactura = "FAC-0001",
            )
        assertTrue(result is CuentaMesaResult.Facturada)
        assertEquals(EstadoCuentaMesa.PAGADA.codigo, result.cuenta.estado)
        assertEquals("F-0001", result.cuenta.idFactura)
        assertTrue(result.cuenta.detalle.all { it.facturado })

        // La cantidad_facturada del pedido habría de quedar en 2.0
        val facturada =
            transaction(database) {
                PedidoMesaTable.selectAll().where { PedidoMesaTable.id eq ped }.single()[PedidoMesaTable.cantidadFacturada]
            }
        assertEquals(0, facturada.compareTo(BigDecimal("2.000")))
    }

    @Test
    fun `marcar facturada dos veces con misma key devuelve IdempotenciaDuplicada y NO duplica`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta

        val primera =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta.id,
                idempotencyKey = "k-dup",
                idFactura = "F-1",
                codFactura = null,
            )
        assertTrue(primera is CuentaMesaResult.Facturada)

        val segunda =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta.id,
                idempotencyKey = "k-dup",
                idFactura = "F-2",
                codFactura = null,
            )
        // Aunque la segunda lleva idFactura="F-2", el resultado es duplicado (no doble efecto).
        assertEquals(CuentaMesaResult.IdempotenciaDuplicada, segunda)

        // La cantidad_facturada sigue siendo 2.0 (no 4.0)
        val facturada =
            transaction(database) {
                PedidoMesaTable.selectAll().where { PedidoMesaTable.id eq ped }.single()[PedidoMesaTable.cantidadFacturada]
            }
        assertEquals(0, facturada.compareTo(BigDecimal("2.000")))
    }

    // ---------- Cierre de sesión al liquidar todo ----------

    @Test
    fun `marcar facturada cierra la sesion en CERRADA_PAGADA cuando se liquida todo`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta

        val result =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta.id,
                idempotencyKey = "k-liquidacion",
                idFactura = "F-LIQ",
                codFactura = null,
            )
        assertTrue(result is CuentaMesaResult.Facturada)
        assertTrue(result.sesionCerrada)

        val estado =
            transaction(database) {
                SesionMesaTable.selectAll().where { SesionMesaTable.id eq sesionId }.single()[SesionMesaTable.estado]
            }
        assertEquals(EstadoSesionMesa.CERRADA_PAGADA.codigo, estado)
    }

    @Test
    fun `marcar facturada NO cierra sesion si quedan cuentas activas o pedidos por entregar`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        // dos pedidos entregados: creamos dos cuentas separadas por producto
        val ped1 = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        val ped2 = crearPedidoEntregado(sesionId, productoId = 502, cantidad = 1.0, precioSinIva = 3.0, iva = 0.0)
        val cuenta1 =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request =
                    CrearCuentaRequest(
                        items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped1)),
                        incluirTodoPendiente = false,
                    ),
            ) as CuentaMesaResult.Creada).cuenta
        val cuenta2 =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request =
                    CrearCuentaRequest(
                        items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped2)),
                        incluirTodoPendiente = false,
                    ),
            ) as CuentaMesaResult.Creada).cuenta

        val r1 =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta1.id,
                idempotencyKey = "k-parcial",
                idFactura = "F-1",
                codFactura = null,
            ) as CuentaMesaResult.Facturada
        assertFalse(r1.sesionCerrada) // todavía hay una cuenta2 ACTIVA

        // Segundo pago: ahora sí debería cerrar.
        val r2 =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta2.id,
                idempotencyKey = "k-total",
                idFactura = "F-2",
                codFactura = null,
            ) as CuentaMesaResult.Facturada
        assertTrue(r2.sesionCerrada)
    }

    @Test
    fun `un pedido no entregado mantiene la sesion abierta aunque la cuenta cobrada quede pagada`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        crearPedidoPendiente(sesionId, productoId = 502)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta

        val result =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                1001,
                cuenta.id,
                "k-con-pendiente",
                "F-CON-PENDIENTE",
                null,
            ) as CuentaMesaResult.Facturada

        assertFalse(result.sesionCerrada)
    }

    @Test
    fun `confirmar venta de cuenta registra factura cantidades e idempotencia atomicamente`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta
        val request = ventaParaCuenta(sesionId, cuenta)

        val cerrada =
            transaction(database) {
                val validada =
                    cuentaRepository.validarVentaEnTransaccion(
                        checkNotNull(request.cuentaMesa),
                        request,
                        "mesa-$sesionId-cuenta-${cuenta.id}",
                    )
                cuentaRepository.confirmarVentaEnTransaccion(validada, "F-ATOMICA", "FAC-ATOMICA")
            }

        assertTrue(cerrada)
        transaction(database) {
            val final = CuentaMesaTable.selectAll().where { CuentaMesaTable.id eq cuenta.id }.single()
            assertEquals(EstadoCuentaMesa.PAGADA.codigo, final[CuentaMesaTable.estado])
            assertEquals("F-ATOMICA", final[CuentaMesaTable.idFactura])
            assertTrue(
                CuentaMesaDetalleTable
                    .selectAll()
                    .where { CuentaMesaDetalleTable.cuentaMesaId eq cuenta.id }
                    .all { it[CuentaMesaDetalleTable.facturado] == 1 },
            )
        }
    }

    @Test
    fun `venta con total distinto se rechaza antes de marcar cantidades`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        val pedido = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                1001,
                CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta
        val request =
            ventaParaCuenta(sesionId, cuenta).let {
                it.copy(factura = it.factura.copy(totalTotalFactura = it.factura.totalTotalFactura + 1.0))
            }

        assertFailsWith<InvalidSaleRequestException> {
            transaction(database) {
                cuentaRepository.validarVentaEnTransaccion(checkNotNull(request.cuentaMesa), request, "F-INVALIDA")
            }
        }
        val facturada =
            transaction(database) {
                PedidoMesaTable.selectAll().where { PedidoMesaTable.id eq pedido }.single()[PedidoMesaTable.cantidadFacturada]
            }
        assertEquals(0, facturada.compareTo(BigDecimal.ZERO))
    }

    // ---------- Fallos de facturación ----------

    @Test
    fun `registrar idempotencia fallida deja el intento FAILED y permite reintento`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        val cuenta =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            ) as CuentaMesaResult.Creada).cuenta

        // Pre-registramos intento SENDING
        cuentaRepository.iniciarIdempotencia(database, sesionId, 1001, cuenta.id, "k-fallido")

        val failed = cuentaRepository.registrarIdempotenciaFallida(database, "k-fallido", "Procesar venta: 500")
        assertTrue(failed is CuentaMesaResult.Creada)

        val estadoIdem =
            transaction(database) {
                CuentaMesaIdempotenciaTable.selectAll().where {
                    CuentaMesaIdempotenciaTable.idempotencyKey eq "k-fallido"
                }.single()[CuentaMesaIdempotenciaTable.estado]
            }
        assertEquals(EstadoCuentaIdempotencia.FAILED.codigo, estadoIdem)

        // Reintento con misma key permitido:
        val remarcada =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta.id,
                idempotencyKey = "k-fallido",
                idFactura = "F-retry",
                codFactura = null,
            )
        assertTrue(remarcada is CuentaMesaResult.Facturada)
    }

    @Test
    fun `marcar facturada en cuenta no activa devuelve CuentaNoActiva`() = runBlocking {
        val sesionId = abrirSesion(mesaId = 1001)
        // Dos pedidos entregados → dos cuentas independientes; así marcar la 1ª NO cierra la sesión.
        val ped1 = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
        crearPedidoEntregado(sesionId, productoId = 502, cantidad = 1.0, precioSinIva = 3.0, iva = 0.0)
        val cuenta1 =
            (cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request =
                    CrearCuentaRequest(
                        items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped1)),
                        incluirTodoPendiente = false,
                    ),
            ) as CuentaMesaResult.Creada).cuenta
        // Creamos cuenta2 previa para que la sesión NO se cierre al pagar cuenta1.
        cuentaRepository.crear(
            database,
            sesionId,
            mesaId = 1001,
            request = CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true),
        )

        // Marcarla una vez (queda PAGADA; no cierra sesión porque todavía hay cuenta2 ACTIVA).
        cuentaRepository.marcarFacturada(
            database,
            sesionId,
            mesaId = 1001,
            cuentaId = cuenta1.id,
            idempotencyKey = "k-1",
            idFactura = "F-1",
            codFactura = null,
        )

        // Segundo intento con otra key (no idempotente): state debe ser CuentaNoActiva.
        val segundo =
            cuentaRepository.marcarFacturada(
                database,
                sesionId,
                mesaId = 1001,
                cuentaId = cuenta1.id,
                idempotencyKey = "k-2",
                idFactura = "F-2",
                codFactura = null,
            )
        assertEquals(CuentaMesaResult.CuentaNoActiva, segundo)
    }

    // ---------- Aislamiento mesa/sesion ----------

    @Test
    fun `crear cuenta en sesion de otra mesa devuelve SesionNoPerteneceMesa`() = runBlocking {
        val sesionA = abrirSesion(mesaId = 1001)
        crearPedidoEntregado(sesionA, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)

        // Mismo sesionId pero la mesa 1002 también existe (abrir sesión en 1002 NO consume el id 1).
        val result =
            cuentaRepository.crear(
                database,
                sesionId = sesionA,
                mesaId = 1002, // mesa distinta
                request = CrearCuentaRequest(incluirTodoPendiente = true),
            )
        assertEquals(CuentaMesaResult.SesionNoPerteneceMesa, result)
    }

    // ---------- helpers ----------

    private suspend fun abrirSesion(mesaId: Int): Int {
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
    private fun crearPedidoEntregado(
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

    private suspend fun crearPedidoPendiente(
        sesionId: Int,
        productoId: Int,
    ) {
        pedidoRepository.crear(
            database,
            sesionId,
            mesaId = 1001,
            request =
                com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest(
                    items =
                        listOf(
                            com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaItemRequest(
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

    private fun crearPedidoRequestConItems(
        productoId: Int,
        cantidad: Double,
    ) = com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest(
        items =
            listOf(
                com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaItemRequest(
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

    private fun ventaParaCuenta(
        sesionId: Int,
        cuenta: com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse,
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

    private companion object {
        const val CAJA_A = "caja-a"
    }
}
