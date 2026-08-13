package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.usecase.payment.PaymentCondition

data class PaymentState(
    val totalAmount: Double = 0.0,
    val tenderedAmountInput: String = "0",
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentCondition: PaymentCondition = PaymentCondition.CONTADO,
    val canUseCredit: Boolean = false,
    val isSuccess: Boolean = false,
    val formasPago: List<FormaPago> = emptyList(),
    val nonCashAmountsInput: Map<Int, String> = emptyMap(),
    val isLoadingFormasPago: Boolean = false,
    val formasPagoError: String? = null,
    val lastFormapagoDetalle: FormapagoDetallePayload? = null,
    val isProcessingPayment: Boolean = false,
    val paymentError: String? = null,
    val showInsufficientReminder: Boolean = false,
    val receiptPrintMessage: String? = null,
    val gatewayStatusMessage: String? = null,
    val successPayload: PaymentSuccessPayload? = null,
    val duplicateInvoice: DuplicateInvoicePrompt? = null,
    val tasa: Double = 0.0,
    val abrMonedaSecundaria: String = "",
    val isMultiCurrency: Boolean = false,
) {
    val totalAmountMoney: Money
        get() = Money.fromDouble(totalAmount)

    val tenderedAmountMoney: Money
        get() = Money.parse(tenderedAmountInput)

    val formasPagoEfectivo: List<FormaPago>
        get() = formasPago.filter { it.siglas.equals("CASH", ignoreCase = true) }

    val formasPagoTarjetaOtro: List<FormaPago>
        get() =
            formasPago
                .filterNot { it.siglas.equals("CASH", ignoreCase = true) }
                .filter { paymentCondition == PaymentCondition.CREDITO || !it.isCxc() }

    val nonCashAssignedMoney: Money
        get() =
            nonCashAmountsInput.values.fold(Money.ZERO) { acc, amount ->
                acc + Money.parse(amount)
            }

    val assignedAmountMoney: Money
        get() = tenderedAmountMoney + nonCashAssignedMoney

    val nonCashPendingMoney: Money
        get() = (totalAmountMoney - assignedAmountMoney).coerceAtLeastZero()

    val changeDueMoney: Money
        get() = (assignedAmountMoney - totalAmountMoney).coerceAtLeastZero()

    val totalAmountText: String
        get() = Money.format(totalAmountMoney)

    val tenderedAmountText: String
        get() = Money.format(tenderedAmountMoney)

    val nonCashAssignedText: String
        get() = Money.format(nonCashAssignedMoney)

    val nonCashPendingText: String
        get() = Money.format(nonCashPendingMoney)

    val changeDueText: String
        get() = Money.format(changeDueMoney)

    val nonCashAssignedTotal: Double
        get() = nonCashAssignedMoney.toDouble()

    val changeDue: Double
        get() = changeDueMoney.toDouble()

    val isPaymentEnough: Boolean
        get() = assignedAmountMoney >= totalAmountMoney

    fun toBs(amount: Double): Double {
        if (!isMultiCurrency || tasa <= 0.0) return 0.0
        return amount * tasa
    }

    /**
     * Multi-currency conversion performed entirely in [Money] (BigDecimal)
     * so no `Double` arithmetic leaks into monetary calculations
     * (auditoría ítem 8 / MONEY-001). The legacy [tasa] field is kept as
     * the source of truth for the rate; only this boundary translates it
     * into a [BigDecimal] multiplier with the currency's canonical scale.
     */
    private fun toBsMoney(amount: Money): Money {
        if (!isMultiCurrency || tasa <= 0.0) return Money.ZERO
        val rate = java.math.BigDecimal.valueOf(tasa)
        return amount.times(rate)
    }

    private fun formatBs(amount: Money): String {
        if (!isMultiCurrency || tasa <= 0.0) return ""
        val rate = java.math.BigDecimal.valueOf(tasa)
        val converted = amount.toBigDecimal().multiply(rate).setScale(Money.SCALE, Money.ROUNDING_MODE)
        return String.format(java.util.Locale.US, "%.2f", converted)
    }

    val totalAmountBsText: String
        get() = formatBs(totalAmountMoney)

    val tenderedAmountBsText: String
        get() = formatBs(tenderedAmountMoney)

    val changeDueBsText: String
        get() = formatBs(changeDueMoney)

    val nonCashAssignedBsText: String
        get() = formatBs(nonCashAssignedMoney)

    val nonCashPendingBsText: String
        get() = formatBs(nonCashPendingMoney)

    val missingCashBsText: String
        get() {
            if (!isMultiCurrency || tasa <= 0.0) return ""
            val missing = (totalAmountMoney - assignedAmountMoney).coerceAtLeastZero()
            return formatBs(missing)
        }

    val totalAmountBs: Double
        get() = toBsMoney(totalAmountMoney).toDouble()

    val changeDueBs: Double
        get() = toBsMoney(changeDueMoney).toDouble()

    val monedaSecundariaLabel: String
        get() = formatCurrencyLabel(abrMonedaSecundaria)
}

fun formatCurrencyLabel(abr: String): String {
    val normalized = abr.uppercase().replace(".", "").trim()
    return when (normalized) {
        "BS", "VES", "BSF", "BSFV", "BVES" -> "Bs."
        "USD" -> "$"
        "PAB" -> "B/."
        else -> "$"
    }
}

enum class PaymentMethod {
    CASH,
    NON_CASH,
}

private fun FormaPago.isCxc(): Boolean = siglas?.trim()?.equals("CXC", ignoreCase = true) == true

sealed interface PaymentUiAction {
    data class SetTotalAmount(
        val amount: Double,
    ) : PaymentUiAction

    data class KeyPadInput(
        val key: String,
    ) : PaymentUiAction

    data object SetExactAmount : PaymentUiAction

    data class SelectMethod(
        val method: PaymentMethod,
    ) : PaymentUiAction

    data class SelectCondition(
        val condition: PaymentCondition,
    ) : PaymentUiAction

    data class SetExactNonCashAmount(
        val paymentMethodId: Int,
    ) : PaymentUiAction

    data class SetNonCashAmount(
        val paymentMethodId: Int,
        val amount: String,
    ) : PaymentUiAction

    data object ProcessPayment : PaymentUiAction

    data object ClearPaymentError : PaymentUiAction

    data object ClearReceiptPrintMessage : PaymentUiAction

    data object ClearSuccessPayload : PaymentUiAction

    data object DismissDuplicateInvoice : PaymentUiAction
}

sealed interface PaymentUiEffect {
    data class LaunchGateway(
        val payload: GatewayLaunchPayload,
    ) : PaymentUiEffect
}

/**
 * Prompt surfaced when the backend returned HTTP 409 Conflict for a sale
 * whose [clientCorrelationId] (`idFactura`) was already processed in a prior
 * attempt. The backend does not return the existing invoice body, so the user
 * must either reconcile (look up the prior invoice) or escalate to manual
 * review. Never offers an automatic "anular" because reversing the prior
 * invoice without operator confirmation is unsafe.
 */
data class DuplicateInvoicePrompt(
    val clientCorrelationId: String,
    val reason: String,
)
