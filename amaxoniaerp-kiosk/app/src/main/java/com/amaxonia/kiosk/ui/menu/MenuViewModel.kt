package com.amaxonia.kiosk.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskCatalogResponse
import com.amaxonia.kiosk.core.network.KioskCategoryDto
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.core.network.NetworkResult
import com.amaxonia.kiosk.domain.cart.OrderGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MenuUiState(
    val isLoading: Boolean = true,
    val categories: List<KioskCategoryDto> = emptyList(),
    val selectedCategoryId: Int? = null,
    val items: List<KioskItemDto> = emptyList(),
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    val errorMessage: String? = null,
    val cartItemCount: Int = 0,
    val cartSubtotal: Money = Money.ZERO,
) {
    val currentCategory: KioskCategoryDto?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    val filteredItems: List<KioskItemDto>
        get() =
            if (selectedCategoryId == null) {
                items
            } else {
                items.filter { it.categoryId == selectedCategoryId }
            }
}

class MenuViewModel(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
    val orderGraph: OrderGraph,
    private val catalogState: MutableStateFlow<KioskCatalogResponse?>? = null,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            MenuUiState(currency = orderGraph.currencyConfig.value),
        )
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    var loadCatalogJob: Job? = null
        private set

    init {
        // Observe cart changes
        orderGraph.lines
            .onEach { lines ->
                _uiState.update {
                    it.copy(
                        cartItemCount = lines.sumOf { line -> line.quantity },
                        cartSubtotal = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal },
                    )
                }
            }
            .launchIn(viewModelScope)

        orderGraph.currencyConfig
            .onEach { curr ->
                _uiState.update { it.copy(currency = curr) }
            }
            .launchIn(viewModelScope)

        loadCatalogJob = loadCatalog()
    }

    fun selectCategory(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onProductClicked(
        item: KioskItemDto,
        onOpenCustomizer: (Int) -> Unit,
    ) {
        if (item.soldOut) return

        if (item.modifierGroups.isNotEmpty()) {
            onOpenCustomizer(item.id)
        } else {
            orderGraph.addLine(item)
            _uiState.update {
                it.copy(
                    cartItemCount = orderGraph.totalItemCount,
                    cartSubtotal = orderGraph.subtotal,
                )
            }
        }
    }

    fun loadCatalog(): Job {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val job =
            viewModelScope.launch {
                // Only revalidate with the ETag when a catalog is actually held in memory; otherwise a
                // 304 after a process restart would leave the menu empty.
                val cached = catalogState?.value
                val result = apiClient.getCatalog(if (cached != null) tokenStorage.catalogEtag else null)
                when (result) {
                    is NetworkResult.Success -> {
                        catalogState?.value = result.data
                        showCatalog(result.data)
                    }
                    is NetworkResult.NotModified -> {
                        if (cached != null) {
                            showCatalog(cached)
                        } else {
                            _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                        }
                    }
                    is NetworkResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = it.errorMessage ?: result.error.message ?: "Error al cargar catálogo",
                            )
                        }
                    }
                }
            }
        loadCatalogJob = job
        return job
    }

    private fun showCatalog(catalog: KioskCatalogResponse) {
        val sortedCategories = catalog.categories.sortedBy { it.order }
        val firstCatId = sortedCategories.firstOrNull()?.id
        _uiState.update {
            it.copy(
                isLoading = false,
                categories = sortedCategories,
                selectedCategoryId = it.selectedCategoryId ?: firstCatId,
                items = catalog.items,
                errorMessage = null,
            )
        }
    }
}
