package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import java.math.BigDecimal

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
    val receiptPrintMessage: String? = null
) {
    val totalAmountMoney: BigDecimal
        get() = Money.fromDouble(totalAmount)

    val tenderedAmountMoney: BigDecimal
        get() = Money.parse(tenderedAmountInput)

    val formasPagoEfectivo: List<FormaPago>
        get() = formasPago.filter { it.siglas.equals("CASH", ignoreCase = true) }

    val formasPagoTarjetaOtro: List<FormaPago>
        get() = formasPago.filterNot { it.siglas.equals("CASH", ignoreCase = true) }

    val nonCashAssignedMoney: BigDecimal
        get() = nonCashAmountsInput.values.fold(BigDecimal.ZERO) { acc, amount ->
            acc + Money.parse(amount)
        }

    val nonCashPendingMoney: BigDecimal
        get() = (totalAmountMoney - nonCashAssignedMoney).coerceAtLeast(BigDecimal.ZERO)

    val changeDueMoney: BigDecimal
        get() = (tenderedAmountMoney - totalAmountMoney).coerceAtLeast(BigDecimal.ZERO)

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
        get() = Money.toDouble(nonCashAssignedMoney)

    val changeDue: Double
        get() = Money.toDouble(changeDueMoney)

    val isPaymentEnough: Boolean
        get() = when (selectedMethod) {
            PaymentMethod.CASH -> tenderedAmountMoney >= totalAmountMoney
            PaymentMethod.NON_CASH -> nonCashAssignedMoney >= totalAmountMoney
        }
}

private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal {
    return if (this < min) min else this
}

enum class PaymentMethod {
    CASH, NON_CASH
}

data class PaymentSuccessPayload(
    val changeDue: Double,
    val paymentMethodsLabel: String,
    val codFactura: String,
    val transactionId: String,
    val receiptPrintMessage: String? = null
)
