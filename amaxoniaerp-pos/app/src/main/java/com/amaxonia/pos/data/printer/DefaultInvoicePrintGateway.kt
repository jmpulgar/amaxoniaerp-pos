package com.amaxonia.pos.data.printer

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readActiveCajaForToday
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.readSelectedPrinterType
import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.usecase.payment.InvoicePrintFeedback
import com.amaxonia.pos.domain.usecase.payment.InvoicePrintGateway

class DefaultInvoicePrintGateway(
    private val printerProvider: PrinterProvider,
    private val localStore: LocalStore,
    private val salesRepository: SalesRepository,
) : InvoicePrintGateway {
    override suspend fun print(
        countryCode: String,
        transaction: Transaction,
        remoteInvoiceId: String,
    ): InvoicePrintFeedback? =
        when (countryCode.uppercase()) {
            VENEZUELA_CODE ->
                when (localStore.readSelectedPrinterType()) {
                    PrinterType.THE_FACTORY_HKA -> printFiscal(transaction)
                    PrinterType.SUNMI_V2 -> printSunmi(remoteInvoiceId, countryCode, transaction)
                    else -> null
                }
            PANAMA_CODE ->
                when (localStore.readSelectedPrinterType()) {
                    PrinterType.SUNMI_V2 -> printSunmi(remoteInvoiceId, countryCode, transaction)
                    else -> null
                }
            else -> null
        }

    private suspend fun printFiscal(transaction: Transaction): InvoicePrintFeedback? {
        val printer = printerProvider.getActivePrinter() ?: return null
        return printer.printReceipt(transaction).fold(
            onSuccess = { result ->
                InvoicePrintFeedback(
                    displayMessage = "Imprimiendo recibo...",
                    fiscalNumber = result.fiscalNumber,
                    printerSerial = result.printerSerial,
                    isSuccess = true,
                )
            },
            onFailure = { error ->
                InvoicePrintFeedback(
                    displayMessage = error.message ?: "No se pudo imprimir el recibo",
                    fiscalNumber = "",
                    printerSerial = "",
                    isSuccess = false,
                )
            },
        )
    }

    private suspend fun printSunmi(
        remoteInvoiceId: String,
        countryCode: String,
        transaction: Transaction? = null,
    ): InvoicePrintFeedback? {
        val printer = printerProvider.getActiveTicketPrinter()
        return when {
            localStore.readSelectedPrinterType() != PrinterType.SUNMI_V2 -> null
            printer == null ->
                InvoicePrintFeedback(
                    "Impresora SUNMI no disponible. Puedes reintentar la impresión desde el historial.",
                    "",
                    "",
                    isSuccess = false,
                )
            else -> {
                val payloadResult =
                    if (remoteInvoiceId.isNotBlank() && !remoteInvoiceId.startsWith("OFF-")) {
                        salesRepository.getPrintPayload(remoteInvoiceId)
                    } else {
                        Result.failure(IllegalStateException("Factura offline"))
                    }

                val payload =
                    payloadResult.getOrElse {
                        if (transaction != null) {
                            val company = localStore.readCompanySession()?.company
                            val caja = localStore.readActiveCajaForToday()
                            LocalInvoicePrintPayloadMapper.fromTransaction(transaction, company, caja)
                        } else {
                            return InvoicePrintFeedback(
                                displayMessage = it.message ?: "No se pudo obtener el payload de impresión",
                                fiscalNumber = "",
                                printerSerial = "",
                                isSuccess = false,
                            )
                        }
                    }

                val ticket =
                    when (countryCode.uppercase()) {
                        VENEZUELA_CODE -> VenezuelaInvoiceTicketFormatter().format(payload)
                        PANAMA_CODE -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
                        else -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
                    }
                when (val result = printer.printTicket(ticket)) {
                    PrintResult.Success ->
                        InvoicePrintFeedback("Ticket SUNMI enviado correctamente", payload.cufe.orEmpty(), "SUNMI", isSuccess = true)
                    is PrintResult.Error -> InvoicePrintFeedback(result.message, payload.cufe.orEmpty(), "SUNMI", isSuccess = false)
                }
            }
        }
    }

    private companion object {
        const val PANAMA_CODE = "PA"
        const val VENEZUELA_CODE = "VE"
    }
}
