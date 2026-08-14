package com.amaxonia.pos.domain.model.printer

data class TicketDocument(
    val elements: List<TicketElement>,
)

sealed class TicketElement {
    data class Text(
        val value: String,
        val align: TicketAlign = TicketAlign.LEFT,
        val bold: Boolean = false,
    ) : TicketElement()

    data class Columns(
        val values: List<String>,
        val widths: List<Int>,
        val aligns: List<TicketAlign>,
    ) : TicketElement()

    /**
     * Two-column row (label + value) rendered by the printer as a **single physical line**
     * instead of via `printColumnsString`. The label is left-padded to [labelWidth] so the value
     * always lands in the same column; the printer never wraps a single trailing character to the
     * next line.
     *
     * Use this for totals blocks (Subtotal / Descuento / Impuesto / Total) where label widths
     * frequently exceed the SUNMI column width (e.g. "Subtotal Items:" + value).
     *
     * [printerWidth] is the physical character width of the target printer (32 for SUNMI v2 58mm
     * default font in Panamá, 40 for Venezuela). The formatter clamps the line to that width
     * and ellipsizes the label rather than spilling.
     */
    data class TotalsRow(
        val label: String,
        val value: String,
        val labelWidth: Int,
        val printerWidth: Int,
        val bold: Boolean = false,
    ) : TicketElement()

    data class Qr(
        val value: String,
        val size: Int = 4,
    ) : TicketElement()

    data class Feed(
        val lines: Int,
    ) : TicketElement()

    object Divider : TicketElement()
}

enum class TicketAlign {
    LEFT,
    CENTER,
    RIGHT,
}
