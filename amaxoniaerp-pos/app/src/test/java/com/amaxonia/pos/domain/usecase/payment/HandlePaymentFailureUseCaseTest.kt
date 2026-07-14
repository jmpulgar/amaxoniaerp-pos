package com.amaxonia.pos.domain.usecase.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HandlePaymentFailureUseCaseTest {
    private val useCase = HandlePaymentFailureUseCase()

    @Test
    fun `network errors remain visible and recoverable`() {
        val result = useCase(IOException("red no disponible"), "falló el pago")

        assertTrue(result is PaymentFailure.Recoverable)
        assertEquals("red no disponible", result.message)
    }

    @Test
    fun `blank infrastructure errors use the explicit fallback`() {
        val result = useCase(IllegalStateException(""), "respuesta inválida de pasarela")

        assertTrue(result is PaymentFailure.Permanent)
        assertEquals("respuesta inválida de pasarela", result.message)
    }
}
