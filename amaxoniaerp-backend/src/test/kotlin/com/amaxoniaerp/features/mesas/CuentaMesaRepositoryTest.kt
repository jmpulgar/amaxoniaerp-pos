package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.mesas.domain.CrearCuentaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResult
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ciclo de creación de cuenta de mesa: creación completa y por división, reserva de saldo,
 * solicitud/cancelación de sesión y cancelación de cuenta ACTIVA. Infraestructura y seeds
 * en [CuentaMesaRepositoryTestBase]; el ciclo de facturación vive en [CuentaMesaFacturadaTest].
 */
class CuentaMesaRepositoryTest : CuentaMesaRepositoryTestBase() {
    // ---------- Creación de cuenta ----------

    @Test
    fun `crear cuenta completa agrega todos los pedidos ENTREGADOS con saldo`() =
        runBlocking {
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
    fun `crear cuenta sin pedidos entregados devuelve SinItemsParaCrear`() =
        runBlocking {
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
    fun `crear cuenta por cantidad divide correctamente el saldo del pedido`() =
        runBlocking {
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
    fun `crear cuenta por cantidad mayor al saldo devuelve CantidadSuperaSaldo`() =
        runBlocking {
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
    fun `cuentas activas reservan cantidades y una cuenta posterior recibe solo el remanente`() =
        runBlocking {
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

            assertEquals(
                2.5,
                segunda.cuenta.detalle
                    .single()
                    .cantidad,
                0.001,
            )
        }

    @Test
    fun `una division con una linea inexistente se rechaza completa sin crear cuenta parcial`() =
        runBlocking {
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
    fun `solicitar cuenta transiciona sesion a CUENTA_SOLICITADA y sigue admitiendo pedidos`() =
        runBlocking {
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
            assertTrue(pedido is PedidoMesaResult.Creado)
        }

    @Test
    fun `cancelar solicitud revierte CUENTA_SOLICITADA a ABIERTA`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            sesionRepository.solicitarCuenta(database, sesionId)
            val result = sesionRepository.cancelarSolicitudCuenta(database, sesionId)
            assertTrue(result is SesionMesaResult.Closed)
            assertEquals(EstadoSesionMesa.ABIERTA.codigo, result.sesion.estado)
        }

    // ---------- Cancelar cuenta ----------

    @Test
    fun `cancelar cuenta ACTIVA elimina sus detalles y libera saldo`() =
        runBlocking {
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
            assertEquals(
                2.0,
                segunda.cuenta.detalle
                    .single { it.pedidoMesaId == ped }
                    .cantidad,
                0.001,
            )
            Unit
        }
}
