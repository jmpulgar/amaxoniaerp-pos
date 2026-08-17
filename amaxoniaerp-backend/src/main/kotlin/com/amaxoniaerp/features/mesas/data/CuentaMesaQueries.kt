package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

internal data class SesionSumario(
    val id: Int,
    val estado: String,
)

internal data class PedidoFacturable(
    val row: ResultRow,
    val saldoPendiente: BigDecimal,
)

/** Propuesta de detalles para `crear`; error != null cuando la solicitud no es vÃƒÂ¡lida. */
internal class PropuestaDetalles(
    val error: CuentaMesaResult?,
    val detalles: List<DetallePropuesta>,
)

internal data class DetallePropuesta(
    val pedido: ResultRow,
    val cantidad: BigDecimal,
)

internal fun sesionActiva(
    sesionId: Int,
    mesaId: Int,
    forUpdate: Boolean = false,
): SesionSumario? {
    val query =
        SesionMesaTable
            .selectAll()
            .where {
                (SesionMesaTable.id eq sesionId) and
                    (SesionMesaTable.mesaId eq mesaId) and
                    (SesionMesaTable.activo eq ACTIVE)
            }
    if (forUpdate) query.forUpdate()
    val row =
        query.singleOrNull()
            ?: return null
    return SesionSumario(id = row[SesionMesaTable.id], estado = row[SesionMesaTable.estado])
}

internal fun siguienteNumeroCuenta(sesionId: Int): Int {
    val maximo =
        CuentaMesaTable
            .selectAll()
            .where { CuentaMesaTable.sesionMesaId eq sesionId }
            .mapNotNull { it[CuentaMesaTable.numeroCuenta] }
            .maxOrNull()
    return (maximo ?: 0) + 1
}

internal fun pedidosFacturablesDeSesion(sesionId: Int): List<PedidoFacturable> {
    val cuentasActivas =
        CuentaMesaTable
            .selectAll()
            .where {
                (CuentaMesaTable.sesionMesaId eq sesionId) and
                    (CuentaMesaTable.estado eq EstadoCuentaMesa.ACTIVA.codigo) and
                    (CuentaMesaTable.activo eq ACTIVE)
            }.map { it[CuentaMesaTable.id] }
    val reservado =
        if (cuentasActivas.isEmpty()) {
            emptyMap()
        } else {
            CuentaMesaDetalleTable
                .selectAll()
                .where { CuentaMesaDetalleTable.cuentaMesaId inList cuentasActivas }
                .groupBy { it[CuentaMesaDetalleTable.pedidoMesaId] }
                .mapValues { (_, rows) ->
                    rows.fold(BigDecimal.ZERO) { acc, row -> acc + row[CuentaMesaDetalleTable.cantidad] }
                }
        }
    return PedidoMesaTable
        .selectAll()
        .where {
            (PedidoMesaTable.sesionMesaId eq sesionId) and
                (PedidoMesaTable.activo eq ACTIVE) and
                (PedidoMesaTable.estado eq EstadoPedidoMesa.ENTREGADA.codigo)
        }.orderBy(PedidoMesaTable.id)
        .map { row ->
            val cantidadPedido = row[PedidoMesaTable.itemCantidad]
            val facturada = row[PedidoMesaTable.cantidadFacturada]
            val saldo =
                (cantidadPedido - facturada - (reservado[row[PedidoMesaTable.id]] ?: BigDecimal.ZERO))
                    .stripTrailingZeros()
            PedidoFacturable(row = row, saldoPendiente = saldo)
        }.filter { it.saldoPendiente.compareTo(BigDecimal.ZERO) > 0 }
}

internal fun cargarCuentas(sesionIds: List<Int>): List<CuentaMesaResponse> =
    if (sesionIds.isEmpty()) {
        emptyList()
    } else {
        CuentaMesaTable
            .selectAll()
            .where { CuentaMesaTable.sesionMesaId inList sesionIds }
            .orderBy(CuentaMesaTable.id)
            .map { it.toCuentaMesaResponse() }
    }

internal fun cargarCuenta(
    sesionId: Int,
    cuentaId: Int,
    forUpdate: Boolean = false,
): CuentaMesaResponse? {
    val query =
        CuentaMesaTable
            .selectAll()
            .where {
                (CuentaMesaTable.id eq cuentaId) and (CuentaMesaTable.sesionMesaId eq sesionId)
            }
    if (forUpdate) query.forUpdate()
    val row =
        query.singleOrNull()
            ?: return null
    val detalles =
        CuentaMesaDetalleTable
            .selectAll()
            .where { CuentaMesaDetalleTable.cuentaMesaId eq cuentaId }
            .orderBy(CuentaMesaDetalleTable.id)
            .map { it.toCuentaDetalleResponse() }
    return row.toCuentaMesaResponse(detalles = detalles)
}

internal fun existeCuentaActivaEnSesion(sesionId: Int): Boolean =
    CuentaMesaTable
        .selectAll()
        .where {
            (CuentaMesaTable.sesionMesaId eq sesionId) and
                (CuentaMesaTable.activo eq ACTIVE) and
                (CuentaMesaTable.estado eq EstadoCuentaMesa.ACTIVA.codigo)
        }.limit(1)
        .singleOrNull() != null

internal fun existeSaldoPendienteEnSesion(sesionId: Int): Boolean =
    PedidoMesaTable
        .selectAll()
        .where {
            (PedidoMesaTable.sesionMesaId eq sesionId) and
                (PedidoMesaTable.activo eq ACTIVE) and
                (PedidoMesaTable.estado neq EstadoPedidoMesa.CANCELADA.codigo)
        }.any { row -> row[PedidoMesaTable.cantidadFacturada] < row[PedidoMesaTable.itemCantidad] }

/**
 * Cierra la sesiÃƒÂ³n por pago completo, dentro de la transacciÃƒÂ³n actual. Idempotente: si la
 * sesiÃƒÂ³n ya no es `ABIERTA`/`CUENTA_SOLICITADA` (race con otra caja), lo ignora.
 */
internal fun runCerradoPorPago(sesionId: Int): Boolean =
    run {
        val row =
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq sesionId) and
                        (SesionMesaTable.activo eq ACTIVE)
                }.singleOrNull()
                ?: return false
        val estadoActual =
            EstadoSesionMesa.fromCodigo(row[SesionMesaTable.estado])
                ?: return false
        if (estadoActual.esFinal) return false
        val ahora = LocalDateTime.now()
        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = EstadoSesionMesa.CERRADA_PAGADA.codigo
            it[SesionMesaTable.fechaCierre] = ahora
            it[SesionMesaTable.activo] = INACTIVE
        }
        return true
    }
