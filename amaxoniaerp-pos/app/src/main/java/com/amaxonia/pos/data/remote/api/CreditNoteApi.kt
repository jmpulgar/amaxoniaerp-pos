package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto

interface CreditNoteApi {
    suspend fun getCreditNotes(authHeader: String, companyDb: String, search: String? = null): Result<CreditNotesListResponseDto>
    suspend fun getCreditNoteDetail(authHeader: String, companyDb: String, id: String): Result<CreditNoteDetailDto>
    suspend fun getSourceInvoices(authHeader: String, companyDb: String, search: String? = null): Result<CreditNoteSourceInvoiceListResponseDto>
    suspend fun getSourceInvoiceDetail(authHeader: String, companyDb: String, id: String): Result<CreditNoteSourceInvoiceDetailDto>
    suspend fun createCreditNote(authHeader: String, companyDb: String, payload: CreateCreditNoteRequestDto): Result<CreateCreditNoteResponseDto>
    suspend fun confirmFiscal(authHeader: String, companyDb: String, id: String, payload: ConfirmCreditNoteFiscalRequestDto): Result<ConfirmCreditNoteFiscalResponseDto>
}
