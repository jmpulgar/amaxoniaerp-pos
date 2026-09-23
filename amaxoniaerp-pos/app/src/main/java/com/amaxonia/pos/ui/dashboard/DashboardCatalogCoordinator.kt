package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.offline.OfflineScopeSelection
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import com.amaxonia.pos.domain.repository.ServerEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardCatalogCoordinator(
    private val productRepository: ProductRepository,
    private val reportRepository: ReportRepository,
    private val sessionReader: DashboardSessionReader,
    private val serverEnvironment: ServerEnvironment,
    private val productMapper: DashboardProductMapper,
    private val visibilityScopeFlow: Flow<OfflineScopeSelection>? = null,
) {
    @Volatile
    private var adminDatabase: String = ""
    @Volatile
    private var currentScope: OfflineScopeSelection = OfflineScopeSelection()
    private var searchJob: Job? = null
    private var loadProductsJob: Job? = null
    private var scopeObserverJob: Job? = null

    fun start(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        scope.launch {
            sessionReader.currentCountry()?.let(serverEnvironment::selectCountry)
            adminDatabase = sessionReader.currentAdminDatabase()
        }
        if (visibilityScopeFlow != null) {
            scopeObserverJob?.cancel()
            scopeObserverJob =
                scope.launch {
                    visibilityScopeFlow.collect { syncScope ->
                        onScopeUpdated(syncScope, scope, state)
                    }
                }
        } else {
            loadDepartments(scope, state)
            loadProducts(scope, state, reset = true)
        }
        loadBestSellers(scope, state)
    }

    fun stop() {
        searchJob?.cancel()
        loadProductsJob?.cancel()
        scopeObserverJob?.cancel()
    }

    private fun onScopeUpdated(
        syncScope: OfflineScopeSelection,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        currentScope = syncScope
        scope.launch {
            productRepository.getDepartments().onSuccess { allDepartments ->
                val visibleDepartments =
                    if (syncScope.allProducts) {
                        allDepartments
                    } else {
                        allDepartments.filter { it.id in syncScope.departmentIds }
                    }

                val newSelectedId =
                    if (!syncScope.allProducts && syncScope.departmentIds.isNotEmpty()) {
                        if (state.value.selectedDepartmentId in syncScope.departmentIds) {
                            state.value.selectedDepartmentId
                        } else {
                            syncScope.departmentIds.first()
                        }
                    } else {
                        if (state.value.selectedDepartmentId != null && visibleDepartments.any { it.id == state.value.selectedDepartmentId }) {
                            state.value.selectedDepartmentId
                        } else {
                            null
                        }
                    }
                val categoryName =
                    newSelectedId?.let { id -> visibleDepartments.find { it.id == id }?.name }
                        ?: "Todos los productos"

                state.update { current ->
                    current.copy(
                        departments = visibleDepartments,
                        selectedDepartmentId = newSelectedId,
                        selectedCategory = categoryName,
                    )
                }
                loadProducts(scope, state, reset = true)
            }
        }
    }

    fun onAction(
        action: DashboardCatalogUiAction,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardCatalogUiAction.Paging -> onPagingAction(action, scope, state)
            is DashboardCatalogUiAction.Search -> onSearchAction(action, scope, state)
            is DashboardCatalogUiAction.View -> onViewAction(action, scope, state)
            is DashboardCatalogUiAction.Department -> onDepartmentAction(action, scope, state)
            is DashboardCatalogUiAction.Filter -> onFilterAction(action, scope, state)
        }
    }

    private fun onPagingAction(
        action: DashboardCatalogUiAction.Paging,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            DashboardCatalogUiAction.LoadMoreProducts -> {
                val current = state.value
                val canLoad =
                    current.bottomSelected == PRODUCTS_TAB &&
                        !current.endOfListReached &&
                        !current.isLoading &&
                        !current.isLoadingMore
                if (canLoad) loadProducts(scope, state, reset = false)
            }
            DashboardCatalogUiAction.Retry -> loadProducts(scope, state, reset = true)
            DashboardCatalogUiAction.RefreshCatalog -> {
                loadDepartments(scope, state)
                loadProducts(scope, state, reset = true)
                loadBestSellers(scope, state)
            }
        }
    }

    private fun onSearchAction(
        action: DashboardCatalogUiAction.Search,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            DashboardCatalogUiAction.ToggleSearch -> {
                var shouldReload = false
                state.update { current ->
                    val open = !current.isSearchOpen
                    shouldReload = !open && current.searchQuery.isNotBlank()
                    current.copy(isSearchOpen = open, searchQuery = if (!open) "" else current.searchQuery)
                }
                if (shouldReload) loadProducts(scope, state, reset = true)
            }
            is DashboardCatalogUiAction.SetSearchQuery -> {
                state.update { it.copy(searchQuery = action.value, error = null) }
                searchJob?.cancel()
                loadProductsJob?.cancel()
                searchJob =
                    scope.launch {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                        loadProducts(scope, state, reset = true)
                    }
            }
        }
    }

    private fun onViewAction(
        action: DashboardCatalogUiAction.View,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            DashboardCatalogUiAction.ToggleViewMode ->
                state.update { current ->
                    val mode = if (current.viewMode == ProductViewMode.GRID) ProductViewMode.LIST else ProductViewMode.GRID
                    current.copy(viewMode = mode)
                }
            is DashboardCatalogUiAction.SetBottomSelected -> {
                state.update { it.copy(bottomSelected = action.index) }
                if (action.index == BEST_SELLERS_TAB && state.value.bestSellers.isEmpty()) {
                    loadBestSellers(scope, state)
                }
            }
        }
    }

    private fun onDepartmentAction(
        action: DashboardCatalogUiAction.Department,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardCatalogUiAction.SetDepartmentPicker -> {
                state.update { it.copy(showDepartmentPicker = action.show) }
                if (action.show && state.value.departments.isEmpty()) loadDepartments(scope, state)
            }
            is DashboardCatalogUiAction.SelectDepartment -> {
                state.update { current ->
                    current.copy(
                        selectedDepartmentId = action.departmentId,
                        showDepartmentPicker = false,
                        selectedCategory =
                            action.departmentId?.let { id -> current.departments.find { it.id == id }?.name }
                                ?: "Todos los productos",
                        searchQuery = current.searchQuery,
                    )
                }
                loadProducts(scope, state, reset = true)
            }
        }
    }

    private fun onFilterAction(
        action: DashboardCatalogUiAction.Filter,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardCatalogUiAction.SetFilterModalOpen -> {
                state.update { it.copy(isFilterModalOpen = action.open) }
                if (action.open && state.value.departments.isEmpty()) {
                    loadDepartments(scope, state)
                }
            }
            is DashboardCatalogUiAction.SelectItemType -> {
                state.update { it.copy(selectedItemType = action.type) }
                loadProducts(scope, state, reset = true)
            }
            is DashboardCatalogUiAction.ApplyFilters -> {
                state.update { current ->
                    current.copy(
                        selectedDepartmentId = action.departmentId,
                        selectedItemType = action.itemType,
                        isFilterModalOpen = false,
                        selectedCategory =
                            action.departmentId?.let { id -> current.departments.find { it.id == id }?.name }
                                ?: "Todos los productos",
                    )
                }
                loadProducts(scope, state, reset = true)
            }
            DashboardCatalogUiAction.ClearFilters -> {
                state.update { current ->
                    current.copy(
                        selectedDepartmentId = null,
                        selectedItemType = ItemTypeFilter.ALL,
                        selectedCategory = "Todos los productos",
                        isFilterModalOpen = false,
                    )
                }
                loadProducts(scope, state, reset = true)
            }
        }
    }

    private fun loadDepartments(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        scope.launch {
            productRepository.getDepartments().onSuccess { allDepartments ->
                val visibleDepartments =
                    if (currentScope.allProducts) {
                        allDepartments
                    } else {
                        allDepartments.filter { it.id in currentScope.departmentIds }
                    }
                state.update { it.copy(departments = visibleDepartments) }
            }
        }
    }

    private fun loadProducts(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        reset: Boolean,
    ) {
        if (reset) {
            loadProductsJob?.cancel()
        }
        loadProductsJob =
            scope.launch {
                if (adminDatabase.isBlank()) adminDatabase = sessionReader.currentAdminDatabase()
                val nextPage = if (reset) FIRST_PAGE else state.value.page + 1
                state.update {
                    it.copy(
                        isLoading = reset,
                        isLoadingMore = !reset,
                        page = nextPage,
                        endOfListReached = if (reset) false else it.endOfListReached,
                        error = null,
                    )
                }
                val departmentId = state.value.selectedDepartmentId
                val query = state.value.searchQuery.trim()
                val itemTypeStr =
                    when (state.value.selectedItemType) {
                        ItemTypeFilter.ALL -> null
                        ItemTypeFilter.PRODUCT -> "PRODUCT"
                        ItemTypeFilter.SERVICE -> "SERVICE"
                    }
                val result =
                    if (query.isBlank()) {
                        productRepository.getAllProducts(departmentId, nextPage, PAGE_SIZE, itemTypeStr)
                    } else {
                        productRepository.searchProducts(query, departmentId, nextPage, PAGE_SIZE, itemTypeStr)
                    }
                result.fold(
                    onSuccess = { products ->
                        if (!isActive) return@launch
                        val dashboardProducts = products.map { productMapper.fromProduct(it, adminDatabase) }
                        state.update {
                            it.copy(
                                products = if (reset) dashboardProducts else it.products + dashboardProducts,
                                isLoading = false,
                                isLoadingMore = false,
                                endOfListReached = products.size < PAGE_SIZE,
                                error = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException || !isActive) return@launch
                        state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = error.message ?: "Error al cargar productos",
                            )
                        }
                    },
                )
            }
    }

    private fun loadBestSellers(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        scope.launch {
            state.update { it.copy(isLoadingBestSellers = true, error = null) }
            reportRepository.getBestSellers().fold(
                onSuccess = { bestSellers ->
                    val products =
                        bestSellers.take(BEST_SELLERS_LIMIT).map { bestSeller ->
                            DashboardProduct(
                                id = bestSeller.id,
                                name = bestSeller.name,
                                price = bestSeller.price,
                                taxRate = 0.0,
                                isExempt = false,
                                imageUrl = productMapper.imageUrl(adminDatabase, bestSeller.photoUrl),
                                category = "Más Vendidos",
                                code = null,
                                barcode = null,
                            )
                        }
                    state.update {
                        it.copy(bestSellers = products, isLoadingBestSellers = false, error = null)
                    }
                },
                onFailure = {
                    state.update {
                        it.copy(isLoadingBestSellers = false)
                    }
                },
            )
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 40
        const val PRODUCTS_TAB = 0
        const val BEST_SELLERS_TAB = 1
        const val BEST_SELLERS_LIMIT = 10
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
