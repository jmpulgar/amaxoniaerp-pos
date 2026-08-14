package com.amaxonia.pos.ui.common

import com.amaxonia.pos.domain.usecase.payment.DefaultPaymentOperation
import com.amaxonia.pos.domain.usecase.payment.PaymentOperation
import kotlinx.coroutines.flow.first

private val canonicalPaymentOperation: PaymentOperation by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultPaymentOperation(
        executeFlow = { input, onEvent -> DependencyContainer.executePaymentFlowUseCase(input, onEvent) },
        printerTypeProvider = { DependencyContainer.posConfigurationRepository.selectedPrinterType.first() },
    )
}

/** Canonical payment capability exposed by the composition root. */
val DependencyContainer.paymentOperation: PaymentOperation
    get() = canonicalPaymentOperation
