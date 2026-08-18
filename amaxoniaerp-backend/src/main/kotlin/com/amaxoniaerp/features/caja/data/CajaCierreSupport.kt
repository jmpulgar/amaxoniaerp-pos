package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreDetalleRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal const val RETURN_PAYMENT_FORM_FALLBACK = 30
internal const val ANNULLED_INVOICE_STATUS = 3
internal const val CASH_SEQUENCE_LENGTH = 6

internal fun Double.toMoney(): BigDecimal = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)

internal fun csvContains(
    csv: String?,
    token: String,
): Boolean {
    if (csv.isNullOrBlank()) return false
    return csv.split(',').any { it.trim() == token }
}

internal fun csvTokens(csv: String?): List<String> {
    if (csv.isNullOrBlank()) return emptyList()
    return csv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) }
}

internal fun resolveNextSecuenciaCode(idCaja: String): String {
    val max =
        CajaSecuenciaTable
            .select(CajaSecuenciaTable.secuencia)
            .where { CajaSecuenciaTable.idCaja eq idCaja }
            .mapNotNull { row -> row[CajaSecuenciaTable.secuencia]?.trim()?.toIntOrNull() }
            .maxOrNull()
            ?: 0
    val next = max + 1
    return next.toString().padStart(CASH_SEQUENCE_LENGTH, '0')
}

internal fun insertCajaDetalleCierre(
    idSecuencia: String,
    serieSucursal: String,
    detalle: CajaCierreDetalleRequest,
) {
    CajaDetalleCierreTable.insert {
        it[id] = UUID.randomUUID().toString()
        it[CajaDetalleCierreTable.idSecuencia] = idSecuencia
        it[idMonedaDenominacion] = detalle.idMonedaDenominacion
        it[cantidad] = detalle.cantidad
        it[valor] = detalle.valor.toMoney()
        it[monto] = detalle.monto.toMoney()
        it[CajaDetalleCierreTable.serieSucursal] = serieSucursal
    }
}

internal fun insertCajaDetalleCierreFormaPago(
    idSecuencia: String,
    serieSucursal: String,
    detalle: CajaCierreFormaPagoRequest,
) {
    CajaDetalleCierreFormaPagoTable.insert {
        it[id] = UUID.randomUUID().toString()
        it[CajaDetalleCierreFormaPagoTable.idSecuencia] = idSecuencia
        it[idFormaPago] = detalle.idFormaPago
        it[montoVentas] = detalle.monto.toMoney()
        it[montoCierre] = detalle.montoCierre.toMoney()
        it[montoDiferencia] = detalle.montoDiferencia.toMoney()
        it[CajaDetalleCierreFormaPagoTable.serieSucursal] = serieSucursal
    }
}

internal data class FormaPagoCloseTotal(
    val sigla: String?,
    val monto: Double,
)

internal fun buildAutoCloseFormaPagoTotals(data: CajaSecuenciaData): Map<Int, FormaPagoCloseTotal> {
    val totals = linkedMapOf<Int, FormaPagoCloseTotal>()

    data.formaPago
        .asSequence()
        .filter { it.id > 0 && it.monto != 0.0 }
        .forEach { line ->
            totals.merge(
                line.id,
                FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
            ) { current, incoming ->
                current.copy(monto = current.monto + incoming.monto)
            }
        }

    data.formaPagoDevolucion
        .asSequence()
        .filter { it.idFormaPago > 0 && it.monto != 0.0 }
        .forEach { line ->
            totals.merge(
                line.idFormaPago,
                FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
            ) { current, incoming ->
                current.copy(monto = current.monto + incoming.monto)
            }
        }

    return totals
        .filterValues { it.monto > 0.0 }
        .toMap()
}

internal fun insertAperturaRecord(
    newId: String,
    request: AperturaRequest,
    username: String,
    now: java.time.LocalDateTime,
    nextSequence: String,
) {
    CajaSecuenciaTable.insert {
        it[idCajaSecuencia] = newId
        it[idCaja] = request.idCaja
        it[idVendedor] = request.idVendedor
        it[fechaApertura] = now
        it[montoEfectivoApertura] = request.montoApertura.toBigDecimal()
        it[usuario] = username
        it[serieSucursal] = request.serieSucursal
        it[secuencia] = nextSequence
        it[contabilizado] = 0
        it[serialFiscal] = ""
        it[observacionApertura] = "Apertura automática desde App POS"
        it[observacionCierre] = ""
        it[usuarioContabilizacion] = ""
        it[fechaContabilizacion] = now
    }

    CajaDetalleAperturaTable.insert {
        it[idDetalleApertura] = UUID.randomUUID().toString()
        it[idCajaSecuencia] = newId
        it[cantidad] = 1
        it[valor] = request.montoApertura.toBigDecimal()
        it[monto] = request.montoApertura.toBigDecimal()
        it[serieSucursal] = request.serieSucursal
    }
}

internal fun persistCierreRecord(
    request: CajaCierreSaveRequest,
    now: java.time.LocalDateTime,
) {
    CajaSecuenciaTable.update({ CajaSecuenciaTable.idCajaSecuencia eq request.id }) {
        it[fechaCierre] = now
        it[montoEfectivoVentas] = request.montoEfectivoVentas.toMoney()
        it[montoEfectivoEntrada] = request.montoEfectivoEntrada.toMoney()
        it[montoEfectivoSalida] = request.montoEfectivoSalida.toMoney()
        it[montoEfectivoTotal] = request.montoEfectivoTotal.toMoney()
        it[montoEfectivoCierre] = request.montoEfectivoCierre.toMoney()
        it[montoEfectivoDiferencia] = request.montoEfectivoDiferencia.toMoney()
        it[montoOtrosTotal] = request.montoOtrosTotal.toMoney()
        it[montoOtrosCierre] = request.montoOtrosCierre.toMoney()
        it[montoOtrosDiferencia] = request.montoOtrosDiferencia.toMoney()
        it[montoTotal] = request.montoTotal.toMoney()
        it[montoCierre] = request.montoCierre.toMoney()
        it[montoDiferencia] = request.montoDiferencia.toMoney()
        it[observacionCierre] = request.observacionCierre.orEmpty()
        it[numeroCierreFiscal] = request.numeroCierreFiscal
    }
}

internal fun rewriteCierreDetallesRecord(
    request: CajaCierreSaveRequest,
    serieSucursal: String,
) {
    CajaDetalleCierreTable.deleteWhere { CajaDetalleCierreTable.idSecuencia eq request.id }
    CajaDetalleCierreFormaPagoTable.deleteWhere {
        CajaDetalleCierreFormaPagoTable.idSecuencia eq request.id
    }

    request.detalle
        .filter { it.cantidad > 0 }
        .forEach { detalle ->
            insertCajaDetalleCierre(request.id, serieSucursal, detalle)
        }

    request.detalleFormaPago.forEach { formaPago ->
        insertCajaDetalleCierreFormaPago(request.id, serieSucursal, formaPago)
    }
}
