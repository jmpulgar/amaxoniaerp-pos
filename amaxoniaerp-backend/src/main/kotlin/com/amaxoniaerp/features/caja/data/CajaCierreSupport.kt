package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaCierreDetalleRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
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

internal fun isCancelledStatus(description: String?): Boolean {
    if (description.isNullOrBlank()) return false
    return description.equals("Anulada", ignoreCase = true) ||
        description.equals("Anulado", ignoreCase = true)
}

internal fun isCashSigla(siglas: String?): Boolean {
    val value = siglas.orEmpty().trim().uppercase()
    return value == "CASH" || value == "EF" || value == "EFE" || value == "EFECTIVO"
}

internal enum class PaymentCategory {
    CASH,
    CARD,
    OTHER,
}

private val CASH_CODES = setOf("CASH", "EF", "EFE", "EFECTIVO")
private val CARD_CODES = setOf("TDC", "TARJETA", "PV", "POS", "NEQ", "DB", "DEBITO", "CR", "CREDITO")

internal fun classifyPaymentCategory(
    tipoMovimiento: String?,
    siglas: String?,
    descripcion: String?,
): PaymentCategory {
    val normalizedSigla = siglas.orEmpty().trim().uppercase()
    val normalizedTipo = tipoMovimiento.orEmpty().trim().uppercase()
    val normalizedDescripcion = descripcion.orEmpty().trim().uppercase()

    val isCash =
        normalizedSigla in CASH_CODES ||
            normalizedTipo in CASH_CODES ||
            normalizedDescripcion.contains("EFECTIVO")
    if (isCash) {
        return PaymentCategory.CASH
    }
    val descripcionHints = listOf("TARJETA", "DEBITO", "CREDITO")
    val isCard =
        normalizedSigla in CARD_CODES ||
            normalizedTipo in CARD_CODES ||
            descripcionHints.any { normalizedDescripcion.contains(it) }
    if (isCard) {
        return PaymentCategory.CARD
    }
    return PaymentCategory.OTHER
}
