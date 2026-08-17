package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val ITBMS_RATE_7 = 7.0
private const val ITBMS_RATE_10 = 10.0
private const val ITBMS_RATE_15 = 15.0
private const val ISO_DATE_LENGTH = 10

/**
 * Mapea el porcentaje de IVA/ITBMS al código del catálogo The Factory.
 * 7% → "01", 10% → "02", 15% → "03", otro → "00" (exento).
 */
internal fun mapTasaITBMS(piva: Double): String =
    when {
        piva == ITBMS_RATE_7 || isApprox(piva, ITBMS_RATE_7) -> "01"
        piva == ITBMS_RATE_10 || isApprox(piva, ITBMS_RATE_10) -> "02"
        piva == ITBMS_RATE_15 || isApprox(piva, ITBMS_RATE_15) -> "03"
        else -> "00"
    }

private fun isApprox(
    a: Double,
    b: Double,
    epsilon: Double = 0.01,
): Boolean = kotlin.math.abs(a - b) < epsilon

internal fun normalizeTipoClienteFE(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.isBlank() || trimmed == "0") "02" else trimmed
}

/**
 * Normaliza un código a 2 dígitos con cero al frente (ej. "1" → "01").
 */
internal fun normalizeToTwoDigits(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.length == 1) "0$trimmed" else trimmed
}

/**
 * Formatea la fecha de la factura al formato ISO 8601 (yyyy-MM-dd'T'HH:mm:ss).
 * La fecha viene en formato "yyyy-MM-dd" desde la DB.
 */
internal fun formatFechaEmisionForPayload(fecha: String?): String {
    if (fecha.isNullOrBlank()) {
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
    }
    return try {
        val localDate = LocalDate.parse(fecha.trim().take(ISO_DATE_LENGTH))
        localDate.format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
    } catch (_: Exception) {
        LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
    }
}

/**
 * Extensión para formatear Double a String con N decimales exactos.
 */
internal fun Double.formatDecimals(scale: Int): String =
    BigDecimal
        .valueOf(this)
        .setScale(scale, RoundingMode.HALF_UP)
        .toPlainString()

internal fun FEFormaPagoData.isCashPayment(): Boolean {
    val siglas = siglas?.uppercase()?.trim()
    return esCash || siglas in setOf("EF", "CASH", "EFECTIVO") || formaPagoFact == "02"
}
