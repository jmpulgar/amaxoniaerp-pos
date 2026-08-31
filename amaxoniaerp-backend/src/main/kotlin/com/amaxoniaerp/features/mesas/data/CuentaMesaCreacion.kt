package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

internal fun proponerDetalles(
    request: CrearCuentaRequest,
    pedidosFacturables: List<PedidoFacturable>,
): PropuestaDetalles =
    if (request.incluirTodoPendiente) {
        PropuestaDetalles(
            error = null,
            detalles =
                pedidosFacturables.map { p ->
                    DetallePropuesta(
                        pedido = p.row,
                        cantidad = p.saldoPendiente,
                    )
                },
        )
    } else {
        proponerDetallesSeleccionados(request, pedidosFacturables)
    }

private fun proponerDetallesSeleccionados(
    request: CrearCuentaRequest,
    pedidosFacturables: List<PedidoFacturable>,
): PropuestaDetalles {
    if (request.items.isEmpty()) {
        return PropuestaDetalles(CuentaMesaResult.SinItemsParaCrear, emptyList())
    }
    val porPedido = pedidosFacturables.associateBy { it.row[PedidoMesaTable.id] }
    val lista = mutableListOf<DetallePropuesta>()
    var error: CuentaMesaResult? = null
    request.items.groupBy { it.pedidoMesaId }.forEach { (pedidoId, solicitudes) ->
        if (error == null) {
            error = acumularSolicitudesPedido(pedidoId, solicitudes, porPedido, lista)
        }
    }
    return PropuestaDetalles(error, lista)
}

private fun acumularSolicitudesPedido(
    pedidoId: Int,
    solicitudes: List<com.amaxoniaerp.features.mesas.domain.CrearCuentaItemRequest>,
    porPedido: Map<Int, PedidoFacturable>,
    lista: MutableList<DetallePropuesta>,
): CuentaMesaResult? {
    val pedido = porPedido[pedidoId]
    return when {
        pedido == null -> CuentaMesaResult.PedidoNoEncontrado
        else -> validarCantidadSolicitada(pedido, solicitudes, lista)
    }
}

private fun validarCantidadSolicitada(
    pedido: PedidoFacturable,
    solicitudes: List<com.amaxoniaerp.features.mesas.domain.CrearCuentaItemRequest>,
    lista: MutableList<DetallePropuesta>,
): CuentaMesaResult? {
    val cantidadSolicitada =
        solicitudes.fold(BigDecimal.ZERO) { total, solicitud ->
            total +
                (
                    solicitud.cantidad?.toBigDecimal()?.stripTrailingZeros()
                        ?: pedido.saldoPendiente
                )
        }
    return when {
        cantidadSolicitada <= BigDecimal.ZERO || cantidadSolicitada > pedido.saldoPendiente ->
            CuentaMesaResult.CantidadSuperaSaldo
        else -> {
            lista += DetallePropuesta(pedido = pedido.row, cantidad = cantidadSolicitada)
            null
        }
    }
}

internal fun insertarCuentaConDetalles(
    sesionId: Int,
    detalles: List<DetallePropuesta>,
): Int {
    val numeroCuenta = siguienteNumeroCuenta(sesionId)
    val ahora = LocalDateTime.now()
    val cuentaId =
        CuentaMesaTable.insert {
            it[CuentaMesaTable.sesionMesaId] = sesionId
            it[CuentaMesaTable.numeroCuenta] = numeroCuenta
            it[CuentaMesaTable.estado] = EstadoCuentaMesa.ACTIVA.codigo
            it[CuentaMesaTable.fechaCreacion] = ahora
            it[CuentaMesaTable.activo] = true
        }[CuentaMesaTable.id]

    // Inserta detalles y acumula totales
    var subtotal = BigDecimal.ZERO
    var descuento = BigDecimal.ZERO
    var impuesto = BigDecimal.ZERO
    var total = BigDecimal.ZERO
    detalles.forEach { detalle ->
        val cantidad = detalle.cantidad
        val row = detalle.pedido
        val factorCantidad =
            cantidad.divide(row[PedidoMesaTable.itemCantidad], UNIT_CALCULATION_SCALE, RoundingMode.HALF_EVEN)
        val detalleSub = row[PedidoMesaTable.itemTotalSinIva].multiply(factorCantidad)
        val detalleDesc = row[PedidoMesaTable.itemMontoDescuento].multiply(factorCantidad)
        val detalleIva =
            row[PedidoMesaTable.itemTotalConIva]
                .subtract(
                    row[PedidoMesaTable.itemTotalSinIva],
                ).multiply(factorCantidad)
        val detalleTotal = row[PedidoMesaTable.itemTotalConIva].multiply(factorCantidad)

        CuentaMesaDetalleTable.insert {
            it[CuentaMesaDetalleTable.cuentaMesaId] = cuentaId
            it[CuentaMesaDetalleTable.pedidoMesaId] = row[PedidoMesaTable.id]
            it[CuentaMesaDetalleTable.productoId] = row[PedidoMesaTable.productoId]
            it[CuentaMesaDetalleTable.itemAlmacen] = row[PedidoMesaTable.itemAlmacen]
            it[CuentaMesaDetalleTable.itemCodigo] = row[PedidoMesaTable.itemCodigo]
            it[CuentaMesaDetalleTable.itemDescripcion] = row[PedidoMesaTable.itemDescripcion]
            it[CuentaMesaDetalleTable.cantidad] = cantidad
            it[CuentaMesaDetalleTable.itemPrecioSinIva] = row[PedidoMesaTable.itemPrecioSinIva]
            it[CuentaMesaDetalleTable.itemDescuento] = row[PedidoMesaTable.itemDescuento]
            it[CuentaMesaDetalleTable.itemMontoDescuento] = detalleDesc.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN)
            it[CuentaMesaDetalleTable.itemPIva] = row[PedidoMesaTable.itemPIva]
            it[CuentaMesaDetalleTable.itemTotalSinIva] = detalleSub.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN)
            it[CuentaMesaDetalleTable.itemTotalConIva] = detalleTotal.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN)
            it[CuentaMesaDetalleTable.facturado] = false
            it[CuentaMesaDetalleTable.fechaCreacion] = ahora
        }
        subtotal = subtotal.add(detalleSub.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN))
        descuento = descuento.add(detalleDesc.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN))
        impuesto = impuesto.add(detalleIva.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN))
        total = total.add(detalleTotal.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN))
    }

    CuentaMesaTable.update({ CuentaMesaTable.id eq cuentaId }) {
        it[CuentaMesaTable.subtotal] = subtotal
        it[CuentaMesaTable.descuento] = descuento
        it[CuentaMesaTable.impuesto] = impuesto
        it[CuentaMesaTable.total] = total
        it[CuentaMesaTable.saldoRestante] = total
    }
    return cuentaId
}
