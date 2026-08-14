package com.amaxonia.pos.data.printer.sunmi

import android.content.Context
import android.os.RemoteException
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketPrinter

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
                        // Render the totals row as a single physical line so trailing characters
                        // of long labels ("Subtotal Items:", "Total Impuesto:") never wrap to the
                        // next physical line on the SUNMI thermal printer.
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
            service.lineWrap(4, null)
        }

    private suspend fun withService(block: (com.sunmi.peripheral.printer.SunmiPrinterService) -> Unit): PrintResult {
        val connected = manager.bind()
        if (!connected) return PrintResult.Error("Servicio de impresión SUNMI no disponible")
        val service = manager.getService() ?: return PrintResult.Error("Impresora SUNMI no conectada")
        return try {
            block(service)
            PrintResult.Success
        } catch (e: RemoteException) {
            PrintResult.Error("Error comunicando con la impresora SUNMI", e)
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "No se pudo imprimir en SUNMI", e)
        }
    }

    private fun TicketAlign.toSunmiAlign(): Int =
        when (this) {
            TicketAlign.LEFT -> 0
            TicketAlign.CENTER -> 1
            TicketAlign.RIGHT -> 2
        }
}

/**
 * SUNMI v2 font sizes (points) used by [SunmiV2Printer]. Centralized so detekt magic-number
 * rule does not flag the print pipeline.
 */
private object SunmiFontSize {
    const val REGULAR = 24f
    const val BOLD = 28f
    const val TOTALS_REGULAR = 24f
    const val TOTALS_BOLD = 26f
}

/**
 * Builds a single physical line for [TicketElement.TotalsRow], guaranteeing that the rendered
 * output never wraps a single trailing character.
 *
 * The label is padded to [TotalsRow.labelWidth] and the value is right-aligned within the
 * remaining space. The four canonical labels ("Subtotal Items:", "Descuento:", "Total Impuesto:"
 * / "Total IVA:" and "Total:") are designed to fit within the formatter-provided [TotalsRow.labelWidth],
 * so no ellipsis is ever emitted. The Unicode "…" glyph is intentionally avoided because some
 * SUNMI firmware revisions render it as "?".
 *
 * Defensive truncation still keeps the layout intact if a future label overflows [TotalsRow.labelWidth]:
 * the label is hard-truncated to [TotalsRow.labelWidth] (no ellipsis) and the value gets the
 * remaining physical width.
 */
private fun TicketElement.TotalsRow.formatMonospacedLine(): String {
    val width = printerWidth.coerceAtLeast(1)
    val safeLabel = if (label.length <= labelWidth) label else label.take(labelWidth)
    val paddedLabel = safeLabel.padEnd(labelWidth)
    val remaining = (width - paddedLabel.length).coerceAtLeast(0)
    val safeValue = value.take(remaining)
    val paddedValue = if (safeValue.length < remaining) safeValue.padStart(remaining) else safeValue
    return (paddedLabel + paddedValue).take(width)
}
