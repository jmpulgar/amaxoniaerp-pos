package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult
import com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult

interface PrinterRepository {
    suspend fun printReceipt(transaction: Transaction): Result<ReceiptPrintResult>
    suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult>
    suspend fun printReportX(): Result<Unit>
    suspend fun printReportZ(): Result<Unit>
}
