package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload

data class PaymentState(
    val totalAmount: Double = 0.0,
    val tenderedAmountInput: String = "0",
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
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
        get() = formasPago.filterNot { it.siglas.equals("CASH", ignoreCase = true) }

    val nonCashAssignedMoney: Money
        get() =
            nonCashAmountsInput.values.fold(Money.ZERO) { acc, amount ->
                acc + Money.parse(amount)
            }

    val nonCashPendingMoney: Money
        get() = (totalAmountMoney - nonCashAssignedMoney).coerceAtLeastZero()

    val changeDueMoney: Money
        get() = (tenderedAmountMoney - totalAmountMoney).coerceAtLeastZero()

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
        get() =
            when (selectedMethod) {
                PaymentMethod.CASH -> tenderedAmountMoney >= totalAmountMoney
                PaymentMethod.NON_CASH -> nonCashAssignedMoney >= totalAmountMoney
            }

    fun toBs(amount: Double): Double {
        if (!isMultiCurrency || tasa <= 0.0) return 0.0
        return amount * tasa
    }

    val totalAmountBsText: String
        get() = if (isMultiCurrency && tasa > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", totalAmount * tasa) else ""

    val tenderedAmountBsText: String
        get() =
            if (isMultiCurrency &&
                tasa > 0.0
            ) {
                String.format(java.util.Locale.getDefault(), "%.2f", tenderedAmountMoney.toDouble() * tasa)
            } else {
                ""
            }

    val changeDueBsText: String
        get() = if (isMultiCurrency && tasa > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", changeDue * tasa) else ""

    val nonCashAssignedBsText: String
        get() = if (isMultiCurrency && tasa > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", nonCashAssignedTotal * tasa) else ""

    val nonCashPendingBsText: String
        get() =
            if (isMultiCurrency &&
                tasa > 0.0
            ) {
                String.format(java.util.Locale.getDefault(), "%.2f", nonCashPendingMoney.toDouble() * tasa)
            } else {
                ""
            }

    val missingCashBsText: String
        get() {
            if (!isMultiCurrency || tasa <= 0.0) return ""
            val missing = (totalAmountMoney - tenderedAmountMoney).coerceAtLeastZero()
            return String.format(java.util.Locale.getDefault(), "%.2f", missing.toDouble() * tasa)
        }

    val totalAmountBs: Double
        get() = toBs(totalAmount)

    val changeDueBs: Double
        get() = toBs(changeDue)

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
}

sealed interface PaymentUiEffect {
    data class LaunchGateway(
        val payload: GatewayLaunchPayload,
    ) : PaymentUiEffect
}
