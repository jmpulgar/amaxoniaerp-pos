package com.amaxonia.pos.composition

import com.amaxonia.pos.data.remote.NetworkMonitor

/**
 * Composition boundary de la app (TASK-050/051): único punto que las pantallas
 * pueden consumir para obtener dependencias. Cada feature expone un grafo
 * ligero (plain Kotlin) que delega en el grafo real construido por
 * [DependencyContainer]. Los graphs no agregan comportamiento: sólo
 * construcción y acceso.
 */
object AppGraph {
    val login get() = LoginGraph
    val company get() = CompanyGraph
    val dashboard get() = DashboardGraph
    val cart get() = CartGraph
    val caja get() = CajaGraph
    val mesas get() = MesasGraph
    val payment get() = PaymentGraph
    val clients get() = ClientsGraph
    val products get() = ProductsGraph
    val creditNotes get() = CreditNotesGraph
    val drafts get() = DraftsGraph
    val history get() = HistoryGraph
    val reports get() = ReportsGraph
    val settings get() = SettingsGraph
    val sync get() = SyncGraph

    /** Conectividad global: la consume la navegación para el banner offline. */
    val networkMonitor: NetworkMonitor get() = DependencyContainer.networkMonitor
}
