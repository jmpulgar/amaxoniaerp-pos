package com.amaxonia.erp.data.printer

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.erp.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.domain.model.printer.PrintResult
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.domain.model.printer.TicketDocument
import com.amaxonia.erp.domain.repository.PrinterProvider
import com.amaxonia.erp.domain.repository.SalesRepository

data class InvoicePrintFeedback(
    val displayMessage: String,
    val fiscalNumber: String = "",
    val printerSerial: String = "",
    val isSuccess: Boolean = true,
)

class DefaultInvoicePrintGateway(
    private val printerProvider: PrinterProvider,
    private val localStore: LocalStore,
    private val salesRepository: SalesRepository,
    private val panamaFormatter: PanamaInvoiceTicketFormatter = PanamaInvoiceTicketFormatter(),
    private val venezuelaFormatter: VenezuelaInvoiceTicketFormatter = VenezuelaInvoiceTicketFormatter(),
) {
    suspend fun print(
        countryCode: String,
        facturaId: String,
        fallbackPayload: FacturaPrintPayloadDto? = null,
    ): InvoicePrintFeedback? {
        val printerType = localStore.readSelectedPrinterType()
        if (printerType == PrinterType.NONE) return null

        val payload =
            if (facturaId.isNotBlank()) {
                salesRepository.getPrintPayload(facturaId).getOrNull() ?: fallbackPayload
            } else {
                fallbackPayload
            } ?: return InvoicePrintFeedback("No se pudo obtener información de impresión", isSuccess = false)

        return when (countryCode.uppercase()) {
            "VE" -> {
                when (printerType) {
                    PrinterType.THE_FACTORY_HKA -> {
                        val fiscalPrinter = printerProvider.getActivePrinter()
                            ?: return InvoicePrintFeedback("Impresora fiscal HKA no disponible", isSuccess = false)
                        fiscalPrinter.printReceipt(payload).fold(
                            onSuccess = { res ->
                                InvoicePrintFeedback(
                                    displayMessage = "Factura fiscal emitida exitosamente",
                                    fiscalNumber = res.fiscalNumber,
                                    printerSerial = res.printerSerial,
                                    isSuccess = true,
                                )
                            },
                            onFailure = { err ->
                                InvoicePrintFeedback(
                                    displayMessage = err.message ?: "Error al emitir en impresora fiscal",
                                    isSuccess = false,
                                )
                            },
                        )
                    }
                    PrinterType.SUNMI_V2,
                    PrinterType.IMIN_SWIFT,
                    PrinterType.GENERIC_BLUETOOTH,
                    -> printTicket(venezuelaFormatter.format(payload))
                    else -> null
                }
            }
            "PA" -> {
                when (printerType) {
                    PrinterType.SUNMI_V2,
                    PrinterType.IMIN_SWIFT,
                    PrinterType.GENERIC_BLUETOOTH,
                    -> printTicket(panamaFormatter.format(payload, "PA"))
                    else -> null
                }
            }
            else -> printTicket(panamaFormatter.format(payload, countryCode))
        }
    }

    private suspend fun printTicket(ticketDoc: TicketDocument): InvoicePrintFeedback {
        val ticketPrinter = printerProvider.getActiveTicketPrinter()
            ?: return InvoicePrintFeedback("Impresora de tickets no disponible o no conectada", isSuccess = false)
        val result = ticketPrinter.printTicket(ticketDoc)
        return when (result) {
            is PrintResult.Success ->
                InvoicePrintFeedback("Ticket impreso exitosamente", isSuccess = true)
            is PrintResult.Error ->
                InvoicePrintFeedback(result.message, isSuccess = false)
        }
    }
}
