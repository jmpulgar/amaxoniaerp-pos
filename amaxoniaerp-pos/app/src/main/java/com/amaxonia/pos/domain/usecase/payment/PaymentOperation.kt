package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.TableAccountPayment

/**
 * External seam for the complete payment operation module.
 *
 * Callers provide the payment intent plus the sale source. The implementation owns
 * preparation, idempotency, gateway execution, persistence, printing and fiscal confirmation.
 */
fun interface PaymentOperation {
    suspend fun execute(
        request: PaymentOperationRequest,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult
}

/**
 * The payment module has two domain concepts plus one frozen execution-context snapshot.
 *
 * [context] is intentionally captured by the caller because those values are loaded when the
 * payment screen is initialized today. Re-reading them while executing would change lifecycle
 * semantics and therefore would not be a pure refactor (BLOCKER-ARCH-01 in the plan).
 */
data class PaymentOperationRequest(
    val payment: PaymentIntent,
    val source: PaymentSource,
    val context: PaymentExecutionContext,
)

/** Cashier-facing intent already normalized by the existing payment-detail builder. */
data class PaymentIntent(
    val details: PaymentDetails,
    val totalAmount: Money,
    val tenderedAmount: Money,
    val changeDue: Money,
    val condition: PaymentCondition,
)

/** Origin of the sale without leaking legacy override parameters into the caller. */
sealed interface PaymentSource {
    data class CurrentCart(
        val financialSnapshot: SaleFinancialSnapshot?,
    ) : PaymentSource

    data class TableAccount(
        val payment: com.amaxonia.pos.domain.repository.TableAccountPayment,
    ) : PaymentSource
}

/**
 * Snapshot of values whose current behavior is tied to the payment screen lifecycle.
 * It keeps the same source of truth and timing while removing legacy flow plumbing.
 */
data class PaymentExecutionContext(
    val countryCode: String,
    val availableMethods: List<FormaPago>,
    val exchangeRate: Double,
    val secondaryCurrency: String,
    val isMultiCurrency: Boolean,
)
