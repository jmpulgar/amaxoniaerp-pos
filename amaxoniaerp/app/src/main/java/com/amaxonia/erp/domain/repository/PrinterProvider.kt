package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.printer.TicketPrinter

interface PrinterProvider {
    fun getActivePrinter(): PrinterRepository?

    fun getActiveTicketPrinter(): TicketPrinter?
}
