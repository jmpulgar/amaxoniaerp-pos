package com.amaxonia.pos.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductFormViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProductFormState())
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    fun loadProduct(productId: String?) {
        if (productId == null) {
            _state.update { it.copy(isEditMode = false, product = Product(), error = null) }
        } else {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                productRepository.getProductById(productId).fold(
                    onSuccess = { product ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isEditMode = true,
                                product = product,
                                error = null
                            )
                        }
                    },
                    onFailure = { exception ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = exception.message ?: "Error al cargar el producto"
                            )
                        }
                    }
                )
            }
        }
    }

    fun updateField(block: Product.() -> Product) {
        _state.update { it.copy(product = it.product.block(), error = null) }
    }

    fun updatePriceRow(index: Int, block: PriceLevel.() -> PriceLevel) {
        _state.update { state ->
            val currentProduct = state.product
            val currentPrices = currentProduct.prices.toMutableList()
            var updatedRow = currentPrices[index].block()
            val cost = currentProduct.costActual
            if (cost > 0) {
                val priceWithUtility = cost * (1 + (updatedRow.utilityPercent / 100))
                val priceWithTax = priceWithUtility * (1 + (currentProduct.taxRate / 100))
                updatedRow = updatedRow.copy(
                    pricePlusUtility = priceWithUtility,
                    pricePlusTax = priceWithTax
                )
            }
            currentPrices[index] = updatedRow
            state.copy(product = currentProduct.copy(prices = currentPrices), error = null)
        }
    }

    fun recalculateAllPrices() {
        _state.update { state ->
            val cost = state.product.costActual
            val taxRate = state.product.taxRate
            val newPrices = state.product.prices.map { row ->
                val priceWithUtility = cost * (1 + (row.utilityPercent / 100))
                val priceWithTax = priceWithUtility * (1 + (taxRate / 100))
                row.copy(
                    pricePlusUtility = priceWithUtility,
                    pricePlusTax = priceWithTax
                )
            }
            state.copy(product = state.product.copy(prices = newPrices), error = null)
        }
    }

    fun saveProduct(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            productRepository.saveProduct(_state.value.product).fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    onSuccess()
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            error = exception.message ?: "Error al guardar el producto"
                        )
                    }
                }
            )
        }
    }
}
