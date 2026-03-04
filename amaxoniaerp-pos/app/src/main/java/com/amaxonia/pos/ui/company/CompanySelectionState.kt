package com.amaxonia.pos.ui.company

import com.amaxonia.pos.domain.model.Company

data class CompanySelectionState(
    val isLoading: Boolean = false,
    val companies: List<Company> = emptyList(),
    val error: String? = null
)
