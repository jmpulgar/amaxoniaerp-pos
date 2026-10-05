package com.amaxonia.erp.domain.model.printer

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

    data object Divider : TicketElement()
}

enum class TicketAlign {
    LEFT,
    CENTER,
    RIGHT,
}
