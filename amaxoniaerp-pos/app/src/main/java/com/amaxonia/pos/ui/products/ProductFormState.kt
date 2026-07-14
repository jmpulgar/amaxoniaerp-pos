package com.amaxonia.pos.ui.products

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.Department

data class ProductFormState(
    val product: Product = Product(),
    val departments: List<Department> = emptyList(),
    val sections: List<Department> = emptyList(),
    val families: List<Department> = emptyList(),
    val subFamilies: List<Department> = emptyList(),
    val brands: List<Department> = emptyList(),
    val lines: List<Department> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val error: String? = null,
)
