package com.amaxonia.erp.domain.model.printer

interface TicketPrinter {
    suspend fun connect(): PrintResult

    suspend fun disconnect()

    suspend fun isAvailable(): Boolean

    suspend fun printText(text: String): PrintResult

    suspend fun printTicket(ticket: TicketDocument): PrintResult
}

sealed class PrintResult {
    data object Success : PrintResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : PrintResult()
}
