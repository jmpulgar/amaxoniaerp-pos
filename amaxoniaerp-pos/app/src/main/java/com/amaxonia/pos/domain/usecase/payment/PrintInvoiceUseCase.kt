package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Transaction

data class InvoicePrintFeedback(
    val displayMessage: String,
    val fiscalNumber: String,
    val printerSerial: String,
)

fun interface InvoicePrintGateway {
    suspend fun print(
        countryCode: String,
        transaction: Transaction,
        remoteInvoiceId: String,
    ): InvoicePrintFeedback?
}

class PrintInvoiceUseCase(
    private val gateway: InvoicePrintGateway,
) {
    suspend operator fun invoke(
        countryCode: String,
        transaction: Transaction,
        remoteInvoiceId: String,
    ): InvoicePrintFeedback? = gateway.print(countryCode, transaction, remoteInvoiceId)
}
