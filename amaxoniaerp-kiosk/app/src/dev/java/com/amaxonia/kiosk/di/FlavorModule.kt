package com.amaxonia.kiosk.di

import com.amaxonia.kiosk.domain.payment.DevMockPaymentTerminal
import com.amaxonia.kiosk.domain.payment.PaymentTerminal

/** `dev` flavor bindings: a simulated card terminal so the full flow can be exercised on an emulator. */
object FlavorModule {
    fun paymentTerminal(): PaymentTerminal = DevMockPaymentTerminal()
}
