package com.amaxonia.pos.domain.usecase.payment

import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

class HandlePaymentFailureUseCase {
    operator fun invoke(
        error: Throwable?,
        fallbackMessage: String,
    ): PaymentFailure {
        val message = error?.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
        return if (error is IOException || error is TimeoutCancellationException) {
            PaymentFailure.Recoverable(message, error)
        } else {
            PaymentFailure.Permanent(message, error)
        }
    }
}

sealed class PaymentFailure(
    val message: String,
    val cause: Throwable?,
) {
    class Recoverable(
        message: String,
        cause: Throwable?,
    ) : PaymentFailure(message, cause)

    class Permanent(
        message: String,
        cause: Throwable?,
    ) : PaymentFailure(message, cause)
}
