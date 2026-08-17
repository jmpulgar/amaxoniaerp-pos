package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

internal const val ALICUOTA_GENERAL_PCT = "16.00"
internal const val ALICUOTA_REDUCIDO_PCT = "8.00"
internal const val ALICUOTA_EXENTO_PCT = "0.00"
internal const val MONEY_SCALE = 2
internal const val QTY_SCALE = 3
internal const val PERCENT_CALCULATION_SCALE = 6
internal const val ISO_DATE_LENGTH = 10
internal const val TRANSACTION_ID_HASH_BYTES = 16

internal data class IvaPorAlicuota(
    val totalGeneral: BigDecimal,
    val totalReducido: BigDecimal,
    val totalExento: BigDecimal,
)

internal fun agruparIvaPorAlicuota(detalles: List<VEDetalleData>): IvaPorAlicuota {
    var general = BigDecimal.ZERO
    var reducido = BigDecimal.ZERO
    var exento = BigDecimal.ZERO
    for (det in detalles) {
        val base = det.totalSinIva.bigDecimalMoney()
        when {
            isAprox(det.piva, BigDecimal("16")) -> general = general.add(base)
            isAprox(det.piva, BigDecimal("8")) -> reducido = reducido.add(base)
            else -> exento = exento.add(base)
        }
    }
    return IvaPorAlicuota(
        general.bigDecimalMoney(),
        reducido.bigDecimalMoney(),
        exento.bigDecimalMoney(),
    )
}

internal fun alicuotaCodigo(piva: BigDecimal): String =
    when {
        isAprox(piva, BigDecimal("16")) -> ALICUOTA_GENERAL_PCT
        isAprox(piva, BigDecimal("8")) -> ALICUOTA_REDUCIDO_PCT
        else -> ALICUOTA_EXENTO_PCT
    }

internal fun isAprox(
    a: BigDecimal,
    b: BigDecimal,
): Boolean = a.setScale(2, RoundingMode.HALF_UP) == b.setScale(2, RoundingMode.HALF_UP)

internal fun BigDecimal.format(): String = setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()

/** Normaliza cualquier BigDecimal a escala monetaria estÃ¡ndar (2). */
internal fun BigDecimal.bigDecimalMoney(): BigDecimal = setScale(MONEY_SCALE, RoundingMode.HALF_UP)

internal fun formatFechaEmision(fecha: String?): String {
    if (fecha.isNullOrBlank()) {
        return java.time.LocalDate
            .now()
            .toString() + "T00:00:00"
    }
    return try {
        val d = java.time.LocalDate.parse(fecha.trim().take(ISO_DATE_LENGTH))
        d.toString() + "T00:00:00"
    } catch (_: Exception) {
        java.time.LocalDate
            .now()
            .toString() + "T00:00:00"
    }
}

/**
 * `transaccionId` determinista: SHA-256(idFactura + numeroFormateado),
 * hex truncado a 32 caracteres. Permite idempotencia y trazabilidad sin
 * exponer el idFactura en el PAC.
 */
internal fun transaccionIdDeterminista(
    idFactura: String,
    numeroFormateado: String,
): String {
    val seed = (idFactura + "|" + numeroFormateado).toByteArray(Charsets.UTF_8)
    val sha = MessageDigest.getInstance("SHA-256").digest(seed)
    // Top 16 bytes â†’ 32 chars hex.
    return java.util.HexFormat
        .of()
        .formatHex(sha.copyOfRange(0, TRANSACTION_ID_HASH_BYTES))
}

internal fun sumFormasPago(ctx: InvoiceVEContext): BigDecimal =
    ctx.formasPago
        .fold(BigDecimal.ZERO) { acc, fp -> acc.add(fp.monto) }
        .bigDecimalMoney()

internal fun sumPrecioNeto(ctx: InvoiceVEContext): BigDecimal =
    ctx.detalles
        .fold(BigDecimal.ZERO) { acc, d -> acc.add(d.totalSinIva) }
        .bigDecimalMoney()

