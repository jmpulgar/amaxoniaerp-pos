package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.reports.ReportsViewModel

/** Grafo del feature reportes (TASK-051/052). */
object ReportsGraph {
    fun reportsViewModel(): ReportsViewModel = ReportsViewModel(DependencyContainer.reportRepository)
}
