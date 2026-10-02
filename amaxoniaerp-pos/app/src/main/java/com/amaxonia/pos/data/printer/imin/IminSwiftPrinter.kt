package com.amaxonia.pos.data.printer.imin

import android.content.Context
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.imin.printer.PrinterHelper

class IminSwiftPrinter(
    context: Context,
    private val manager: IminPrinterManager = IminPrinterManager(context),
) : TicketPrinter {
    override suspend fun connect(): PrintResult =
        if (manager.bind()) PrintResult.Success else PrintResult.Error("No se pudo conectar con la impresora iMin Swift")

    override suspend fun disconnect() {
        manager.unbind()
    }

    override suspend fun isAvailable(): Boolean {
        if (manager.isBound()) return true
        return manager.bind()
    }

    override suspend fun printText(text: String): PrintResult =
        withPrinter { helper ->
            helper.initPrinterParams()
            helper.printText(text + "\n", null)
            helper.printAndFeedPaper(FEED_LINES_SHORT)
        }

    override suspend fun printTicket(ticket: TicketDocument): PrintResult =
        withPrinter { helper ->
            helper.initPrinterParams()
            ticket.elements.forEach { element ->
                when (element) {
                    is TicketElement.Text -> {
                        helper.setTextBitmapSize(if (element.bold) FONT_SIZE_BOLD else FONT_SIZE_REGULAR)
                        helper.setTextBitmapStyle(if (element.bold) 1 else 0)
                        helper.printTextWithAli(element.value + "\n", element.align.toIminAlign(), null)
                    }

                    is TicketElement.Columns -> {
                        val sizes = IntArray(element.values.size) { FONT_SIZE_REGULAR }
                        helper.printColumnsString(
                            element.values.toTypedArray(),
                            element.widths.toIntArray(),
                            element.aligns.map { it.toIminAlign() }.toIntArray(),
                            sizes,
                            null,
                        )
                    }

                    is TicketElement.TotalsRow -> {
                        helper.setTextBitmapSize(if (element.bold) FONT_SIZE_TOTALS_BOLD else FONT_SIZE_TOTALS_REGULAR)
                        helper.setTextBitmapStyle(if (element.bold) 1 else 0)
                        helper.printTextWithAli(element.formatMonospacedLine() + "\n", 0, null)
                    }

                    is TicketElement.Qr -> {
                        helper.setQrCodeSize(element.size)
                        helper.printQrCodeWithAlign(element.value, 1, null)
                    }

                    is TicketElement.Feed -> {
                        val lines = element.lines.coerceAtLeast(0)
                        if (lines > 0) {
                            helper.printAndFeedPaper(lines * FEED_UNIT_HEIGHT)
                        }
                    }

                    TicketElement.Divider -> {
                        helper.printTextWithAli("--------------------------------\n", 0, null)
                    }
                }
            }
            helper.printAndFeedPaper(FEED_LINES_TRAILING)
            runCatching { helper.partialCutAndFeedPaper(10) }
        }

    private suspend fun withPrinter(block: (PrinterHelper) -> Unit): PrintResult {
        if (!manager.bind()) return PrintResult.Error("Servicio de impresión iMin no disponible")
        val helper = PrinterHelper.getInstance()
        return runCatching { block(helper) }.fold(
            onSuccess = { PrintResult.Success },
            onFailure = { e ->
                PrintResult.Error(e.message ?: "Error comunicando con la impresora iMin", e)
            },
        )
    }

    private fun TicketAlign.toIminAlign(): Int =
        when (this) {
            TicketAlign.LEFT -> 0
            TicketAlign.CENTER -> 1
            TicketAlign.RIGHT -> 2
        }

    private companion object {
        const val FONT_SIZE_REGULAR = 24
        const val FONT_SIZE_BOLD = 28
        const val FONT_SIZE_TOTALS_REGULAR = 24
        const val FONT_SIZE_TOTALS_BOLD = 26
        const val FEED_UNIT_HEIGHT = 20
        const val FEED_LINES_SHORT = 40
        const val FEED_LINES_TRAILING = 80
    }
}

/** Formatea TotalsRow como una sola línea monoespaciada para papel de 58 mm. */
private fun TicketElement.TotalsRow.formatMonospacedLine(): String {
    val width = printerWidth.coerceAtLeast(1)
    val safeLabel = if (label.length <= labelWidth) label else label.take(labelWidth)
    val paddedLabel = safeLabel.padEnd(labelWidth)
    val remaining = (width - paddedLabel.length).coerceAtLeast(0)
    val safeValue = value.take(remaining)
    val paddedValue = if (safeValue.length < remaining) safeValue.padStart(remaining) else safeValue
    return (paddedLabel + paddedValue).take(width)
}
