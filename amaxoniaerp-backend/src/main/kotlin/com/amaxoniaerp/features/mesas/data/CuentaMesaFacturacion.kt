package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.CuentaDetalleResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Resultado de la verificación de idempotencia previa a facturar:
 * `error != null` indica el resultado temprano a devolver.
 */
internal class IdempotenciaPrevia(
    val error: CuentaMesaResult?,
    val existente: ResultRow?,
)

internal fun verificarIdempotenciaPrevia(
    idempotencyKey: String,
    sesionId: Int,
    cuentaId: Int,
): IdempotenciaPrevia {
    val existente =
        CuentaMesaIdempotenciaTable
            .selectAll()
            .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }
            .singleOrNull()

    val error =
        when {
            existente == null -> null
            esConfirmada(existente) -> CuentaMesaResult.IdempotenciaDuplicada
            perteneceAOtraCuenta(existente, sesionId, cuentaId) -> CuentaMesaResult.IdempotenciaDuplicada
            else -> null
        }
    return IdempotenciaPrevia(error, existente)
}

private fun esConfirmada(row: ResultRow): Boolean =
    EstadoCuentaIdempotencia.fromCodigo(row[CuentaMesaIdempotenciaTable.estado]) ==
        EstadoCuentaIdempotencia.CONFIRMED

private fun perteneceAOtraCuenta(
    row: ResultRow,
    sesionId: Int,
    cuentaId: Int,
): Boolean =
    row[CuentaMesaIdempotenciaTable.cuentaMesaId] != cuentaId ||
        row[CuentaMesaIdempotenciaTable.sesionMesaId] != sesionId

/** Decreta `cantidad_facturada` por detalle; error != null si el saldo ya no alcanza. */
internal fun aplicarFacturacionDetalles(
    cuenta: CuentaMesaResponse,
    cuentaId: Int,
): CuentaMesaResult? {
    for (d in cuenta.detalle) {
        val error = aplicarFacturacionDetalle(d)
        if (error != null) return error
    }
    CuentaMesaDetalleTable.update(
        {
            (CuentaMesaDetalleTable.cuentaMesaId eq cuentaId) and
                (CuentaMesaDetalleTable.facturado eq false)
        },
    ) {
        it[CuentaMesaDetalleTable.facturado] = true
    }
    return null
}

private fun aplicarFacturacionDetalle(d: CuentaDetalleResponse): CuentaMesaResult? {
    val pedido = PedidoMesaTable.selectAll().where { PedidoMesaTable.id eq d.pedidoMesaId }.single()
    val actual = pedido[PedidoMesaTable.cantidadFacturada]
    val nueva = actual + d.cantidad.toBigDecimal()
    if (nueva > pedido[PedidoMesaTable.itemCantidad]) {
        return CuentaMesaResult.CantidadSuperaSaldo
    }
    val updated =
        PedidoMesaTable.update({
            (PedidoMesaTable.id eq d.pedidoMesaId) and
                (PedidoMesaTable.cantidadFacturada eq actual)
        }) {
            it[cantidadFacturada] = nueva
        }
    return if (updated != 1) {
        CuentaMesaResult.CantidadSuperaSaldo
    } else {
        null
    }
}

/** Datos necesarios para confirmar la fila de idempotencia tras facturar. */
internal class ConfirmarIdempotenciaInput(
    val command: MarcarFacturadaCommand,
    val sesionId: Int,
    val existente: ResultRow?,
    val ahora: LocalDateTime,
)

internal fun confirmarIdempotencia(input: ConfirmarIdempotenciaInput) {
    val command = input.command
    if (input.existente != null) {
        CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq command.idempotencyKey }) {
            it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.CONFIRMED.codigo
            it[CuentaMesaIdempotenciaTable.idFacturaResultado] = command.idFactura
            it[CuentaMesaIdempotenciaTable.codFacturaResultado] = command.codFactura
            it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = input.ahora
            it[CuentaMesaIdempotenciaTable.errorMensaje] = null
        }
    } else {
        CuentaMesaIdempotenciaTable.insert {
            it[CuentaMesaIdempotenciaTable.idempotencyKey] = command.idempotencyKey
            it[CuentaMesaIdempotenciaTable.cuentaMesaId] = command.cuentaId
            it[CuentaMesaIdempotenciaTable.sesionMesaId] = input.sesionId
            it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.CONFIRMED.codigo
            it[CuentaMesaIdempotenciaTable.idFacturaResultado] = command.idFactura
            it[CuentaMesaIdempotenciaTable.codFacturaResultado] = command.codFactura
            it[CuentaMesaIdempotenciaTable.fechaPrimerIntento] = input.ahora
            it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = input.ahora
        }
    }
}

internal fun marcarCuentaPagada(
    cuentaId: Int,
    idFactura: String,
    codFactura: String?,
    ahora: LocalDateTime,
) {
    CuentaMesaTable.update({ CuentaMesaTable.id eq cuentaId }) {
        it[CuentaMesaTable.estado] = com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa.PAGADA.codigo
        it[CuentaMesaTable.idFactura] = idFactura
        it[CuentaMesaTable.codFactura] = codFactura
        it[CuentaMesaTable.fechaFactura] = ahora
        it[CuentaMesaTable.fechaCierre] = ahora
        it[CuentaMesaTable.activo] = false
        it[CuentaMesaTable.saldoRestante] = BigDecimal.ZERO
    }
}
