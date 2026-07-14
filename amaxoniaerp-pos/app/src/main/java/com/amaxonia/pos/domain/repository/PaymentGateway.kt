package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.payment.GatewayApproval
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload

interface PaymentGateway {
    suspend fun validateConfiguration(methods: List<TransactionPaymentMethod>): Result<Unit>

    suspend fun prepare(
        method: TransactionPaymentMethod,
        customerIdentifier: String,
        exchangeRate: Double,
        isMultiCurrency: Boolean,
    ): Result<GatewayLaunchPayload?>

    suspend fun awaitApproval(): GatewayApproval
}
