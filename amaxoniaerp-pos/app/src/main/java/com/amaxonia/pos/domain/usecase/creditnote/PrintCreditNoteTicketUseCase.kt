package com.amaxonia.pos.domain.usecase.creditnote

import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto

fun interface PrintCreditNoteTicketUseCase {
    suspend operator fun invoke(detail: CreditNoteDetailDto): Result<Unit>
}
