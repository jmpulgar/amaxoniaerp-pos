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
    val receiptPrintMessage: String? = null,
    /** Status message shown while waiting for gateway (e.g. "Esperando respuesta de pasarela...") */
    val gatewayStatusMessage: String? = null
)
