package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.history.HistoryViewModel

/** Grafo del feature historial de ventas (TASK-051/052). */
object HistoryGraph {
    fun historyViewModel(): HistoryViewModel =
        HistoryViewModel(
            transactionRepository = DependencyContainer.invoiceHistoryRepository,
            cajaRepository = DependencyContainer.cajaRepository,
            printInvoiceUseCase = DependencyContainer.printInvoiceUseCase,
            sessionReader = DependencyContainer.posConfigurationRepository,
        )
}
