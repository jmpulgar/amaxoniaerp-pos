package com.amaxonia.pos.ui.payment

data class PaymentSuccessPayload(
    val changeDue: Double,
    val paymentMethodsLabel: String,
    val codFactura: String,
    val transactionId: String,
    val receiptPrintMessage: String?
)
