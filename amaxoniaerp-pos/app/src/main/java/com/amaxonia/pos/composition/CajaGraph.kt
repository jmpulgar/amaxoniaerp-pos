package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.caja.CierreCajaViewModel

/** Grafo del feature caja/cierre (TASK-051/052). */
object CajaGraph {
    fun cierreCajaViewModel(): CierreCajaViewModel =
        CierreCajaViewModel(
            DependencyContainer.cajaRepository,
            DependencyContainer.cashClosePrintingService,
            DependencyContainer.cashCloseTicketPayloadBuilder,
        )
}
