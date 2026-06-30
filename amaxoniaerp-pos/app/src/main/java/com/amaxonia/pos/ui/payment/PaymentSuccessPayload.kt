package com.amaxonia.pos.ui.payment

@kotlinx.serialization.Serializable
data class PaymentSuccessPayload(
    val changeDue: Double,
    val paymentMethodsLabel: String,
    val codFactura: String,
    val transactionId: String,
    val receiptPrintMessage: String? = null,
    val fiscalNumber: String = "",
    val totalBs: Double = 0.0,
    val changeDueBs: Double = 0.0,
    val tasa: Double = 0.0,
    val abrMonedaSecundaria: String = "",
    val isMultiCurrency: Boolean = false,
    val feError: String? = null,
)
