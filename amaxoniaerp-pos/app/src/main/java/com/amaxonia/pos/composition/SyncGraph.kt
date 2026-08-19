package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.sync.SyncViewModel

/** Grafo del feature sincronización de catálogos (TASK-051/052). */
object SyncGraph {
    fun syncViewModel(): SyncViewModel = SyncViewModel(DependencyContainer.catalogSyncer)
}
