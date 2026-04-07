package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult

interface PrinterRepository {
    suspend fun printReceipt(transaction: Transaction): Result<Boolean>
    suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult>
}
