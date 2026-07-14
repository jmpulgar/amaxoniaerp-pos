package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.GatewayPaymentRequest
import com.amaxonia.pos.domain.repository.PaymentGateway

class ExecuteGatewayPaymentUseCase(
    private val gateway: PaymentGateway,
) {
    suspend fun validateConfiguration(methods: List<TransactionPaymentMethod>): Result<Unit> = gateway.validateConfiguration(methods)

    suspend operator fun invoke(
        request: GatewayPaymentRequest,
        launch: suspend (GatewayLaunchPayload) -> Unit,
    ): Result<Unit> {
        var result = Result.success(Unit)
        request.methods.forEach { method ->
            if (result.isSuccess) {
                result = executeMethod(request, method, launch)
            }
        }
        return result
    }

    private suspend fun executeMethod(
        request: GatewayPaymentRequest,
        method: TransactionPaymentMethod,
        launch: suspend (GatewayLaunchPayload) -> Unit,
    ): Result<Unit> =
        gateway
            .prepare(
                method = method,
                customerIdentifier = request.customerIdentifier,
                exchangeRate = request.exchangeRate,
                isMultiCurrency = request.isMultiCurrency,
            ).fold(
                onFailure = Result.Companion::failure,
                onSuccess = { payload ->
                    if (payload == null) {
                        Result.success(Unit)
                    } else {
                        launch(payload)
                        val approval = gateway.awaitApproval()
                        if (approval.approved) Result.success(Unit) else Result.failure(IllegalStateException(approval.message))
                    }
                },
            )
}
