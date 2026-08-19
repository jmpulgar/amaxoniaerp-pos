package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.drafts.DraftInvoicesViewModel

/** Grafo del feature borradores de factura (TASK-051/052). */
object DraftsGraph {
    fun draftInvoicesViewModel(): DraftInvoicesViewModel =
        DraftInvoicesViewModel(
            DependencyContainer.draftInvoiceRepository,
            DependencyContainer.restoreDraftInvoiceUseCase,
        )
}
