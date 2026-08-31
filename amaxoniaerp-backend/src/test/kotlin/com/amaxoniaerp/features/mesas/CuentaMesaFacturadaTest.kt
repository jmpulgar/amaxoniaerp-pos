package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.mesas.data.CuentaMesaDetalleTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaIdempotenciaTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaTable
import com.amaxoniaerp.features.mesas.data.MarcarFacturadaCommand
import com.amaxoniaerp.features.mesas.data.PedidoMesaTable
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.data.validarVentaEnTransaccion
import com.amaxoniaerp.features.mesas.domain.CrearCuentaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ciclo de facturación de cuenta de mesa: marcar-facturada (idempotencia y cierre de sesión),
 * confirmación/validación de venta, fallos de facturación y aislamiento mesa/sesión.
 * Infraestructura y seeds en [CuentaMesaRepositoryTestBase].
 */
class CuentaMesaFacturadaTest : CuentaMesaRepositoryTestBase() {
    // ---------- Idempotencia de marcar-facturada ----------

    @Test
    fun `marcar facturada marca lineas y decrementa cantidad_facturada del pedido`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request = CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            val result =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta.id,
                            idempotencyKey = "key-1",
                            idFactura = "F-0001",
                            codFactura = "FAC-0001",
                        ),
                )
            assertTrue(result is CuentaMesaResult.Facturada)
            assertEquals(EstadoCuentaMesa.PAGADA.codigo, result.cuenta.estado)
            assertEquals("F-0001", result.cuenta.idFactura)
            assertTrue(result.cuenta.detalle.all { it.facturado })

            // La cantidad_facturada del pedido habría de quedar en 2.0
            val facturada =
                transaction(database) {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            PedidoMesaTable.id eq ped
                        }.single()[PedidoMesaTable.cantidadFacturada]
                }
            assertEquals(0, facturada.compareTo(BigDecimal("2.000")))
        }

    @Test
    fun `marcar facturada dos veces con misma key devuelve IdempotenciaDuplicada y NO duplica`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val ped = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request = CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            val primera =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta.id,
                            idempotencyKey = "k-dup",
                            idFactura = "F-1",
                            codFactura = null,
                        ),
                )
            assertTrue(primera is CuentaMesaResult.Facturada)

            val segunda =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta.id,
                            idempotencyKey = "k-dup",
                            idFactura = "F-2",
                            codFactura = null,
                        ),
                )
            // Aunque la segunda lleva idFactura="F-2", el resultado es duplicado (no doble efecto).
            assertEquals(CuentaMesaResult.IdempotenciaDuplicada, segunda)

            // La cantidad_facturada sigue siendo 2.0 (no 4.0)
            val facturada =
                transaction(database) {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            PedidoMesaTable.id eq ped
                        }.single()[PedidoMesaTable.cantidadFacturada]
                }
            assertEquals(0, facturada.compareTo(BigDecimal("2.000")))
        }

    // ---------- Cierre de sesión al liquidar todo ----------

    @Test
    fun `marcar facturada cierra la sesion en CERRADA_PAGADA cuando se liquida todo`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request = CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            val result =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta.id,
                            idempotencyKey = "k-liquidacion",
                            idFactura = "F-LIQ",
                            codFactura = null,
                        ),
                )
            assertTrue(result is CuentaMesaResult.Facturada)
            assertTrue(result.sesionCerrada)

            val estado =
                transaction(database) {
                    SesionMesaTable
                        .selectAll()
                        .where {
                            SesionMesaTable.id eq sesionId
                        }.single()[SesionMesaTable.estado]
                }
            assertEquals(EstadoSesionMesa.CERRADA_PAGADA.codigo, estado)
        }

    @Test
    fun `marcar facturada NO cierra sesion si quedan cuentas activas o pedidos por entregar`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            // dos pedidos entregados: creamos dos cuentas separadas por producto
            val ped1 = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            val ped2 = crearPedidoEntregado(sesionId, productoId = 502, cantidad = 1.0, precioSinIva = 3.0, iva = 0.0)
            val cuenta1 =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request =
                            CrearCuentaRequest(
                                items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped1)),
                                incluirTodoPendiente = false,
                            ),
                    ) as CuentaMesaResult.Creada
                ).cuenta
            val cuenta2 =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request =
                            CrearCuentaRequest(
                                items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped2)),
                                incluirTodoPendiente = false,
                            ),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            val r1 =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta1.id,
                            idempotencyKey = "k-parcial",
                            idFactura = "F-1",
                            codFactura = null,
                        ),
                ) as CuentaMesaResult.Facturada
            assertFalse(r1.sesionCerrada) // todavía hay una cuenta2 ACTIVA

            // Segundo pago: ahora sí debería cerrar.
            val r2 =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta2.id,
                            idempotencyKey = "k-total",
                            idFactura = "F-2",
                            codFactura = null,
                        ),
                ) as CuentaMesaResult.Facturada
            assertTrue(r2.sesionCerrada)
        }

    @Test
    fun `un pedido no entregado mantiene la sesion abierta aunque la cuenta cobrada quede pagada`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            crearPedidoPendiente(sesionId, productoId = 502)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        1001,
                        CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            val result =
                cuentaRepository.marcarFacturada(
                    database,
                    MarcarFacturadaCommand(
                        sesionId = sesionId,
                        mesaId = 1001,
                        cuentaId = cuenta.id,
                        idempotencyKey = "k-con-pendiente",
                        idFactura = "F-CON-PENDIENTE",
                        codFactura = null,
                    ),
                ) as CuentaMesaResult.Facturada

            assertFalse(result.sesionCerrada)
        }

    // ---------- Confirmación de venta ----------

    @Test
    fun `confirmar venta de cuenta registra factura cantidades e idempotencia atomicamente`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            crearPedidoEntregado(sesionId, productoId = 501, cantidad = 2.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        1001,
                        CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta
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
                        .all { it[CuentaMesaDetalleTable.facturado] },
                )
            }
        }

    @Test
    fun `venta con total distinto se rechaza antes de marcar cantidades`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val pedido = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        1001,
                        CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta
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
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            PedidoMesaTable.id eq pedido
                        }.single()[PedidoMesaTable.cantidadFacturada]
                }
            assertEquals(0, facturada.compareTo(BigDecimal.ZERO))
        }

    // ---------- Fallos de facturación ----------

    @Test
    fun `registrar idempotencia fallida deja el intento FAILED y permite reintento`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            val cuenta =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request = CrearCuentaRequest(incluirTodoPendiente = true),
                    ) as CuentaMesaResult.Creada
                ).cuenta

            // Pre-registramos intento SENDING
            cuentaRepository.iniciarIdempotencia(database, sesionId, 1001, cuenta.id, "k-fallido")

            val failed = cuentaRepository.registrarIdempotenciaFallida(database, "k-fallido", "Procesar venta: 500")
            assertTrue(failed is CuentaMesaResult.Creada)

            val estadoIdem =
                transaction(database) {
                    CuentaMesaIdempotenciaTable
                        .selectAll()
                        .where {
                            CuentaMesaIdempotenciaTable.idempotencyKey eq "k-fallido"
                        }.single()[CuentaMesaIdempotenciaTable.estado]
                }
            assertEquals(EstadoCuentaIdempotencia.FAILED.codigo, estadoIdem)

            // Reintento con misma key permitido:
            val remarcada =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta.id,
                            idempotencyKey = "k-fallido",
                            idFactura = "F-retry",
                            codFactura = null,
                        ),
                )
            assertTrue(remarcada is CuentaMesaResult.Facturada)
        }

    @Test
    fun `marcar facturada en cuenta no activa devuelve CuentaNoActiva`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            // Dos pedidos entregados -> dos cuentas independientes; pagar la 1a NO cierra la sesion.
            val ped1 = crearPedidoEntregado(sesionId, productoId = 501, cantidad = 1.0, precioSinIva = 5.0, iva = 0.0)
            crearPedidoEntregado(sesionId, productoId = 502, cantidad = 1.0, precioSinIva = 3.0, iva = 0.0)
            val cuenta1 =
                (
                    cuentaRepository.crear(
                        database,
                        sesionId,
                        mesaId = 1001,
                        request =
                            CrearCuentaRequest(
                                items = listOf(CrearCuentaItemRequest(pedidoMesaId = ped1)),
                                incluirTodoPendiente = false,
                            ),
                    ) as CuentaMesaResult.Creada
                ).cuenta
            // Creamos cuenta2 previa para que la sesión NO se cierre al pagar cuenta1.
            cuentaRepository.crear(
                database,
                sesionId,
                mesaId = 1001,
                request = CrearCuentaRequest(items = emptyList(), incluirTodoPendiente = true),
            )

            // Marcarla una vez (queda PAGADA; no cierra sesión porque aún hay cuenta2 ACTIVA).
            cuentaRepository.marcarFacturada(
                database = database,
                command =
                    MarcarFacturadaCommand(
                        sesionId = sesionId,
                        mesaId = 1001,
                        cuentaId = cuenta1.id,
                        idempotencyKey = "k-1",
                        idFactura = "F-1",
                        codFactura = null,
                    ),
            )

            // Segundo intento con otra key (no idempotente): state debe ser CuentaNoActiva.
            val segundo =
                cuentaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = sesionId,
                            mesaId = 1001,
                            cuentaId = cuenta1.id,
                            idempotencyKey = "k-2",
                            idFactura = "F-2",
                            codFactura = null,
                        ),
                )
            assertEquals(CuentaMesaResult.CuentaNoActiva, segundo)
        }

    // ---------- Aislamiento mesa/sesion ----------

    @Test
    fun `crear cuenta en sesion de otra mesa devuelve SesionNoPerteneceMesa`() =
        runBlocking {
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
}
