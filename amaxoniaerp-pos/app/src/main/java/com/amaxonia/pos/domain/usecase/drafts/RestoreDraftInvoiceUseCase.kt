package com.amaxonia.pos.domain.usecase.drafts

import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.repository.DraftInvoiceRestorer

class RestoreDraftInvoiceUseCase(
    private val restorer: DraftInvoiceRestorer,
) {
    operator fun invoke(draft: DraftInvoice): Result<Unit> = restorer.restore(draft)
}
