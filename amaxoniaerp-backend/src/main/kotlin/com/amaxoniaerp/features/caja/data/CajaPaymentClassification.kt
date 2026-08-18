package com.amaxoniaerp.features.caja.data

private val CASH_CODES = setOf("CASH", "EF", "EFE", "EFECTIVO")
private val CARD_CODES = setOf("TDC", "TARJETA", "PV", "POS", "NEQ", "DB", "DEBITO", "CR", "CREDITO")

internal fun isCancelledStatus(description: String?): Boolean {
    if (description.isNullOrBlank()) return false
    return description.equals("Anulada", ignoreCase = true) ||
        description.equals("Anulado", ignoreCase = true)
}

internal fun isCashSigla(siglas: String?): Boolean {
    val value = siglas.orEmpty().trim().uppercase()
    return value == "CASH" || value == "EF" || value == "EFE" || value == "EFECTIVO"
}

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
    return if (isCard) {
        PaymentCategory.CARD
    } else {
        PaymentCategory.OTHER
    }
}
