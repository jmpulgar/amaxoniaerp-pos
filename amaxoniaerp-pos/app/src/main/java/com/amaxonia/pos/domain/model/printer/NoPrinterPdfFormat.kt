package com.amaxonia.pos.domain.model.printer

enum class NoPrinterPdfFormat {
    FACTURA_CARTA,
    TICKET_TERMICO;

    val displayName: String
        get() = when (this) {
            FACTURA_CARTA -> "Factura Digital (Carta)"
            TICKET_TERMICO -> "Ticket Térmico (PDF)"
        }
}
