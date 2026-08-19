package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.company.CompanySelectionViewModel

/** Grafo del feature selección de empresa (TASK-051/052). */
object CompanyGraph {
    fun companySelectionViewModel(): CompanySelectionViewModel =
        CompanySelectionViewModel(
            DependencyContainer.companyRepository,
            DependencyContainer.authRepository,
        )
}
