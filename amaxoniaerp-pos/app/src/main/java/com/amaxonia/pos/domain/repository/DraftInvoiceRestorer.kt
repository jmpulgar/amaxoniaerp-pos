package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.DraftInvoice

/** Boundary for decoding a persisted draft into the active cart. */
fun interface DraftInvoiceRestorer {
    fun restore(draft: DraftInvoice): Result<Unit>
}
