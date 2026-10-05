package com.amaxonia.erp.data.printer.sunmi

import android.content.Context
import android.os.RemoteException
import com.amaxonia.erp.domain.model.printer.PrintResult
import com.amaxonia.erp.domain.model.printer.TicketAlign
import com.amaxonia.erp.domain.model.printer.TicketDocument
import com.amaxonia.erp.domain.model.printer.TicketElement
import com.amaxonia.erp.domain.model.printer.TicketPrinter

class SunmiV2Printer(
    context: Context,
    private val manager: SunmiPrinterManager = SunmiPrinterManager(context),
) : TicketPrinter {
    override suspend fun connect(): PrintResult =
        if (manager.bind()) PrintResult.Success else PrintResult.Error("No se pudo conectar con la impresora SUNMI")

    override suspend fun disconnect() {
        manager.unbind()
    }

    override suspend fun isAvailable(): Boolean {
        if (manager.getService() != null) return true
        return manager.bind()
    }

    override suspend fun printText(text: String): PrintResult =
        withService { service ->
            service.printerInit(null)
            service.printText(text + "\n", null)
            service.lineWrap(2, null)
        }

    override suspend fun printTicket(ticket: TicketDocument): PrintResult =
        withService { service ->
            service.printerInit(null)
            ticket.elements.forEach { element ->
                when (element) {
                    is TicketElement.Text -> {
                        service.setAlignment(element.align.toSunmiAlign(), null)
                        val size = if (element.bold) SunmiFontSize.BOLD else SunmiFontSize.REGULAR
                        service.setFontSize(size, null)
                        service.printText(element.value + "\n", null)
                        service.setFontSize(SunmiFontSize.REGULAR, null)
                    }

                    is TicketElement.Columns -> {
                        service.setAlignment(TicketAlign.LEFT.toSunmiAlign(), null)
                        service.printColumnsString(
                            element.values.toTypedArray(),
                            element.widths.toIntArray(),
                            element.aligns.map { it.toSunmiAlign() }.toIntArray(),
                            null,
                        )
                    }

                    is TicketElement.TotalsRow -> {
                        service.setAlignment(TicketAlign.LEFT.toSunmiAlign(), null)
                        if (element.bold) {
                            service.setFontSize(SunmiFontSize.TOTALS_BOLD, null)
                        } else {
                            service.setFontSize(SunmiFontSize.TOTALS_REGULAR, null)
                        }
                        service.printText(element.formatMonospacedLine() + "\n", null)
                        service.setFontSize(SunmiFontSize.TOTALS_REGULAR, null)
                    }

                    is TicketElement.Qr -> {
                        service.setAlignment(TicketAlign.CENTER.toSunmiAlign(), null)
                        service.printQRCode(element.value, element.size, 2, null)
                    }

                    is TicketElement.Feed -> service.lineWrap(element.lines.coerceAtLeast(0), null)
                    TicketElement.Divider -> {
                        service.setAlignment(TicketAlign.LEFT.toSunmiAlign(), null)
                        service.printText("--------------------------------\n", null)
                    }
                }
            }
            service.lineWrap(TICKET_TRAILING_FEED_LINES, null)
        }

    private suspend fun withService(block: (com.sunmi.peripheral.printer.SunmiPrinterService) -> Unit): PrintResult {
        if (!manager.bind()) return PrintResult.Error("Servicio de impresión SUNMI no disponible")
        val service = manager.getService()
        return when {
            service == null -> PrintResult.Error("Impresora SUNMI no conectada")
            else ->
                runCatching { block(service) }
                    .fold(
                        onSuccess = { PrintResult.Success },
                        onFailure = { e ->
                            when (e) {
                                is RemoteException -> PrintResult.Error("Error comunicando con la impresora SUNMI", e)
                                is Exception -> PrintResult.Error(e.message ?: "No se pudo imprimir en SUNMI", e)
                                else -> throw e
                            }
                        },
                    )
        }
    }

    private fun TicketAlign.toSunmiAlign(): Int =
        when (this) {
            TicketAlign.LEFT -> 0
            TicketAlign.CENTER -> 1
            TicketAlign.RIGHT -> 2
        }
}

private object SunmiFontSize {
    const val REGULAR = 24f
    const val BOLD = 28f
    const val TOTALS_REGULAR = 24f
    const val TOTALS_BOLD = 26f
}

private const val TICKET_TRAILING_FEED_LINES = 4

private fun TicketElement.TotalsRow.formatMonospacedLine(): String {
    val width = printerWidth.coerceAtLeast(1)
    val safeLabel = if (label.length <= labelWidth) label else label.take(labelWidth)
    val paddedLabel = safeLabel.padEnd(labelWidth)
    val remaining = (width - paddedLabel.length).coerceAtLeast(0)
    val safeValue = value.take(remaining)
    val paddedValue = if (safeValue.length < remaining) safeValue.padStart(remaining) else safeValue
    return (paddedLabel + paddedValue).take(width)
}
