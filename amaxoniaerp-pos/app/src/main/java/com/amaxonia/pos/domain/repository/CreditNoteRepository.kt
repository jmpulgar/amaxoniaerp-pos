package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto

fun interface CreditNoteFiscalConfirmationRepository {
    suspend fun confirmFiscal(
        id: String,
        payload: ConfirmCreditNoteFiscalRequestDto,
    ): Result<ConfirmCreditNoteFiscalResponseDto>
}

interface CreditNoteRepository : CreditNoteFiscalConfirmationRepository {
    suspend fun getCreditNotes(search: String? = null): Result<CreditNotesListResponseDto>

    suspend fun getCreditNoteDetail(id: String): Result<CreditNoteDetailDto>

    suspend fun getSourceInvoices(search: String? = null): Result<CreditNoteSourceInvoiceListResponseDto>

    suspend fun getSourceInvoiceDetail(id: String): Result<CreditNoteSourceInvoiceDetailDto>

    suspend fun createCreditNote(payload: CreateCreditNoteRequestDto): Result<CreateCreditNoteResponseDto>

    override suspend fun confirmFiscal(
        id: String,
        payload: ConfirmCreditNoteFiscalRequestDto,
    ): Result<ConfirmCreditNoteFiscalResponseDto>
}
