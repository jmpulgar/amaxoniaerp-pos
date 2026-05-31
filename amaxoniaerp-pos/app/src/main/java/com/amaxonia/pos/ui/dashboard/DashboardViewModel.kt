package com.amaxonia.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val productRepository: ProductRepository,
    private val reportRepository: ReportRepository,
    private val cartRepository: CartRepository,
    private val cajaRepository: CajaRepository,
    private val localStore: com.amaxonia.pos.data.local.LocalStore,
    private val apiConfigManager: com.amaxonia.pos.data.remote.ApiConfigManager
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    @Volatile
    private var adminDb: String = ""
    private var searchJob: Job? = null

    private companion object {
        const val PAGE_SIZE = 40
    }

    init {
        viewModelScope.launch {
            val savedCountry = localStore.readSelectedCountry()
            if (savedCountry != null) {
                apiConfigManager.updateBaseUrl(savedCountry)
            }
            val companySession = localStore.readCompanySession()
            adminDb = companySession?.company?.adminDb ?: ""
        }
        loadProducts(reset = true)
        loadBestSellers()
        observeCart()
        observeClient()
        observeSeller()
        observeCaja()
        viewModelScope.launch {
            cajaRepository.restoreActiveCajaIfValid()
            fetchAvailableCajas()
        }
    }

    private fun observeCaja() {
        viewModelScope.launch {
            cajaRepository.activeCajaName.collect { cajaName ->
                _state.update { it.copy(cajaPrincipalNombre = cajaName) }
            }
        }

        viewModelScope.launch {
            cajaRepository.activeCaja.collect { caja ->
                val sucursal = caja?.sucursalNombre
                    ?.takeIf { it.isNotBlank() }
                    ?: "Sucursal"
                _state.update { it.copy(sucursalNombre = sucursal) }

                if (caja != null) {
                    cartRepository.setSellerContext(
                        defaultSellerId = caja.defaultSellerId,
                        defaultSellerName = caja.defaultSellerName,
                        sellers = caja.availableSellers,
                    )
                }
            }
        }
    }

    private fun observeSeller() {
        viewModelScope.launch {
            cartRepository.currentSeller.collect { seller ->
                _state.update { it.copy(currentSeller = seller) }
            }
        }

        viewModelScope.launch {
            cartRepository.availableSellers.collect { sellers ->
                _state.update { it.copy(availableSellers = sellers) }
            }
        }
    }

    fun fetchAvailableCajas(forceShowSelector: Boolean = false) {
        viewModelScope.launch {
            val keepSelectorVisible = forceShowSelector || _state.value.showCajaSelector
            _state.update { it.copy(isLoadingCajas = true, showCajaSelector = keepSelectorVisible) }
            cajaRepository.getCajas().fold(
                onSuccess = { cajas ->
                    val shouldShowSelector = forceShowSelector || cajaRepository.activeCaja.value == null
                    _state.update { 
                        it.copy(
                            availableCajas = cajas,
                            isLoadingCajas = false,
                            showCajaSelector = shouldShowSelector
                        ) 
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isLoadingCajas = false,
                            error = "Error al cargar cajas: ${error.message}"
                        ) 
                    }
                }
            )
        }
    }

    fun selectAndOpenCaja(caja: Caja, montoApertura: Double) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCajas = true) }

            val secuencia = cajaRepository.getNextSecuenciaCodigo(caja.idCaja).getOrElse { error ->
                _state.update {
                    it.copy(
                        isLoadingCajas = false,
                        error = "Error al obtener correlativo de caja: ${error.message}"
                    )
                }
                return@launch
            }

            val sellerId = _state.value.currentSeller?.id ?: caja.defaultSellerId

            val request = AperturaRequest(
                idCaja = caja.idCaja,
                montoApertura = montoApertura,
                idVendedor = sellerId,
                secuencia = secuencia,
                serieSucursal = caja.serieSucursal ?: caja.serieCaja,
                idSucursal = caja.idSucursal,
                facturaInicial = 0,
                notacreditoInicial = 0,
                devolucionInicial = 0,
                zInicial = 0
            )

            cajaRepository.openCaja(request).fold(
                onSuccess = {
                    cajaRepository.setActiveCaja(caja)
                    _state.update {
                        it.copy(
                            isLoadingCajas = false,
                            showCajaSelector = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoadingCajas = false,
                            error = "Error al abrir caja: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun dismissAutoCloseMessage() {
        _state.update { it.copy(autoCloseMessage = null) }
    }

    fun getProductImageUrl(photoPath: String): String {
        if (photoPath.isBlank() || adminDb.isBlank()) return ""
        val url = com.amaxonia.pos.data.remote.ImageUrlHelper.productImageUrl(
            baseUrl = apiConfigManager.baseUrl.value,
            countryCode = apiConfigManager.getCurrentCountryCode(),
            companyDb = adminDb,
            photoPath = photoPath
        )
        android.util.Log.d("IMG_URL", "baseUrl=${apiConfigManager.baseUrl.value} country=${apiConfigManager.getCurrentCountryCode()} db=$adminDb path=$photoPath -> url=$url")
        return url
    }

    fun getClientPhotoUrl(client: Client): String {
        if (client.id.isBlank() || adminDb.isBlank()) return ""
        val filename = client.photoFilename?.takeIf { it.isNotBlank() }
            ?: return ""
        return com.amaxonia.pos.data.remote.ImageUrlHelper.clientPhotoUrl(
            baseUrl = apiConfigManager.baseUrl.value,
            countryCode = apiConfigManager.getCurrentCountryCode(),
            companyDb = adminDb,
            idCliente = client.id,
            photoFilename = filename
        )
    }

    private fun observeCart() {
        viewModelScope.launch {
            cartRepository.cartItems.collect { items ->
                _state.update {
                    it.copy(
                        cartItems = items,
                        cartItemCount = items.sumOf { item -> item.quantity },
                        cartTotal = items.sumOf { item -> item.total }
                    )
                }
            }
        }
    }

    // Nuevo: Observar cambios en el cliente seleccionado
    private fun observeClient() {
        viewModelScope.launch {
            cartRepository.selectedClient.collect { client ->
                _state.update { it.copy(selectedClient = client) }
            }
        }
    }

    fun setShowCajaSelector(show: Boolean) {
        _state.update { it.copy(showCajaSelector = show, error = null) }
    }

    fun loadDepartments() {
        viewModelScope.launch {
            productRepository.getDepartments().fold(
                onSuccess = { list ->
                    _state.update { it.copy(departments = list) }
                },
                onFailure = { }
            )
        }
    }

    fun setShowDepartmentPicker(show: Boolean) {
        _state.update { it.copy(showDepartmentPicker = show) }
        if (show && _state.value.departments.isEmpty()) loadDepartments()
    }

    fun selectDepartment(departmentId: Int?) {
        _state.update {
            it.copy(
                selectedDepartmentId = departmentId,
                showDepartmentPicker = false,
                selectedCategory = if (departmentId == null) "Todos los productos" else it.departments.find { d -> d.id == departmentId }?.name ?: "Todos los productos",
                searchQuery = it.searchQuery
            )
        }
        loadProducts(reset = true)
    }

    private fun loadProducts(reset: Boolean) {
        viewModelScope.launch {
            if (adminDb.isBlank()) adminDb = localStore.readCompanySession()?.company?.adminDb ?: ""
            val nextPage = if (reset) 1 else _state.value.page + 1
            _state.update {
                it.copy(
                    isLoading = reset && it.products.isEmpty(),
                    isLoadingMore = !reset,
                    page = nextPage,
                    products = if (reset) emptyList() else it.products,
                    endOfListReached = if (reset) false else it.endOfListReached,
                    error = null
                )
            }
            val departmentId = _state.value.selectedDepartmentId
            val query = _state.value.searchQuery.trim()
            val result = if (query.isBlank()) {
                productRepository.getAllProducts(departmentId, nextPage, PAGE_SIZE)
            } else {
                productRepository.searchProducts(query, departmentId, nextPage, PAGE_SIZE)
            }
            result.fold(
                onSuccess = { products ->
                    val dashboardProducts = products.map { product ->
                        DashboardProduct(
                            id = product.id,
                            name = product.description,
                            price = product.prices.firstOrNull()?.pricePlusTax ?: 0.0,
                            taxRate = product.taxRate,
                            isExempt = product.isExempt,
                            imageUrl = getProductImageUrl(product.photoUrl),
                            category = product.department.ifEmpty { "General" },
                            code = product.code,
                            barcode = product.barcode1
                        )
                    }
                    _state.update {
                        it.copy(
                            products = if (reset) dashboardProducts else it.products + dashboardProducts,
                            isLoading = false,
                            isLoadingMore = false,
                            endOfListReached = products.size < PAGE_SIZE,
                            error = null
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = exception.message ?: "Error al cargar productos"
                        )
                    }
                }
            )
        }
    }

    fun loadMoreProducts() {
        val current = _state.value
        if (current.bottomSelected != 0 || current.isLoading || current.isLoadingMore || current.endOfListReached) return
        loadProducts(reset = false)
    }

    // --- LÓGICA DEL CARRITO CORREGIDA ---

    fun addToCart(dashboardProduct: DashboardProduct) {
        val product = com.amaxonia.pos.domain.model.Product(
            id = dashboardProduct.id,
            description = dashboardProduct.name,
            prices = listOf(com.amaxonia.pos.domain.model.PriceLevel(label = "A", pricePlusTax = dashboardProduct.price)),
            taxRate = dashboardProduct.taxRate,
            isExempt = dashboardProduct.isExempt,
            department = dashboardProduct.category,
            code = dashboardProduct.code ?: "",
            barcode1 = dashboardProduct.barcode ?: ""
        )
        cartRepository.addToCart(product)

        // Consultar lotes FEFO en background y asignar automaticamente
        viewModelScope.launch {
            refreshLotsForProduct(product.id)
        }
    }

    /** Consulta lotes FEFO desde el backend y los asigna automaticamente al item */
    private suspend fun refreshLotsForProduct(productId: String) {
        val session = localStore.readCompanySession() ?: return
        val token = session.token
        val apiService = com.amaxonia.pos.ui.common.DependencyContainer.apiService

        runCatching {
            val response = apiService.getItemLots(token, productId)
            if (response.poseeConfiguracionLote) {
                cartRepository.setItemHasLotConfig(productId, true)

                // Obtener cantidad total del item en el carrito
                val cartItem = cartRepository.cartItems.value.firstOrNull { it.product.id == productId }
                val totalQty = cartItem?.quantity ?: 0
                if (totalQty > 0 && response.lotes.isNotEmpty()) {
                    val assignments = assignFefo(response.lotes, totalQty)
                    cartRepository.assignLots(productId, assignments)
                }
            }
        }
    }

    /** Distribuye la cantidad solicitada entre lotes del mas viejo al mas nuevo */
    private fun assignFefo(
        lots: List<com.amaxonia.pos.data.remote.dto.ItemLotInfoDto>,
        totalQty: Int
    ): List<LotAssignment> {
        val assignments = mutableListOf<LotAssignment>()
        var remaining = totalQty
        for (lot in lots) {
            if (remaining <= 0) break
            val take = minOf(remaining, lot.disponibilidad)
            if (take > 0) {
                assignments.add(
                    LotAssignment(
                        idLoteItem = lot.idLoteItem.toString(),
                        codigoLote = lot.codigoLoteItem,
                        vencimiento = lot.vencimiento,
                        cantidad = take,
                        almacen = lot.idAlmacen
                    )
                )
                remaining -= take
            }
        }
        return assignments
    }

    fun toggleSearch() {
        var shouldReload = false
        _state.update { s ->
            val open = !s.isSearchOpen
            shouldReload = !open && s.searchQuery.isNotBlank()
            s.copy(isSearchOpen = open, searchQuery = if (!open) "" else s.searchQuery)
        }
        if (shouldReload) loadProducts(reset = true)
    }

    fun setSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            loadProducts(reset = true)
        }
    }

    fun toggleViewMode() {
        _state.update { s ->
            s.copy(viewMode = if (s.viewMode == ProductViewMode.GRID) ProductViewMode.LIST else ProductViewMode.GRID)
        }
    }

    fun setBottomSelected(index: Int) {
        _state.update { it.copy(bottomSelected = index) }
        if (index == 1 && _state.value.bestSellers.isEmpty()) {
            loadBestSellers()
        }
    }

    private fun loadBestSellers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingBestSellers = true, error = null) }
            reportRepository.getBestSellers().fold(
                onSuccess = { bestSellers ->
                    val dashboardProducts = bestSellers.take(10).map { bestSeller ->
                        DashboardProduct(
                            id = bestSeller.id,
                            name = bestSeller.name,
                            price = bestSeller.price,
                            taxRate = 0.0,
                            isExempt = false,
                            imageUrl = getProductImageUrl(bestSeller.photoUrl),
                            category = "Más Vendidos",
                            code = null,
                            barcode = null
                        )
                    }
                    _state.update {
                        it.copy(
                            bestSellers = dashboardProducts,
                            isLoadingBestSellers = false,
                            error = null
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoadingBestSellers = false,
                            error = exception.message ?: "Error al cargar productos más vendidos"
                        )
                    }
                }
            )
        }
    }

    fun retry() {
        loadProducts(reset = true)
    }

    // Lógica para la calculadora manual
    fun onManualKey(key: String) {
        _state.update { s ->
            // Evitar múltiples puntos decimales
            if (key == "." && s.manualEntryValue.contains(".")) return@update s

            // Limitar longitud si es necesario
            if (s.manualEntryValue.length > 10) return@update s

            s.copy(manualEntryValue = s.manualEntryValue + key)
        }
    }

    fun onManualClear() {
        _state.update { it.copy(manualEntryValue = "") }
    }

    fun onManualBackspace() {
        _state.update { s ->
            if (s.manualEntryValue.isNotEmpty()) {
                s.copy(manualEntryValue = s.manualEntryValue.dropLast(1))
            } else s
        }
    }

    fun onManualSubmit() {
        val valueStr = _state.value.manualEntryValue
        val value = valueStr.toDoubleOrNull()

        if (value != null && value > 0) {
            // Creamos un producto "dummy" para la entrada manual
            val manualProduct = DashboardProduct(
                id = "manual_${System.currentTimeMillis()}",
                name = "Entrada Manual",
                price = value,
                taxRate = 0.0,
                isExempt = true,
                category = "Manual"
            )
            addToCart(manualProduct)
            onManualClear() // Limpiamos la pantalla después de agregar
        }
    }

    // --- NUEVO: Función para quitar el cliente desde el dashboard si se desea ---
    fun clearSelectedClient() {
        cartRepository.removeClient()
    }

    fun selectSeller(sellerId: Int) {
        val seller = cartRepository.availableSellers.value.firstOrNull { it.id == sellerId } ?: return
        cartRepository.setCurrentSeller(seller)
    }

    // --- NUEVO: Función para iniciar "Crear Pedido" (limpia carrito anterior) ---
    fun startNewOrder() {
        cartRepository.clearCart()
        // La navegación se maneja en la UI, aquí solo limpiamos el estado
    }
}
