package com.amaxonia.kiosk.di

import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import com.amaxonia.kiosk.domain.payment.UnavailablePaymentTerminal

/**
 * `prod` flavor bindings. No certified card terminal adapter is integrated yet (ADR-009 D1), so the
 * terminal reports itself unavailable and CARD is hidden; Yappy keeps working through the backend.
 */
object FlavorModule {
    fun paymentTerminal(): PaymentTerminal = UnavailablePaymentTerminal()
}
