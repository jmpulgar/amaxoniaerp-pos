package com.amaxonia.pos.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import com.amaxonia.pos.domain.repository.ProductSessionReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductListViewModel(
    private val productRepository: ProductCatalogReader,
    private val sessionReader: ProductSessionReader,
    private val imageUrlResolver: ImageUrlResolver,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductListState())
    val state: StateFlow<ProductListState> = _state.asStateFlow()
    private val pageSize = 20

    @Volatile
    private var adminDb: String = ""

    init {
        viewModelScope.launch {
            adminDb = sessionReader.currentAdminDatabase()
        }
        loadProducts(reset = true)
    }

    fun getProductImageUrl(photoPath: String): String {
        if (photoPath.isBlank() || adminDb.isBlank()) return ""
        return imageUrlResolver.product(adminDb, photoPath)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
        loadProducts(reset = true)
    }

    fun loadMoreProducts() {
        if (_state.value.isLoading || _state.value.endOfListReached) return
        loadProducts(reset = false)
    }

    fun retry() {
        loadProducts(reset = true)
    }

    fun ensureStockLoaded(productId: String) {
        val current = _state.value
        if (productId.isBlank()) return
        if (current.stockByProductId.containsKey(productId)) return
        if (current.loadingStockIds.contains(productId)) return

        viewModelScope.launch {
            _state.update { it.copy(loadingStockIds = it.loadingStockIds + productId) }
            productRepository.getProductStock(productId).fold(
                onSuccess = { stock ->
                    _state.update {
                        it.copy(
                            stockByProductId = it.stockByProductId + (productId to stock),
                            loadingStockIds = it.loadingStockIds - productId,
                        )
                    }
                },
                onFailure = {
                    _state.update { current -> current.copy(loadingStockIds = current.loadingStockIds - productId) }
                },
            )
        }
    }

    fun getStock(productId: String): ProductStock? = _state.value.stockByProductId[productId]

    private fun loadProducts(reset: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    page = if (reset) 1 else it.page + 1,
                    products = if (reset) emptyList() else it.products,
                    endOfListReached = if (reset) false else it.endOfListReached,
                    error = null,
                )
            }
            val query = _state.value.searchQuery.trim()
            val result =
                if (query.isEmpty()) {
                    productRepository.getAllProducts(_state.value.page, pageSize)
                } else {
                    productRepository.searchProducts(query, _state.value.page, pageSize)
                }
            result.fold(
                onSuccess = { products ->
                    val newProducts = if (reset) products else _state.value.products + products
                    _state.update {
                        it.copy(
                            isLoading = false,
                            products = newProducts,
                            endOfListReached = products.size < pageSize,
                            error = null,
                        )
                    }
                    newProducts.forEach { product ->
                        ensureStockLoaded(product.id)
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Error desconocido",
                        )
                    }
                },
            )
        }
    }
}
