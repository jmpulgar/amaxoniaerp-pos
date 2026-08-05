package com.amaxonia.pos.data.printer

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
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
                        // FASE 2 (Punto 3) — Selector por país para SUNMI_V2, preservando
                        // el comportamiento anterior como fallback explícito.
                        //   - THE_FACTORY_HKA (cualquier país) → nunca llega aquí: se enruta a
                        //     `printFiscal` con comandos nativos HKA-20.
                        //   - VE  → VenezuelaInvoiceTicketFormatter (factura digital HKA, 40 cols)
                        //   - PA  → PanamaInvoiceTicketFormatter (CAFE/DGI/QR)
                        //   - OTRO → se conserva el formatter por defecto que tenía el sistema
                        //            antes de esta integración (PanamaInvoiceTicketFormatter),
                        //            para NO romper países distintos de VE/PA.
                        val ticket =
                            when (countryCode.uppercase()) {
                                VENEZUELA_CODE -> VenezuelaInvoiceTicketFormatter().format(payload)
                                PANAMA_CODE -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
                                else -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
                            }
                        when (val result = printer.printTicket(ticket)) {
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
