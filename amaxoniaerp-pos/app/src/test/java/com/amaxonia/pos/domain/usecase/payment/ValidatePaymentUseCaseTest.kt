package com.amaxonia.pos.domain.usecase.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatePaymentUseCaseTest {
    private val useCase = ValidatePaymentUseCase()

    @Test
    fun `credit condition requires a client that allows credit`() {
        assertEquals(
            PaymentValidationFailure.CreditNotAllowed,
            useCase.validatePaymentCondition(PaymentCondition.CREDITO, clientAllowsCredit = false),
        )
        assertNull(useCase.validatePaymentCondition(PaymentCondition.CREDITO, clientAllowsCredit = true))
        assertNull(useCase.validatePaymentCondition(PaymentCondition.CONTADO, clientAllowsCredit = false))
    }

    @Test
    fun `sale context preserves validation priority`() {
        assertEquals(
            PaymentValidationFailure.EmptyCart,
            useCase.validateSaleContext(itemCount = 0, hasClient = false, hasCaja = false),
        )
        assertEquals(
            PaymentValidationFailure.MissingClient,
            useCase.validateSaleContext(itemCount = 1, hasClient = false, hasCaja = false),
        )
        assertEquals(
            PaymentValidationFailure.MissingCaja,
            useCase.validateSaleContext(itemCount = 1, hasClient = true, hasCaja = false),
        )
        assertNull(useCase.validateSaleContext(itemCount = 1, hasClient = true, hasCaja = true))
    }

    @Test
    fun `offline sale accepts missing remote sequence but never unsynchronized items`() {
        assertNull(useCase.validateSaleReadiness(isOnline = false, hasCajaSequence = false, invalidItemCount = 0))
        assertEquals(
            PaymentValidationFailure.UnsynchronizedItems,
            useCase.validateSaleReadiness(isOnline = false, hasCajaSequence = false, invalidItemCount = 1),
        )
    }

    @Test
    fun `online sale requires an active caja sequence before checking items`() {
        assertEquals(
            PaymentValidationFailure.MissingCajaSequence,
            useCase.validateSaleReadiness(isOnline = true, hasCajaSequence = false, invalidItemCount = 1),
        )
    }
}
