package com.amaxonia.pos.domain.usecase

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class PromotionCharacterizationTest {
    private val now = LocalDateTime.of(2026, 7, 12, 12, 0)

    @Test
    fun acceptsActivePromotionInsideConfiguredWindow() {
        val result =
            ValidarAdicionPromocionUseCase()(
                promotion(inicio = "2026-07-01 00:00:00", fin = "2026-07-31 23:59:59"),
                emptyList(),
                now,
            )

        assertTrue(result.isSuccess)
    }

    @Test
    fun rejectsInactiveFutureExpiredAndDuplicatePromotions() {
        val validator = ValidarAdicionPromocionUseCase()

        assertEquals("La promoción no está activa", validator(promotion(activo = false), emptyList(), now).exceptionOrNull()?.message)
        assertEquals(
            "La promoción aún no ha iniciado",
            validator(promotion(inicio = "2026-08-01 00:00:00"), emptyList(), now).exceptionOrNull()?.message,
        )
        assertEquals(
            "La promoción ya venció",
            validator(promotion(fin = "2026-07-01 00:00:00"), emptyList(), now).exceptionOrNull()?.message,
        )
        val duplicate = CartItem(Product(id = "p"), promocionId = "promo-1")
        assertEquals("Esta promoción ya está en el carrito", validator(promotion(), listOf(duplicate), now).exceptionOrNull()?.message)
    }

    private fun promotion(
        activo: Boolean = true,
        inicio: String? = null,
        fin: String? = null,
    ): Promocion =
        Promocion(
            id = "promo-1",
            codigo = "PROMO",
            inicio = inicio,
            fin = fin,
            nombre = "Promoción",
            imagen = "",
            descuentoGlobal = BigDecimal.ZERO,
            idItem = "p",
            activo = activo,
            detalles = emptyList(),
        )
}
