package com.amaxonia.pos.domain.usecase

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.PromocionDetalle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ValidarAdicionPromocionUseCase {
    operator fun invoke(
        promocion: Promocion,
        cartItems: List<CartItem>,
        now: LocalDateTime = LocalDateTime.now(),
    ): Result<Unit> {
        val inicio = promocion.inicio.toLocalDateTimeOrNull()
        val fin = promocion.fin.toLocalDateTimeOrNull()
        val failureMessage =
            when {
                !promocion.activo -> "La promoción no está activa"
                inicio != null && now.isBefore(inicio) -> "La promoción aún no ha iniciado"
                fin != null && now.isAfter(fin) -> "La promoción ya venció"
                cartItems.any { it.promocionId == promocion.id } -> "Esta promoción ya está en el carrito"
                else -> null
            }
        return failureMessage
            ?.let { Result.failure(IllegalStateException(it)) }
            ?: Result.success(Unit)
    }
}

object PromotionPriceCalculator {
    fun totalConIva(detalle: PromocionDetalle): BigDecimal = detalle.totalConIva.money()

    fun totalSinIva(detalle: PromocionDetalle): BigDecimal = (detalle.totalConIva - detalle.impuesto).money()

    fun iva(detalle: PromocionDetalle): BigDecimal = detalle.iva.money()
}

object BigDecimalMoneyFormatter {
    fun money(value: BigDecimal): String = "$ ${value.money().toPlainString()}"
}

fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

private fun String?.toLocalDateTimeOrNull(): LocalDateTime? {
    if (isNullOrBlank()) return null
    val normalized = replace("T", " ").substringBefore(".")
    return runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
        .recoverCatching { LocalDateTime.parse(normalized) }
        .getOrNull()
}
