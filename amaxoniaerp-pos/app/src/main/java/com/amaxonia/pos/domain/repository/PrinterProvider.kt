package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.printer.TicketPrinter

interface PrinterProvider {
    fun getActivePrinter(): PrinterRepository?

    fun getActiveTicketPrinter(): TicketPrinter?
}
