package com.amaxonia.pos.domain.model

enum class TransactionStatus(val label: String, val colorHex: Long) {
    PAID("PAGADO", 0xFF1565C0),
    PENDING("PENDIENTE", 0xFFFFA000),
    CANCELLED("ANULADO", 0xFFD32F2F)
}

data class Transaction(
    val id: String,
    val invoiceNumber: String,
    val time: String,
    val amount: Double,
    val currency: String = "USD",
    val fiscalAmountBs: Double? = null,
    val status: TransactionStatus = TransactionStatus.PAID,
    val dateHeader: String,
    val clienteNombre: String = "",
    val clienteIdentificacion: String = "",
    val formaPago: String = "",
    val paymentMethods: List<TransactionPaymentMethod> = emptyList(),
    val fiscalItems: List<TransactionFiscalItem> = emptyList(),
)

data class TransactionPaymentMethod(
    val description: String = "",
    val sigla: String = "",
    val amount: Double = 0.0,
    val fiscalCode: String = "",
    val gatewayCommandPrefix: String = ""
)

data class TransactionFiscalItem(
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPriceWithoutTax: Double = 0.0,
    val iva: Double = 0.0,
)
