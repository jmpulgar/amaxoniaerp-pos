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
            VENEZUELA_CODE ->
                when (localStore.readSelectedPrinterType()) {
                    PrinterType.THE_FACTORY_HKA -> printFiscal(transaction)
                    PrinterType.SUNMI_V2 -> printSunmi(remoteInvoiceId, countryCode)
                    else -> null
                }
            PANAMA_CODE -> printSunmi(remoteInvoiceId, countryCode)
            else -> null
        }

    private suspend fun printFiscal(transaction: Transaction): InvoicePrintFeedback? {
        val printer = printerFactory.getActivePrinter() ?: return null
        return printer.printReceipt(transaction).fold(
            onSuccess = { result -> InvoicePrintFeedback("Imprimiendo recibo...", result.fiscalNumber, result.printerSerial) },
            onFailure = { error -> InvoicePrintFeedback(error.message ?: "No se pudo imprimir el recibo", "", "") },
        )
    }

    private suspend fun printSunmi(
        remoteInvoiceId: String,
        countryCode: String,
    ): InvoicePrintFeedback? {
        val printer = printerFactory.getActiveTicketPrinter()
        return when {
            localStore.readSelectedPrinterType() != PrinterType.SUNMI_V2 -> null
            printer == null ->
                InvoicePrintFeedback(
                    "Impresora SUNMI no disponible. Puedes reintentar la impresión desde el historial.",
                    "",
                    "",
                )
            else ->
                salesRepository.getPrintPayload(remoteInvoiceId).fold(
                    onSuccess = { payload ->
                        when (val result = printer.printTicket(PanamaInvoiceTicketFormatter().format(payload, countryCode))) {
                            PrintResult.Success ->
                                InvoicePrintFeedback("Ticket SUNMI enviado correctamente", payload.cufe.orEmpty(), "SUNMI")
                            is PrintResult.Error -> InvoicePrintFeedback(result.message, payload.cufe.orEmpty(), "SUNMI")
                        }
                    },
                    onFailure = { error ->
                        InvoicePrintFeedback(error.message ?: "No se pudo obtener el payload de impresión", "", "")
                    },
                )
        }
    }

    private companion object {
        const val PANAMA_CODE = "PA"
        const val VENEZUELA_CODE = "VE"
    }
}
