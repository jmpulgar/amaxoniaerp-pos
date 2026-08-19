package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.ui.settings.SettingsViewModel

/**
 * Grafo del feature ajustes (TASK-051/052): además del ViewModel expone los
 * accesos a impresoras que los callbacks de prueba de impresión necesitan.
 */
object SettingsGraph {
    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(
            settingsRepository = DependencyContainer.posConfigurationRepository,
            fiscalDiagnostics = DependencyContainer.fiscalDeviceDiagnostics,
        )

    fun activeTicketPrinter(): TicketPrinter? = DependencyContainer.printerFactory.getActiveTicketPrinter()

    fun activePrinter(): PrinterRepository? = DependencyContainer.printerFactory.getActivePrinter()
}
