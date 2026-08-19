package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.usecase.creditnote.ProcessCreditNoteFiscalUseCase
import com.amaxonia.pos.ui.creditnotes.CreditNotesViewModel

/**
 * Grafo del feature notas de crédito (TASK-051/052): el use case fiscal se
 * construye en composition, no dentro del Composable.
 */
object CreditNotesGraph {
    fun creditNotesViewModel(): CreditNotesViewModel =
        CreditNotesViewModel(
            creditNoteRepository = DependencyContainer.creditNoteRepository,
            cajaRepository = DependencyContainer.cajaRepository,
            formaPagoRepository = DependencyContainer.formaPagoRepository,
            processCreditNoteFiscal =
                ProcessCreditNoteFiscalUseCase(
                    DependencyContainer.creditNoteRepository,
                    DependencyContainer.printerFactory,
                    DependencyContainer.posConfigurationRepository,
                ),
        )
}
