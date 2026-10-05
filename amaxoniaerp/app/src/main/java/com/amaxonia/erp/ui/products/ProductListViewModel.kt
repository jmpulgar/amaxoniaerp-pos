package com.amaxonia.erp.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductListState(
    val products: List<Product> = emptyList(),
    val departments: List<DepartmentDto> = emptyList(),
    val selectedDepartmentId: Int? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 1,
    val endOfListReached: Boolean = false,
    val error: String? = null,
    val isFormOpen: Boolean = false,
    val editingProduct: Product? = null,
)

class ProductListViewModel(
    private val productRepository: ProductRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductListState())
    val state: StateFlow<ProductListState> = _state.asStateFlow()
    private val pageSize = 20
    private var searchJob: Job? = null

    init {
        loadDepartments()
        loadProducts(reset = true)
    }

    private fun loadDepartments() {
        viewModelScope.launch {
            productRepository.getDepartments().onSuccess { deps ->
                _state.update { it.copy(departments = deps) }
            }
        }
    }

    fun onDepartmentSelect(departmentId: Int?) {
        if (_state.value.selectedDepartmentId == departmentId) return
        _state.update { it.copy(selectedDepartmentId = departmentId) }
        loadProducts(reset = true)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            loadProducts(reset = true)
        }
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.endOfListReached) return
        loadProducts(reset = false)
    }

    fun retry() {
        loadProducts(reset = true)
    }

    fun openCreateForm() {
        _state.update { it.copy(isFormOpen = true, editingProduct = null) }
    }

    fun openEditForm(product: Product) {
        _state.update { it.copy(isFormOpen = true, editingProduct = product) }
    }

    fun closeForm() {
        _state.update { it.copy(isFormOpen = false, editingProduct = null) }
    }

    fun saveProduct(product: Product, departmentId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = if (product.id.isNotBlank()) {
                productRepository.updateProduct(product.id, product, departmentId)
            } else {
                productRepository.createProduct(product, departmentId)
            }
            result.fold(
                onSuccess = {
                    closeForm()
                    loadProducts(reset = true)
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Error al guardar producto") }
                },
            )
        }
    }

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
            val result = if (query.isEmpty()) {
                productRepository.getAllProducts(
                    page = _state.value.page,
                    pageSize = pageSize,
                    departmentId = _state.value.selectedDepartmentId,
                )
            } else {
                productRepository.searchProducts(
                    query = query,
                    page = _state.value.page,
                    pageSize = pageSize,
                )
            }
            result.fold(
                onSuccess = { newProducts ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            products = if (reset) newProducts else it.products + newProducts,
                            endOfListReached = newProducts.size < pageSize,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Error al cargar productos",
                        )
                    }
                },
            )
        }
    }
}
