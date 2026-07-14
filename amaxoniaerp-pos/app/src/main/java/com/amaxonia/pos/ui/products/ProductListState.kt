package com.amaxonia.pos.ui.products

import com.amaxonia.pos.domain.model.ProductStock

data class ProductListState(
    val products: List<com.amaxonia.pos.domain.model.Product> = emptyList(),
    val stockByProductId: Map<String, ProductStock> = emptyMap(),
    val loadingStockIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val page: Int = 1,
    val endOfListReached: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
)
