package com.amaxonia.pos.domain.usecase.caja

import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.PrinterProvider

enum class FiscalReportType {
    X,
    Z,
}

sealed interface CashClosePrintOutcome {
    data object NoPrinter : CashClosePrintOutcome

    data class Message(
        val value: String,
    ) : CashClosePrintOutcome
}

class CashClosePrintingService(
    private val printerProvider: PrinterProvider,
    private val contextReader: CashCloseContextReader,
    private val ticketFormatter: CashCloseTicketFormatter,
) {
    fun hasActiveFiscalPrinter(): Boolean = printerProvider.getActivePrinter() != null

    suspend fun printReport(type: FiscalReportType): CashClosePrintOutcome {
        val printer = printerProvider.getActivePrinter() ?: return CashClosePrintOutcome.NoPrinter
        val result =
            when (type) {
                FiscalReportType.X -> printer.printReportX()
                FiscalReportType.Z -> printer.printReportZ()
            }
        val label = type.name
        return result.fold(
            onSuccess = { CashClosePrintOutcome.Message("Reporte $label impreso correctamente") },
            onFailure = { error -> CashClosePrintOutcome.Message("Error Reporte $label: ${error.message}") },
        )
    }

    suspend fun shouldOfferCloseTicket(): Boolean =
        contextReader.currentCountryCode().equals(PANAMA_CODE, ignoreCase = true) &&
            contextReader.selectedPrinterType() == PrinterType.SUNMI_V2 &&
            printerProvider.getActiveTicketPrinter() != null

    suspend fun printCloseTicket(payload: CashCloseTicketPayload): CashClosePrintOutcome {
        val printer =
            printerProvider.getActiveTicketPrinter()
                ?: return CashClosePrintOutcome.Message("Cierre realizado, pero no hay impresora SUNMI disponible")
        return when (val result = printer.printTicket(ticketFormatter.format(payload))) {
            PrintResult.Success -> CashClosePrintOutcome.Message("Ticket de cierre impreso correctamente")
            is PrintResult.Error ->
                CashClosePrintOutcome.Message("Cierre realizado, pero no se pudo imprimir: ${result.message}")
        }
    }

    private companion object {
        const val PANAMA_CODE = "PA"
    }
}
