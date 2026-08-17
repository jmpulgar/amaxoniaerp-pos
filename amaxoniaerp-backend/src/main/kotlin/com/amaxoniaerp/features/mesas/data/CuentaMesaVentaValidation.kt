package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.sales.domain.CuentaMesaVentaInput
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/**
 * Valida una cuenta antes de crear la factura. Debe invocarse desde una transacción Exposed
 * ya abierta y seguida por la confirmación de venta en esa misma transacción.
 */
fun CuentaMesaRepository.validarVentaEnTransaccion(
    context: CuentaMesaVentaInput,
    request: ProcessSaleRequest,
    idFactura: String,
): CuentaMesaVentaValidada {
    validarSesionVenta(context, request)
    val cuenta = validarCuentaParaCobro(context, request)
    validarPedidosEntregados(cuenta.detalle, context)
    registrarIdempotenciaSending(context, idFactura)
    return CuentaMesaVentaValidada(context, cuenta)
}

private fun validarSesionVenta(
    context: CuentaMesaVentaInput,
    request: ProcessSaleRequest,
) {
    val sesion =
        SesionMesaTable
            .selectAll()
            .where {
                (SesionMesaTable.id eq context.sesionMesaId) and
                    (SesionMesaTable.areaId eq context.areaId) and
                    (SesionMesaTable.mesaId eq context.mesaId) and
                    (SesionMesaTable.cajaId eq request.factura.idCaja) and
                    (SesionMesaTable.activo eq ACTIVE)
            }.singleOrNull()
            ?: throw InvalidSaleRequestException("La sesión de mesa no pertenece a la caja, área o mesa indicadas")
    val estadoSesion = EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
    if (estadoSesion == null || estadoSesion.esFinal) {
        throw InvalidSaleRequestException("La sesión de mesa ya no está activa")
    }
}

private fun validarCuentaParaCobro(
    context: CuentaMesaVentaInput,
    request: ProcessSaleRequest,
): CuentaMesaResponse {
    val cuenta =
        cargarCuenta(context.sesionMesaId, context.cuentaMesaId, forUpdate = true)
            ?: throw InvalidSaleRequestException("Cuenta de mesa no encontrada")
    if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo || cuenta.detalle.isEmpty()) {
        throw InvalidSaleRequestException("La cuenta de mesa ya no está disponible para cobro")
    }
    validarCoincidenciaVenta(cuenta, request)
    return cuenta
}

private fun validarCoincidenciaVenta(
    cuenta: CuentaMesaResponse,
    request: ProcessSaleRequest,
) {
    if (dinero(cuenta.total) != dinero(request.factura.totalTotalFactura)) {
        throw InvalidSaleRequestException("El total de la venta no coincide con la cuenta de mesa")
    }

    val esperado =
        cuenta.detalle
            .groupBy { Triple(it.productoId, it.itemAlmacen, dinero(it.itemPrecioSinIva)) }
            .mapValues { (_, lineas) -> cantidad(lineas.sumOf { it.cantidad }) }
    val recibido =
        request.items
            .groupBy { Triple(it.idItem, it.itemAlmacen, dinero(it.itemPrecioSinIva)) }
            .mapValues { (_, lineas) -> cantidad(lineas.sumOf { it.itemCantidadTotal }) }
    if (esperado != recibido) {
        throw InvalidSaleRequestException(
            "Los productos o cantidades de la venta no coinciden con la cuenta de mesa",
        )
    }
}

private fun validarPedidosEntregados(
    detalles: List<com.amaxoniaerp.features.mesas.domain.CuentaDetalleResponse>,
    context: CuentaMesaVentaInput,
) {
    detalles.forEach { detalle ->
        val pedido =
            PedidoMesaTable
                .selectAll()
                .where {
                    (PedidoMesaTable.id eq detalle.pedidoMesaId) and
                        (PedidoMesaTable.sesionMesaId eq context.sesionMesaId) and
                        (PedidoMesaTable.activo eq ACTIVE)
                }.singleOrNull()
                ?: throw InvalidSaleRequestException("Una línea de la cuenta ya no existe")
        validarPedidoEntregado(pedido, detalle)
    }
}

private fun validarPedidoEntregado(
    pedido: org.jetbrains.exposed.sql.ResultRow,
    detalle: com.amaxoniaerp.features.mesas.domain.CuentaDetalleResponse,
) {
    if (pedido[PedidoMesaTable.estado] == EstadoPedidoMesa.CANCELADA.codigo ||
        pedido[PedidoMesaTable.estado] != EstadoPedidoMesa.ENTREGADA.codigo
    ) {
        throw InvalidSaleRequestException("Sólo se pueden facturar pedidos entregados y no cancelados")
    }
    val nuevoFacturado = pedido[PedidoMesaTable.cantidadFacturada] + detalle.cantidad.toBigDecimal()
    if (nuevoFacturado > pedido[PedidoMesaTable.itemCantidad]) {
        throw InvalidSaleRequestException("La cantidad de la cuenta supera el saldo pendiente")
    }
}

private fun registrarIdempotenciaSending(
    context: CuentaMesaVentaInput,
    idFactura: String,
) {
    val idem =
        CuentaMesaIdempotenciaTable
            .selectAll()
            .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idFactura }
            .singleOrNull()
    if (idem != null &&
        (
            idem[CuentaMesaIdempotenciaTable.cuentaMesaId] != context.cuentaMesaId ||
                idem[CuentaMesaIdempotenciaTable.sesionMesaId] != context.sesionMesaId
        )
    ) {
        throw InvalidSaleRequestException("La clave idempotente pertenece a otra cuenta de mesa")
    }
    val ahora = LocalDateTime.now()
    if (idem == null) {
        CuentaMesaIdempotenciaTable.insert {
            it[idempotencyKey] = idFactura
            it[cuentaMesaId] = context.cuentaMesaId
            it[sesionMesaId] = context.sesionMesaId
            it[estado] = EstadoCuentaIdempotencia.SENDING.codigo
            it[intentos] = 1
            it[fechaPrimerIntento] = ahora
            it[fechaUltimoIntento] = ahora
        }
    } else {
        CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq idFactura }) {
            it[estado] = EstadoCuentaIdempotencia.SENDING.codigo
            it[intentos] = idem[CuentaMesaIdempotenciaTable.intentos] + 1
            it[fechaUltimoIntento] = ahora
            it[errorMensaje] = null
        }
    }
}
