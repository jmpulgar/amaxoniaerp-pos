package com.amaxonia.pos.domain.model

enum class TransactionStatus(
    val label: String,
    val colorHex: Long,
) {
    PAID("PAGADO", 0xFF1565C0),
    PENDING("PENDIENTE", 0xFFFFA000),
    CANCELLED("ANULADO", 0xFFD32F2F),
}

enum class ElectronicInvoiceStatus(
    val label: String,
    val colorHex: Long,
) {
    PENDING("FE Pendiente", 0xFFFFA000),
    FAILED("FE Fallida", 0xFFD32F2F),
    SUCCESS("FE Exitosa", 0xFF388E3C),
    NONE("", 0x00000000),
}

/**
 * Q3: criterio FE unificado con el Web. EXITOSA exige CUFE (el backend PA
 * expone `codigoFiscal` = cufe); sin CUFE la factura es PENDIENTE o FALLIDA
 * y debe quedar disponible para reenvío, aunque ya tenga número fiscal.
 */
fun resolveElectronicInvoiceStatus(
    estatus: String,
    transactionStatus: TransactionStatus,
    codigoFiscal: String,
): ElectronicInvoiceStatus =
    when {
        codigoFiscal.isNotBlank() -> ElectronicInvoiceStatus.SUCCESS
        estatus.equals("Fallida", ignoreCase = true) || estatus.contains("Error", ignoreCase = true) ->
            ElectronicInvoiceStatus.FAILED
        transactionStatus == TransactionStatus.PAID -> ElectronicInvoiceStatus.PENDING
        else -> ElectronicInvoiceStatus.NONE
    }

data class Transaction(
    val id: String,
    val invoiceNumber: String,
    val time: String,
    val amount: Double,
    val currency: String = "USD",
    val fiscalAmount: Double? = null,
    val status: TransactionStatus = TransactionStatus.PAID,
    val electronicStatus: ElectronicInvoiceStatus = ElectronicInvoiceStatus.NONE,
    val codigoFiscal: String = "",
    val numeroDocumentoFiscal: String = "",
    val fechaDgi: String = "",
    val dateHeader: String,
    val clienteNombre: String = "",
    val clienteIdentificacion: String = "",
    val formaPago: String = "",
    val paymentMethods: List<TransactionPaymentMethod> = emptyList(),
    val fiscalItems: List<TransactionFiscalItem> = emptyList(),
    val totalRef: Double? = null,
    val abrMonedaSecundaria: String? = null,
)

data class TransactionPaymentMethod(
    val description: String = "",
    val sigla: String = "",
    val amount: Double = 0.0,
    val fiscalCode: String = "",
    val gatewayCommandPrefix: String = "",
)

data class TransactionFiscalItem(
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPriceWithoutTax: Double = 0.0,
    val iva: Double = 0.0,
)

fun Transaction.isOfflinePending(): Boolean =
    id.startsWith("OFF-") ||
        invoiceNumber.startsWith("OFF-") ||
        status == TransactionStatus.PENDING

