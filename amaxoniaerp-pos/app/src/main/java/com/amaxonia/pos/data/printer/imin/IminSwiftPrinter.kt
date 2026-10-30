package com.amaxonia.pos.data.printer.imin

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readTicketFontSize
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketFontSize
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.imin.printer.PrinterHelper

class IminSwiftPrinter(
    context: Context,
    private val manager: IminPrinterManager = IminPrinterManager(context),
    private val localStore: LocalStore? = null,
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

    override suspend fun printText(text: String): PrintResult {
        val dimensions = IminFontSize.dimensionsFor(resolveFontSize())
        return withPrinter { helper ->
            helper.initPrinterParams()
            helper.setTextBitmapSize(dimensions.regular)
            helper.printText(text + "\n", null)
            helper.printAndFeedPaper(FEED_LINES_SHORT)
        }
    }

    override suspend fun printTicket(ticket: TicketDocument): PrintResult {
        val dimensions = IminFontSize.dimensionsFor(resolveFontSize())
        return withPrinter { helper ->
            helper.initPrinterParams()
            ticket.elements.forEach { element ->
                when (element) {
                    is TicketElement.Text -> {
                        helper.setTextBitmapSize(if (element.bold) dimensions.bold else dimensions.regular)
                        helper.setTextBitmapStyle(if (element.bold) 1 else 0)
                        helper.printTextWithAli(element.value + "\n", element.align.toIminAlign(), null)
                    }

                    is TicketElement.Columns -> {
                        val sizes = IntArray(element.values.size) { dimensions.regular }
                        helper.printColumnsString(
                            element.values.toTypedArray(),
                            element.widths.toIntArray(),
                            element.aligns.map { it.toIminAlign() }.toIntArray(),
                            sizes,
                            null,
                        )
                    }

                    is TicketElement.TotalsRow -> {
                        helper.setTextBitmapSize(if (element.bold) dimensions.totalsBold else dimensions.totalsRegular)
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
    }

    private suspend fun resolveFontSize(): TicketFontSize =
        localStore?.readTicketFontSize() ?: TicketFontSize.NORMAL

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
        const val FEED_UNIT_HEIGHT = 20
        const val FEED_LINES_SHORT = 40
        const val FEED_LINES_TRAILING = 40
    }
}

private data class IminFontDimensions(
    val regular: Int,
    val bold: Int,
    val totalsRegular: Int,
    val totalsBold: Int,
)

private object IminFontSize {
    private val NORMAL = IminFontDimensions(regular = 16, bold = 18, totalsRegular = 16, totalsBold = 18)
    private val COMPACT = IminFontDimensions(regular = 14, bold = 16, totalsRegular = 14, totalsBold = 16)

    fun dimensionsFor(size: TicketFontSize): IminFontDimensions =
        when (size) {
            TicketFontSize.NORMAL -> NORMAL
            TicketFontSize.COMPACT -> COMPACT
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
