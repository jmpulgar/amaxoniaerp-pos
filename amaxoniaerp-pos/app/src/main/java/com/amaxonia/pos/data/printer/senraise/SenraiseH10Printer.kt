package com.amaxonia.pos.data.printer.senraise

import android.content.Context
import android.os.RemoteException
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readTicketFontSize
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketFontSize
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import recieptservice.com.recieptservice.PrinterInterface

class SenraiseH10Printer(
    context: Context,
    private val manager: SenraisePrinterManager = SenraisePrinterManager(context),
    private val localStore: LocalStore? = null,
) : TicketPrinter {
    override suspend fun connect(): PrintResult =
        if (manager.bind()) PrintResult.Success else PrintResult.Error("No se pudo conectar con la impresora Senraise")

    override suspend fun disconnect() {
        manager.unbind()
    }

    override suspend fun isAvailable(): Boolean {
        if (manager.getInterface() != null) return true
        return manager.bind()
    }

    override suspend fun printText(text: String): PrintResult {
        val dimensions = SenraiseFontSize.dimensionsFor(resolveFontSize())
        return withService { service ->
            runCatching {
                service.printEpson(byteArrayOf(ESC_COMMAND, SET_LINE_SPACING_COMMAND, COMPACT_LINE_SPACING_DOTS))
            }
            service.setAlignment(0)
            service.setTextBold(false)
            service.setTextSize(dimensions.regular)
            service.printText(text + "\n")
            service.nextLine(2)
        }
    }

    override suspend fun printTicket(ticket: TicketDocument): PrintResult {
        val dimensions = SenraiseFontSize.dimensionsFor(resolveFontSize())
        return withService { service ->
            runCatching {
                service.printEpson(byteArrayOf(ESC_COMMAND, SET_LINE_SPACING_COMMAND, COMPACT_LINE_SPACING_DOTS))
            }
            service.setTextSize(dimensions.regular)
            ticket.elements.forEach { element ->
                when (element) {
                    is TicketElement.Text -> {
                        service.setAlignment(element.align.toSenraiseAlign())
                        service.setTextBold(element.bold)
                        val size = if (element.bold) dimensions.bold else dimensions.regular
                        service.setTextSize(size)
                        service.printText(element.value + "\n")
                        service.setTextBold(false)
                        service.setTextSize(dimensions.regular)
                    }

                    is TicketElement.Columns -> {
                        service.printTableText(
                            element.values.toTypedArray(),
                            element.widths.toIntArray(),
                            element.aligns.map { it.toSenraiseAlign() }.toIntArray(),
                        )
                        service.nextLine(1)
                    }

                    is TicketElement.TotalsRow -> {
                        service.setAlignment(TicketAlign.LEFT.toSenraiseAlign())
                        if (element.bold) {
                            service.setTextBold(true)
                            service.setTextSize(dimensions.totalsBold)
                        } else {
                            service.setTextBold(false)
                            service.setTextSize(dimensions.totalsRegular)
                        }
                        service.printText(element.formatMonospacedLine() + "\n")
                        service.setTextBold(false)
                        service.setTextSize(dimensions.regular)
                    }

                    is TicketElement.Qr -> {
                        service.setAlignment(TicketAlign.CENTER.toSenraiseAlign())
                        service.printQRCode(element.value, element.size, QR_ERROR_CORRECTION_LEVEL)
                        service.nextLine(1)
                    }

                    is TicketElement.Feed -> {
                        val lines = element.lines.coerceAtLeast(0)
                        if (lines > 0) {
                            service.nextLine(lines)
                        }
                    }

                    TicketElement.Divider -> {
                        service.setAlignment(TicketAlign.LEFT.toSenraiseAlign())
                        service.setTextBold(false)
                        service.setTextSize(dimensions.regular)
                        service.printText("--------------------------------\n")
                    }
                }
            }
            service.nextLine(TICKET_TRAILING_FEED_LINES)
        }
    }

    private suspend fun resolveFontSize(): TicketFontSize =
        localStore?.readTicketFontSize() ?: TicketFontSize.NORMAL

    private suspend fun withService(block: (PrinterInterface) -> Unit): PrintResult {
        if (!manager.bind()) return PrintResult.Error("Servicio de impresión Senraise no disponible")
        val service = manager.getInterface()
        return when {
            service == null -> PrintResult.Error("Impresora Senraise no conectada")
            else ->
                runCatching { block(service) }
                    .fold(
                        onSuccess = { PrintResult.Success },
                        onFailure = { e ->
                            when (e) {
                                is RemoteException -> PrintResult.Error("Error comunicando con la impresora Senraise", e)
                                is Exception -> PrintResult.Error(e.message ?: "No se pudo imprimir en Senraise", e)
                                else -> throw e
                            }
                        },
                    )
        }
    }

    private fun TicketAlign.toSenraiseAlign(): Int =
        when (this) {
            TicketAlign.LEFT -> 0
            TicketAlign.CENTER -> 1
            TicketAlign.RIGHT -> 2
        }

    private companion object {
        const val QR_ERROR_CORRECTION_LEVEL = 2
        const val ESC_COMMAND: Byte = 0x1B
        const val SET_LINE_SPACING_COMMAND: Byte = 0x33
        const val COMPACT_LINE_SPACING_DOTS: Byte = 16
        const val TICKET_TRAILING_FEED_LINES = 2
    }
}

private data class SenraiseFontDimensions(
    val regular: Float,
    val bold: Float,
    val totalsRegular: Float,
    val totalsBold: Float,
)

private object SenraiseFontSize {
    private val NORMAL = SenraiseFontDimensions(regular = 16f, bold = 18f, totalsRegular = 16f, totalsBold = 18f)
    private val COMPACT = SenraiseFontDimensions(regular = 14f, bold = 16f, totalsRegular = 14f, totalsBold = 16f)

    fun dimensionsFor(size: TicketFontSize): SenraiseFontDimensions =
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
