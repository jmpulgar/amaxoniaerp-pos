package com.amaxonia.pos.data.printer

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.usecase.payment.InvoicePrintFeedback
import com.amaxonia.pos.domain.usecase.payment.InvoicePrintGateway

class DefaultInvoicePrintGateway(
    private val printerFactory: PrinterFactory,
    private val localStore: LocalStore,
    private val salesRepository: SalesRepository,
) : InvoicePrintGateway {
    override suspend fun print(
        countryCode: String,
        transaction: Transaction,
        remoteInvoiceId: String,
    ): InvoicePrintFeedback? =
        when (countryCode) {
            "VE" -> printFiscal(transaction)
            "PA" -> printPanama(remoteInvoiceId)
            else -> null
        }

    private suspend fun printFiscal(transaction: Transaction): InvoicePrintFeedback? {
        val printer = printerFactory.getActivePrinter() ?: return null
        return printer.printReceipt(transaction).fold(
            onSuccess = { result -> InvoicePrintFeedback("Imprimiendo recibo...", result.fiscalNumber, result.printerSerial) },
            onFailure = { error -> InvoicePrintFeedback(error.message ?: "No se pudo imprimir el recibo", "", "") },
        )
    }

    private suspend fun printPanama(remoteInvoiceId: String): InvoicePrintFeedback? {
        if (localStore.readSelectedPrinterType() != PrinterType.SUNMI_V2) return null
        val printer =
            printerFactory.getActiveTicketPrinter()
                ?: return InvoicePrintFeedback(
                    "Impresora SUNMI no disponible. Puedes reintentar la impresión desde el historial.",
                    "",
                    "",
                )
        val payload =
            salesRepository.getPrintPayload(remoteInvoiceId).getOrElse { error ->
                return InvoicePrintFeedback(error.message ?: "No se pudo obtener el payload de impresión", "", "")
            }
        return when (val result = printer.printTicket(PanamaInvoiceTicketFormatter().format(payload))) {
            PrintResult.Success -> InvoicePrintFeedback("Ticket SUNMI enviado correctamente", payload.cufe.orEmpty(), "SUNMI")
            is PrintResult.Error -> InvoicePrintFeedback(result.message, payload.cufe.orEmpty(), "SUNMI")
        }
    }
}
