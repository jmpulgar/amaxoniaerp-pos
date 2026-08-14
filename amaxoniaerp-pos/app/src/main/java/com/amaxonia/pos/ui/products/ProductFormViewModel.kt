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
    private val productRepository: ProductRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductFormState())
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    fun loadProduct(productId: String?) {
        if (productId == null) {
            _state.update { it.copy(isEditMode = false, product = Product(), error = null) }
            loadCatalogsForProduct(Product())
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
                                error = null,
                            )
                        }
                        loadCatalogsForProduct(product)
                    },
                    onFailure = { exception ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = exception.message ?: "Error al cargar el producto",
                            )
                        }
                    },
                )
            }
        }
    }

    fun updateField(block: Product.() -> Product) {
        _state.update { it.copy(product = it.product.block(), error = null) }
    }

    fun onDepartmentChanged(departmentId: Int) {
        _state.update {
            it.copy(
                product =
                    it.product.copy(
                        department = departmentId.toString(),
                        section = "",
                        family = "",
                        subFamily = "",
                    ),
                sections = emptyList(),
                families = emptyList(),
                subFamilies = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            productRepository.getSections(departmentId).fold(
                onSuccess = { sections ->
                    _state.update { it.copy(sections = sections) }
                },
                onFailure = { exception ->
                    _state.update { it.copy(error = exception.message ?: "Error al cargar secciones") }
                },
            )
        }
    }

    fun onSectionChanged(sectionId: Int) {
        _state.update {
            it.copy(
                product =
                    it.product.copy(
                        section = sectionId.toString(),
                        family = "",
                        subFamily = "",
                    ),
                families = emptyList(),
                subFamilies = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            productRepository.getFamilies(sectionId).fold(
                onSuccess = { families ->
                    _state.update { it.copy(families = families) }
                },
                onFailure = { exception ->
                    _state.update { it.copy(error = exception.message ?: "Error al cargar familias") }
                },
            )
        }
    }

    fun onFamilyChanged(familyId: Int) {
        _state.update {
            it.copy(
                product =
                    it.product.copy(
                        family = familyId.toString(),
                        subFamily = "",
                    ),
                subFamilies = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            productRepository.getSubFamilies(familyId).fold(
                onSuccess = { subFamilies ->
                    _state.update { it.copy(subFamilies = subFamilies) }
                },
                onFailure = { exception ->
                    _state.update { it.copy(error = exception.message ?: "Error al cargar subfamilias") }
                },
            )
        }
    }

    fun onBrandChanged(brandId: Int) {
        _state.update {
            it.copy(
                product =
                    it.product.copy(
                        brand = brandId.toString(),
                        line = "",
                    ),
                lines = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            productRepository.getLines(brandId).fold(
                onSuccess = { lines ->
                    _state.update { it.copy(lines = lines) }
                },
                onFailure = { exception ->
                    _state.update { it.copy(error = exception.message ?: "Error al cargar lineas") }
                },
            )
        }
    }

    fun onLineChanged(lineId: Int) {
        _state.update {
            it.copy(
                product = it.product.copy(line = lineId.toString()),
                error = null,
            )
        }
    }

    fun updatePriceRow(
        index: Int,
        block: PriceLevel.() -> PriceLevel,
    ) {
        _state.update { state ->
            val currentProduct = state.product
            val currentPrices = currentProduct.prices.toMutableList()
            var updatedRow = currentPrices[index].block()
            val cost = currentProduct.costActual
            val price = if (cost > 0) cost * (1 + (updatedRow.utilityPercent / 100)) else updatedRow.price
            val priceWithTax =
                if (currentProduct.isExempt) {
                    price
                } else {
                    price * (1 + (currentProduct.taxRate / 100))
                }
            updatedRow =
                updatedRow.copy(
                    price = price,
                    pricePlusUtility = price,
                    pricePlusTax = priceWithTax,
                )
            currentPrices[index] = updatedRow
            state.copy(product = currentProduct.copy(prices = currentPrices), error = null)
        }
    }

    fun recalculateAllPrices() {
        _state.update { state ->
            val cost = state.product.costActual
            val taxRate = state.product.taxRate
            val newPrices =
                state.product.prices.map { row ->
                    val price =
                        if (cost > 0) {
                            cost * (1 + (row.utilityPercent / 100))
                        } else {
                            row.price
                        }
                    val priceWithTax =
                        if (state.product.isExempt) {
                            price
                        } else {
                            price * (1 + (taxRate / 100))
                        }
                    row.copy(
                        price = price,
                        pricePlusUtility = price,
                        pricePlusTax = priceWithTax,
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
                    _state.update { current -> current.copy(isSaving = false) }
                    onSuccess()
                },
                onFailure = { exception ->
                    _state.update { current ->
                        current.copy(
                            isSaving = false,
                            error = exception.message ?: "Error al guardar el producto",
                        )
                    }
                },
            )
        }
    }

    private fun loadCatalogsForProduct(product: Product) {
        viewModelScope.launch {
            val departments = productRepository.getDepartments().getOrDefault(emptyList())
            val brands = productRepository.getBrands().getOrDefault(emptyList())

            val departmentId = product.department.toIntOrNull()
            val sectionId = product.section.toIntOrNull()
            val familyId = product.family.toIntOrNull()
            val brandId = product.brand.toIntOrNull()

            val sections = departmentId?.let { productRepository.getSections(it).getOrDefault(emptyList()) }.orEmpty()
            val families = sectionId?.let { productRepository.getFamilies(it).getOrDefault(emptyList()) }.orEmpty()
            val subFamilies = familyId?.let { productRepository.getSubFamilies(it).getOrDefault(emptyList()) }.orEmpty()
            val lines = brandId?.let { productRepository.getLines(it).getOrDefault(emptyList()) }.orEmpty()

            _state.update {
                it.copy(
                    departments = departments,
                    sections = sections,
                    families = families,
                    subFamilies = subFamilies,
                    brands = brands,
                    lines = lines,
                )
            }
        }
    }
}
