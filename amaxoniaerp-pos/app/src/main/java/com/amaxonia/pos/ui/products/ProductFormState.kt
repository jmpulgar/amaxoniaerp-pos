package com.amaxonia.pos.ui.products

import com.amaxonia.pos.domain.model.Product

data class ProductFormState(
    val product: Product = Product(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val error: String? = null
)
