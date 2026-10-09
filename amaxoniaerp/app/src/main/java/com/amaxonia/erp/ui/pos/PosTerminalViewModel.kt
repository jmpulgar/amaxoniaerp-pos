package com.amaxonia.erp.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.SaleInvoiceDto
import com.amaxonia.erp.data.remote.dto.SaleItemDto
import com.amaxonia.erp.data.remote.dto.SalePaymentDto
import com.amaxonia.erp.data.remote.dto.SalePaymentSummaryDto
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.repository.CajaRepository
import com.amaxonia.erp.domain.repository.ClientRepository
import com.amaxonia.erp.domain.repository.PagedProducts
import com.amaxonia.erp.domain.repository.ProductRepository
import com.amaxonia.erp.domain.repository.SalesRepository
import com.amaxonia.erp.domain.util.CajaDateParser
import com.amaxonia.erp.data.printer.DefaultInvoicePrintGateway
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.SellerSummary
import com.amaxonia.erp.ui.customerdisplay.CustomerDisplayManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.amaxonia.erp.BuildConfig
import com.amaxonia.erp.data.local.db.PendingInvoiceDao
import com.amaxonia.erp.data.local.db.PendingInvoiceEntity
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.NetworkMonitor
import java.util.Locale
import java.util.UUID

class PosTerminalViewModel(
    private val productRepository: ProductRepository,
    private val clientRepository: ClientRepository,
    private val cajaRepository: CajaRepository,
    private val salesRepository: SalesRepository,
    private val localStore: LocalStore,
    private val printGateway: DefaultInvoicePrintGateway? = null,
    private val customerDisplayManager: CustomerDisplayManager? = null,
    private val promotionRepository: com.amaxonia.erp.domain.repository.PromotionRepository? = null,
    private val pendingInvoiceDao: PendingInvoiceDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val onOfflineInvoiceQueued: (() -> Unit)? = null,
) : ViewModel() {


    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeCajaChanges()
        observeCustomerDisplaySync()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            localStore.allowEditPricesFlow().collect { allowed ->
                _uiState.update { it.copy(allowEditPrices = allowed) }
            }
        }
        viewModelScope.launch {
            localStore.allowDiscountsFlow().collect { allowed ->
                _uiState.update { it.copy(allowDiscounts = allowed) }
            }
        }
    }

    private fun observeCustomerDisplaySync() {
        viewModelScope.launch {
            _uiState
                .distinctUntilChangedBy {
                    listOf(
                        it.cart,
                        it.selectedClient?.id,
                        it.isProcessingSale,
                        it.completedSaleInfo,
                        it.sucursalNombre,
                        it.globalDiscountPercent,
                    )
                }
                .collect { state ->
                    customerDisplayManager?.updateCart(
                        cart = state.cart,
                        summary = state.summary,
                        client = state.selectedClient,
                        isProcessingSale = state.isProcessingSale,
                        completedSaleInfo = state.completedSaleInfo,
                        branchName = state.sucursalNombre.orEmpty(),
                    )
                }
        }
    }

    private var lastPromptedSecuenciaId: String? = null

    private fun observeCajaChanges() {
        viewModelScope.launch {
            combine(
                cajaRepository.activeCaja,
                cajaRepository.activeCajaSecuencia,
            ) { caja, secuencia ->
                Pair(caja, secuencia)
            }.collect { (caja, secuencia) ->
                val branchName = caja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal Principal"
                val warehouseName =
                    caja?.almacenNombre?.takeIf(String::isNotBlank)
                        ?: caja?.defaultWarehouseId?.let { "Almacén $it" }
                        ?: caja?.codAlmacen?.takeIf { it > 0 }?.let { "Almacén $it" }
                        ?: "Almacén Principal"
                val rawFecha = secuencia?.fechaApertura
                val formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                val isDiaAnterior = secuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                val isOpen = secuencia != null && (caja == null || secuencia.idCaja == caja.idCaja)
                val seqId = secuencia?.idCajaSecuencia
                val shouldPrompt = isDiaAnterior && isOpen && !seqId.isNullOrBlank() && seqId != lastPromptedSecuenciaId
                if (shouldPrompt) {
                    lastPromptedSecuenciaId = seqId
                } else if (seqId == null || !isDiaAnterior) {
                    lastPromptedSecuenciaId = null
                }

                val sellers = caja?.availableSellers.orEmpty().ifEmpty {
                    listOf(SellerSummary(1, "Vendedor 1"))
                }
                val defaultSeller = caja?.defaultSellerId?.let { id ->
                    sellers.firstOrNull { it.id == id } ?: SellerSummary(id, caja.defaultSellerName ?: "Vendedor $id")
                } ?: sellers.firstOrNull() ?: SellerSummary(1, "Vendedor 1")

                _uiState.update { current ->
                    current.copy(
                        activeCajaId = caja?.idCaja ?: current.activeCajaId,
                        activeCajaName = caja?.displayName ?: current.activeCajaName,
                        activeCajaSecuenciaId = seqId ?: current.activeCajaSecuenciaId,
                        sucursalNombre = branchName,
                        almacenNombre = warehouseName,
                        isCajaOpen = isOpen,
                        isCajaDiaAnterior = isDiaAnterior,
                        cajaFechaApertura = formattedFecha,
                        usuarioApertura = secuencia?.usuarioApertura,
                        showAvisoCajaAnterior = if (shouldPrompt) true else (if (isDiaAnterior) current.showAvisoCajaAnterior else false),
                        availableSellers = sellers,
                        selectedSeller = current.selectedSeller ?: defaultSeller,
                    )
                }
            }
        }
    }

    companion object {
        const val CATALOG_PAGE_SIZE = 12
    }

    private var catalogLoadJob: Job? = null

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            coroutineScope {
                // 1. Tarea Caja & Pagos en paralelo
                val cajaAndPaymentsDeferred = async {
                    var activeCaja = cajaRepository.activeCaja.value
                    var cajaId = activeCaja?.idCaja ?: cajaRepository.getActiveCaja()?.first

                    if (cajaId == null) {
                        cajaRepository.getCajas().onSuccess { cajas ->
                            val openCaja = findFirstOpenCaja(cajas) ?: cajas.singleOrNull() ?: cajas.firstOrNull()
                            if (openCaja != null) {
                                cajaRepository.setActiveCaja(openCaja)
                                activeCaja = openCaja
                                cajaId = openCaja.idCaja
                            }
                        }
                    }

                    var isCajaOpen = false
                    var isDiaAnterior = false
                    var formattedFecha: String? = null
                    var usuarioApertura: String? = null
                    var activeCajaSecuenciaId: String? = null
                    if (cajaId != null) {
                        val statusResult = cajaRepository.checkCajaStatus(cajaId!!)
                        val status = statusResult.getOrNull()
                        isCajaOpen = status?.isOpen == true
                        val rawFecha = status?.cajaSecuencia?.fechaApertura
                        formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                        isDiaAnterior = status?.cajaSecuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                        usuarioApertura = status?.cajaSecuencia?.usuarioApertura
                        activeCajaSecuenciaId = status?.cajaSecuencia?.idCajaSecuencia
                    }
                    val branchName = activeCaja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal Principal"
                    val warehouseName =
                        activeCaja?.almacenNombre?.takeIf(String::isNotBlank)
                            ?: activeCaja?.defaultWarehouseId?.let { "Almacén $it" }
                            ?: activeCaja?.codAlmacen?.takeIf { it > 0 }?.let { "Almacén $it" }
                            ?: "Almacén Principal"

                    val sellers = activeCaja?.availableSellers.orEmpty().ifEmpty {
                        listOf(SellerSummary(1, "Vendedor 1"))
                    }
                    val defaultSeller = activeCaja?.defaultSellerId?.let { id ->
                        sellers.firstOrNull { it.id == id } ?: SellerSummary(id, activeCaja.defaultSellerName ?: "Vendedor $id")
                    } ?: sellers.firstOrNull() ?: SellerSummary(1, "Vendedor 1")

                    val paymentMethods = salesRepository.getFormasPago(cajaId).getOrElse {
                        listOf(
                            FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
                            FormaPagoDto(idFormaPago = 2, siglas = "TARJ", codigo = "TARJETA", descripcion = "Tarjeta"),
                            FormaPagoDto(idFormaPago = 3, siglas = "TRANS", codigo = "TRANSFERENCIA", descripcion = "Transferencia"),
                        )
                    }

                    CajaInitBundle(
                        activeCaja = activeCaja,
                        cajaId = cajaId,
                        isCajaOpen = isCajaOpen,
                        isDiaAnterior = isDiaAnterior,
                        formattedFecha = formattedFecha,
                        usuarioApertura = usuarioApertura,
                        activeCajaSecuenciaId = activeCajaSecuenciaId,
                        branchName = branchName,
                        warehouseName = warehouseName,
                        sellers = sellers,
                        defaultSeller = defaultSeller,
                        paymentMethods = paymentMethods,
                    )
                }

                // 2. Departamentos en paralelo
                val departmentsDeferred = async {
                    productRepository.getDepartments().getOrElse { emptyList() }
                }

                // 3. Productos (Página 1: 12 ítems compactos) en paralelo
                val productsDeferred = async {
                    val paged = productRepository.getPagedProducts(page = 1, pageSize = CATALOG_PAGE_SIZE).getOrNull()
                    if (paged != null) {
                        paged
                    } else {
                        val fallback = productRepository.getAllProducts(page = 1, pageSize = CATALOG_PAGE_SIZE).getOrElse { emptyList() }
                        PagedProducts(items = fallback, totalCount = fallback.size, page = 1, pageSize = CATALOG_PAGE_SIZE)
                    }
                }

                // 4. Promociones en paralelo
                val promotionsDeferred = async {
                    promotionRepository?.getPromotions()?.getOrElse { emptyList() } ?: emptyList()
                }

                val cajaBundle = cajaAndPaymentsDeferred.await()
                val departments = departmentsDeferred.await()
                val pagedProducts = productsDeferred.await()
                val promotions = promotionsDeferred.await()

                val defaultClient = Client(id = "0", code = "CF", name = "CONSUMIDOR FINAL", identification = "CF")
                val totalPages = pagedProducts.totalPages.coerceAtLeast(1)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        products = pagedProducts.items,
                        filteredProducts = pagedProducts.items,
                        catalogCurrentPage = 1,
                        catalogTotalPages = totalPages,
                        isCatalogLoading = false,
                        departments = departments,
                        allPromotions = promotions,
                        activeCajaId = cajaBundle.cajaId,
                        activeCajaName = cajaBundle.activeCaja?.displayName ?: cajaRepository.activeCajaName.value,
                        activeCajaSecuenciaId = cajaBundle.activeCajaSecuenciaId,
                        sucursalNombre = cajaBundle.branchName,
                        almacenNombre = cajaBundle.warehouseName,
                        usuarioApertura = cajaBundle.usuarioApertura,
                        isCajaOpen = cajaBundle.isCajaOpen,
                        isCajaDiaAnterior = cajaBundle.isDiaAnterior,
                        showAvisoCajaAnterior = cajaBundle.isDiaAnterior,
                        cajaFechaApertura = cajaBundle.formattedFecha,
                        paymentMethods = cajaBundle.paymentMethods,
                        selectedPaymentMethod = cajaBundle.paymentMethods.firstOrNull(),
                        selectedClient = defaultClient,
                        availableSellers = cajaBundle.sellers,
                        selectedSeller = it.selectedSeller ?: cajaBundle.defaultSeller,
                    )
                }
            }
        }
    }

    fun onCatalogNextPage() {
        val current = _uiState.value.catalogCurrentPage
        val total = _uiState.value.catalogTotalPages
        if (current < total) {
            loadCatalogPage(current + 1)
        }
    }

    fun onCatalogPrevPage() {
        val current = _uiState.value.catalogCurrentPage
        if (current > 1) {
            loadCatalogPage(current - 1)
        }
    }

    fun onCatalogPageChange(page: Int) {
        val total = _uiState.value.catalogTotalPages
        if (page in 1..total) {
            loadCatalogPage(page)
        }
    }

    fun loadCatalogPage(
        page: Int,
        deptId: Int? = _uiState.value.selectedDepartmentId,
        query: String = _uiState.value.searchQuery,
    ) {
        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isCatalogLoading = true) }
            val result = productRepository.getPagedProducts(
                page = page,
                pageSize = CATALOG_PAGE_SIZE,
                departmentId = deptId,
                search = query.takeIf { it.isNotBlank() },
            ).getOrNull()

            if (result != null) {
                val filtered = filterProducts(result.items, query, deptId)
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        catalogCurrentPage = page,
                        catalogTotalPages = result.totalPages.coerceAtLeast(1),
                        products = if (query.isBlank() && deptId == null && page == 1) result.items else it.products,
                        filteredProducts = filtered,
                    )
                }
            } else {
                _uiState.update { it.copy(isCatalogLoading = false) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val filtered = filterProducts(state.products, query, state.selectedDepartmentId)
            state.copy(
                searchQuery = query,
                catalogCurrentPage = 1,
                filteredProducts = filtered,
            )
        }
        loadCatalogPage(1, deptId = _uiState.value.selectedDepartmentId, query = query)
    }

    fun onDepartmentSelected(deptId: Int?) {
        val newDeptId = if (_uiState.value.selectedDepartmentId == deptId) null else deptId
        _uiState.update { state ->
            val filtered = filterProducts(state.products, state.searchQuery, newDeptId)
            state.copy(
                selectedDepartmentId = newDeptId,
                catalogCurrentPage = 1,
                filteredProducts = filtered,
            )
        }
        loadCatalogPage(1, deptId = newDeptId, query = _uiState.value.searchQuery)
    }

    private fun matchesDepartment(product: Product, deptId: Int?, departments: List<DepartmentDto>): Boolean {
        if (deptId == null) return true
        val deptIdStr = deptId.toString()
        if (product.department == deptIdStr || product.department.toIntOrNull() == deptId) return true
        val dept = departments.firstOrNull { it.id == deptId } ?: return false
        return product.department.equals(dept.displayName, ignoreCase = true) ||
            product.department.equals(dept.name, ignoreCase = true)
    }

    private fun filterProducts(products: List<Product>, query: String, deptId: Int?): List<Product> {
        val q = query.trim().lowercase()
        val departments = _uiState.value.departments
        return products.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.description.lowercase().contains(q) ||
                p.code.lowercase().contains(q) ||
                p.barcode1.lowercase().contains(q) ||
                p.reference.lowercase().contains(q)
            val matchesDept = matchesDepartment(p, deptId, departments)
            matchesQuery && matchesDept
        }
    }

    fun getActivePromotionsFor(productId: String): List<com.amaxonia.erp.domain.model.Promocion> {
        return _uiState.value.allPromotions.filter { promo ->
            promo.activo && (promo.idItem == productId || promo.detalles.any { it.idItem == productId })
        }
    }

    fun addToCart(product: Product) {
        val promos = getActivePromotionsFor(product.id)
        if (promos.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    pendingPromotionProduct = product,
                    promotionOptions = promos,
                    showPromotionChoice = true,
                )
            }
        } else {
            addIndividualToCart(product, 1.0)
        }
    }

    fun openQuantityPicker(product: Product) {
        _uiState.update { it.copy(quantityPickerProduct = product) }
    }

    fun dismissQuantityPicker() {
        _uiState.update { it.copy(quantityPickerProduct = null) }
    }

    fun confirmProductQuantity(product: Product, quantity: Double) {
        dismissQuantityPicker()
        val promos = getActivePromotionsFor(product.id)
        if (promos.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    pendingPromotionProduct = product,
                    promotionOptions = promos,
                    showPromotionChoice = true,
                )
            }
        } else {
            addIndividualToCart(product, quantity.coerceAtLeast(1.0))
        }
    }

    fun dismissPromotionChoice() {
        _uiState.update {
            it.copy(
                showPromotionChoice = false,
                pendingPromotionProduct = null,
                promotionOptions = emptyList(),
            )
        }
    }

    fun addIndividualFromPromotionChoice(quantity: Double) {
        val product = _uiState.value.pendingPromotionProduct ?: return
        dismissPromotionChoice()
        addIndividualToCart(product, quantity.coerceAtLeast(1.0))
    }

    fun addPromotionToCart(promocion: com.amaxonia.erp.domain.model.Promocion, times: Int = 1) {
        val safeTimes = times.coerceAtLeast(1)
        _uiState.update { state ->
            val currentItems = state.cart
            if (currentItems.any { it.promocionId == promocion.id }) {
                val updated = updatePromotionLines(currentItems, promocion.id, safeTimes, append = true)
                return@update state.copy(
                    cart = updated,
                    showPromotionChoice = false,
                    pendingPromotionProduct = null,
                    promotionOptions = emptyList(),
                )
            }
            val promotionLines = promocion.detalles.map { detalle ->
                val baseQuantity = detalle.cantidadTotal.toDouble().takeIf { it > 0.0 }
                    ?: detalle.cantidad.toDouble().coerceAtLeast(1.0)
                val quantity = baseQuantity * safeTimes
                val unitPriceWithTax = if (quantity > 0.0) detalle.totalConIva.toDouble() / quantity else 0.0
                CartItem(
                    product = detalle.product.copy(
                        isExempt = detalle.iva.toDouble() <= 0.0,
                        taxRate = detalle.iva.toDouble(),
                    ),
                    quantity = quantity,
                    unitPriceWithTax = unitPriceWithTax,
                    discountPercent = detalle.descuento.toDouble(),
                    promocionId = promocion.id,
                    promocionCodigo = promocion.codigo,
                    promocionNombre = promocion.nombre,
                    promocionTipo = promocion.tipo,
                    promocionGrupo = detalle.grupo,
                    promocionDetalleId = detalle.id,
                    promocionVeces = safeTimes,
                )
            }
            state.copy(
                cart = currentItems + promotionLines,
                showPromotionChoice = false,
                pendingPromotionProduct = null,
                promotionOptions = emptyList(),
            )
        }
    }

    fun updatePromotionQuantity(promocionId: String, times: Int) {
        if (times <= 0) {
            removePromotion(promocionId)
            return
        }
        _uiState.update { state ->
            val updated = updatePromotionLines(state.cart, promocionId, times, append = false)
            state.copy(cart = updated)
        }
    }

    fun removePromotion(promocionId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.promocionId == promocionId })
        }
    }

    private fun updatePromotionLines(
        items: List<CartItem>,
        promotionId: String,
        times: Int,
        append: Boolean,
    ): List<CartItem> {
        val safeTimes = times.coerceAtLeast(1)
        return items.map { item ->
            if (item.promocionId != promotionId) return@map item
            val currentTimes = item.promocionVeces.coerceAtLeast(1)
            val nextTimes = if (append) currentTimes + safeTimes else safeTimes
            val baseQuantity = item.quantity / currentTimes
            val nextQuantity = baseQuantity * nextTimes
            val unitPriceWithTax = if (nextQuantity > 0.0) {
                (item.unitPriceWithTax * item.quantity) / nextQuantity
            } else item.unitPriceWithTax
            item.copy(
                quantity = nextQuantity,
                promocionVeces = nextTimes,
                unitPriceWithTax = unitPriceWithTax,
            )
        }
    }

    fun addIndividualToCart(product: Product, quantity: Double = 1.0) {
        _uiState.update { state ->
            val existingIndex = state.cart.indexOfFirst { it.product.id == product.id && !it.isPromotionLine }
            val updatedCart = state.cart.toMutableList()
            if (existingIndex >= 0) {
                val current = updatedCart[existingIndex]
                updatedCart[existingIndex] = current.copy(quantity = current.quantity + quantity)
            } else {
                updatedCart.add(CartItem(product = product, quantity = quantity))
            }
            state.copy(cart = updatedCart)
        }
    }

    fun incrementQuantity(productId: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(quantity = item.quantity + 1.0)
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun decrementQuantity(productId: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.mapNotNull { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    val newQty = item.quantity - 1.0
                    if (newQty > 0) item.copy(quantity = newQty) else null
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun setItemQuantity(productId: String, quantity: Double) {
        if (quantity <= 0.0) {
            removeFromCart(productId)
            return
        }
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(quantity = quantity)
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun updateItemPrice(productId: String, newPriceWithTax: Double) {
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(
                        unitPriceWithTax = newPriceWithTax.coerceAtLeast(0.0),
                        isManualPrice = true,
                    )
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun updateItemPriceLevel(productId: String, priceLevelLabel: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    val level = item.product.prices.firstOrNull { it.label.equals(priceLevelLabel, ignoreCase = true) }
                    val newPrice = level?.pricePlusTax?.takeIf { it > 0.0 } ?: level?.price ?: item.unitPriceWithTax
                    item.copy(
                        unitPriceWithTax = newPrice,
                        selectedPriceLabel = priceLevelLabel,
                        isManualPrice = false,
                    )
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun updateItemDiscount(productId: String, discountPercent: Double) {
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(discountPercent = discountPercent.coerceIn(0.0, 100.0))
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId && !it.isPromotionLine })
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun selectClient(client: Client) {
        _uiState.update {
            it.copy(
                selectedClient = client,
                showClientDialog = false,
                isLoadingBranches = true,
                branchSelectionRequiredError = false,
                selectedClientBranch = null,
                clientBranches = emptyList(),
            )
        }
        if (client.code == "CF" || client.id == "0" || client.id.isBlank()) {
            _uiState.update { it.copy(isLoadingBranches = false) }
            return
        }
        viewModelScope.launch {
            val branches = clientRepository.getClientSucursales(client.id).getOrElse { emptyList() }
            _uiState.update { current ->
                current.copy(
                    clientBranches = branches,
                    selectedClientBranch = if (branches.size == 1) branches.first() else null,
                    isLoadingBranches = false,
                )
            }
        }
    }

    fun selectClientBranch(branch: ClientBranch) {
        _uiState.update {
            it.copy(
                selectedClientBranch = branch,
                branchSelectionRequiredError = false,
            )
        }
    }

    fun removeSelectedClient() {
        selectClient(Client(id = "0", code = "CF", name = "CONSUMIDOR FINAL", identification = "CF"))
    }

    suspend fun searchClients(query: String): List<Client> {
        return if (query.isBlank()) {
            clientRepository.getAllClients(page = 1, pageSize = 30).getOrElse { emptyList() }
        } else {
            clientRepository.searchClients(query = query, page = 1, pageSize = 30).getOrElse { emptyList() }
        }
    }

    fun openClientDialog() {
        _uiState.update { it.copy(showClientDialog = true) }
    }

    fun dismissClientDialog() {
        _uiState.update { it.copy(showClientDialog = false) }
    }

    fun openProductDialog(initialQuery: String = "") {
        _uiState.update { it.copy(showProductDialog = true, productDialogQuery = initialQuery) }
    }

    fun dismissProductDialog() {
        _uiState.update { it.copy(showProductDialog = false, productDialogQuery = "") }
    }

    fun selectSeller(seller: SellerSummary) {
        _uiState.update { it.copy(selectedSeller = seller, showSellerSheet = false) }
    }

    fun openSellerSheet() {
        _uiState.update { it.copy(showSellerSheet = true) }
    }

    fun dismissSellerSheet() {
        _uiState.update { it.copy(showSellerSheet = false) }
    }

    fun searchSellerByCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val codeInt = trimmed.toIntOrNull()
        val found = _uiState.value.availableSellers.firstOrNull {
            it.id == codeInt || it.id.toString().padStart(3, '0') == trimmed || it.nombre.contains(trimmed, ignoreCase = true)
        }
        if (found != null) {
            _uiState.update { it.copy(selectedSeller = found, errorMessage = null) }
        } else {
            _uiState.update { it.copy(errorMessage = "Vendedor '$trimmed' no encontrado") }
        }
    }

    fun setCustomClientName(name: String) {
        _uiState.update { it.copy(customClientName = name.trim(), showCustomClientNameDialog = false) }
    }

    fun openCustomClientNameDialog() {
        _uiState.update { it.copy(showCustomClientNameDialog = true) }
    }

    fun dismissCustomClientNameDialog() {
        _uiState.update { it.copy(showCustomClientNameDialog = false) }
    }

    fun setGlobalDiscount(percent: Double) {
        _uiState.update { it.copy(globalDiscountPercent = percent.coerceIn(0.0, 100.0), showGlobalDiscountDialog = false) }
    }

    fun openGlobalDiscountDialog() {
        _uiState.update { it.copy(showGlobalDiscountDialog = true) }
    }

    fun dismissGlobalDiscountDialog() {
        _uiState.update { it.copy(showGlobalDiscountDialog = false) }
    }

    fun setPaymentMethodAmount(methodId: Int, amount: Double) {
        val formatted = if (amount > 0.0) String.format(Locale.US, "%.2f", amount) else ""
        onPaymentMethodTextChange(methodId, formatted)
    }

    fun setActivePaymentInputMethod(methodId: Int) {
        val method = _uiState.value.paymentMethods.firstOrNull { it.idFormaPago == methodId }
        _uiState.update {
            it.copy(
                activePaymentInputMethodId = methodId,
                selectedPaymentMethod = method ?: it.selectedPaymentMethod,
            )
        }
    }

    fun onPaymentMethodBadgeClick(method: FormaPagoDto) {
        val state = _uiState.value
        val total = state.summary.total
        val currentAmount = state.paymentsMap[method.idFormaPago] ?: 0.0

        val otherSum = state.paymentsMap.filter { it.key != method.idFormaPago }.values.sum()
        val difference = (total - otherSum).coerceAtLeast(0.0)

        val updatedMap = state.paymentsMap.toMutableMap()
        val updatedTexts = state.paymentInputTexts.toMutableMap()

        if (currentAmount <= 0.0 && difference > 0.0) {
            updatedMap[method.idFormaPago] = difference
            val formatted = String.format(Locale.US, "%.2f", difference)
            updatedTexts[method.idFormaPago] = formatted
            if (isCashPaymentMethod(method)) {
                _uiState.update { it.copy(receivedAmountText = formatted) }
            }
        }

        _uiState.update {
            it.copy(
                selectedPaymentMethod = method,
                activePaymentInputMethodId = method.idFormaPago,
                paymentsMap = updatedMap,
                paymentInputTexts = updatedTexts,
            )
        }
    }

    fun onPaymentMethodTextChange(methodId: Int, newText: String) {
        val filtered = newText.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return

        val state = _uiState.value
        val updatedTexts = state.paymentInputTexts.toMutableMap()
        val updatedMap = state.paymentsMap.toMutableMap()

        if (filtered.isBlank()) {
            updatedTexts.remove(methodId)
            updatedMap.remove(methodId)
        } else {
            updatedTexts[methodId] = filtered
            val parsed = filtered.toDoubleOrNull() ?: 0.0
            if (parsed > 0.0) {
                updatedMap[methodId] = parsed
            } else {
                updatedMap.remove(methodId)
            }
        }

        val method = state.paymentMethods.firstOrNull { it.idFormaPago == methodId }
        val isCash = isCashPaymentMethod(method)

        _uiState.update { current ->
            current.copy(
                paymentInputTexts = updatedTexts,
                paymentsMap = updatedMap,
                activePaymentInputMethodId = methodId,
                selectedPaymentMethod = method ?: current.selectedPaymentMethod,
                receivedAmountText = if (isCash) filtered else current.receivedAmountText,
            )
        }
    }

    fun onClearSinglePaymentMethod(methodId: Int) {
        val state = _uiState.value
        val updatedTexts = state.paymentInputTexts.toMutableMap()
        val updatedMap = state.paymentsMap.toMutableMap()
        updatedTexts.remove(methodId)
        updatedMap.remove(methodId)

        val method = state.paymentMethods.firstOrNull { it.idFormaPago == methodId }
        val isCash = isCashPaymentMethod(method)

        _uiState.update {
            it.copy(
                paymentInputTexts = updatedTexts,
                paymentsMap = updatedMap,
                receivedAmountText = if (isCash) "" else it.receivedAmountText,
            )
        }
    }

    fun clearPayments() {
        _uiState.update {
            it.copy(
                paymentsMap = emptyMap(),
                paymentInputTexts = emptyMap(),
                receivedAmountText = "",
            )
        }
    }

    fun onKeypadInput(key: String) {
        val state = _uiState.value
        val targetId = state.activePaymentInputMethodId
            ?: state.paymentMethods.firstOrNull { isCashPaymentMethod(it) }?.idFormaPago
            ?: state.paymentMethods.firstOrNull()?.idFormaPago
            ?: return

        val currentText = state.paymentInputTexts[targetId].orEmpty()
        val total = state.summary.total

        val newText = when (key) {
            "C", "Clear" -> ""
            "Backspace", "⌫" -> if (currentText.isNotEmpty()) currentText.dropLast(1) else ""
            "." -> {
                if (!currentText.contains('.')) {
                    if (currentText.isEmpty()) "0." else "$currentText."
                } else currentText
            }
            "Saldo", "Exacto" -> {
                val otherSum = state.paymentsMap.filter { it.key != targetId }.values.sum()
                val diff = (total - otherSum).coerceAtLeast(0.0)
                if (diff > 0.0) String.format(Locale.US, "%.2f", diff) else ""
            }
            "Enter", "↵" -> currentText
            else -> {
                if (key.length == 1 && key[0].isDigit()) {
                    if (currentText == "0") key
                    else if (currentText.contains('.') && currentText.substringAfter('.').length >= 2) currentText
                    else currentText + key
                } else currentText
            }
        }
        onPaymentMethodTextChange(targetId, newText)
    }

    fun toggleCashDenominations() {
        _uiState.update { it.copy(expandedCashDenominations = !it.expandedCashDenominations) }
    }

    fun onAddCashDenomination(billValue: Double) {
        val state = _uiState.value
        val cashMethod = state.paymentMethods.firstOrNull { isCashPaymentMethod(it) } ?: return
        val currentCash = state.paymentsMap[cashMethod.idFormaPago] ?: 0.0
        val newCash = currentCash + billValue
        val formatted = String.format(Locale.US, "%.2f", newCash)
        onPaymentMethodTextChange(cashMethod.idFormaPago, formatted)
    }

    fun openPaymentDialog() {
        val state = _uiState.value
        if (state.cart.isEmpty()) return

        if (state.activeCajaId == null || !state.isCajaOpen) {
            _uiState.update { it.copy(showCajaWarningDialog = true) }
            return
        }

        if (state.clientBranches.size > 1 && state.selectedClientBranch == null) {
            _uiState.update {
                it.copy(
                    branchSelectionRequiredError = true,
                    errorMessage = "Por favor selecciona la sucursal del cliente antes de continuar al cobro",
                )
            }
            return
        }

        val total = state.summary.total
        val defaultMethod = state.paymentMethods.firstOrNull { isCashPaymentMethod(it) }
            ?: state.paymentMethods.firstOrNull()
        val defaultId = defaultMethod?.idFormaPago
        val formattedTotal = if (total > 0.0) String.format(Locale.US, "%.2f", total) else "0.00"

        val initialPayments = if (defaultId != null && total > 0.0) {
            mapOf(defaultId to total)
        } else emptyMap()

        val initialTexts = if (defaultId != null && total > 0.0) {
            mapOf(defaultId to formattedTotal)
        } else emptyMap()

        _uiState.update {
            it.copy(
                showPaymentDialog = true,
                selectedPaymentMethod = defaultMethod,
                activePaymentInputMethodId = defaultId,
                paymentsMap = initialPayments,
                paymentInputTexts = initialTexts,
                receivedAmountText = formattedTotal,
                expandedCashDenominations = false,
                errorMessage = null,
            )
        }
    }

    fun dismissPaymentDialog() {
        _uiState.update { it.copy(showPaymentDialog = false, errorMessage = null) }
    }

    fun dismissCajaWarning() {
        _uiState.update { it.copy(showCajaWarningDialog = false) }
    }

    fun dismissAvisoCajaAnterior() {
        _uiState.update { it.copy(showAvisoCajaAnterior = false) }
    }

    fun renovarCajaDiaAnterior() {
        val cajaId = _uiState.value.activeCajaId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRenovandoCaja = true, showAvisoCajaAnterior = false, errorMessage = null) }
            val nextSequence = cajaRepository.getNextSecuenciaCodigo(cajaId).getOrNull()
            val activeCajaObj = cajaRepository.activeCaja.value
            val request = AperturaRequest(
                idCaja = cajaId,
                montoApertura = 0.0,
                secuencia = nextSequence,
                serieSucursal = activeCajaObj?.serieSucursal ?: activeCajaObj?.serieCaja ?: "",
                idSucursal = activeCajaObj?.idSucursal,
            )
            cajaRepository.openCaja(request).fold(
                onSuccess = { res ->
                    val rawFecha = res.cajaSecuencia?.fechaApertura
                    val formatted = rawFecha?.let(CajaDateParser::formatDisplayDate)
                    _uiState.update {
                        it.copy(
                            isRenovandoCaja = false,
                            isCajaOpen = res.isOpen,
                            cajaFechaApertura = formatted,
                            usuarioApertura = res.cajaSecuencia?.usuarioApertura,
                            isCajaDiaAnterior = false,
                            showAvisoCajaAnterior = false,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRenovandoCaja = false,
                            errorMessage = "Error al renovar caja: ${err.message}",
                        )
                    }
                },
            )
        }
    }

    private suspend fun findFirstOpenCaja(cajas: List<com.amaxonia.erp.domain.model.Caja>): com.amaxonia.erp.domain.model.Caja? =
        coroutineScope {
            cajas.map { caja ->
                async {
                    val isOpen = cajaRepository.checkCajaStatus(caja.idCaja).getOrNull()?.isOpen == true
                    if (isOpen) caja else null
                }
            }.mapNotNull { it.await() }.firstOrNull()
        }

    fun dismissSuccessDialog() {
        _uiState.update {
            it.copy(
                completedSaleInvoice = null,
                completedSaleInfo = null,
                printFeedbackMessage = null,
                isPrintingReceipt = false,
            )
        }
    }

    fun printReceipt(facturaId: String? = null) {
        val targetId = facturaId
            ?: _uiState.value.completedSaleInfo?.facturaId
            ?: _uiState.value.completedSaleInvoice
            ?: return

        if (_uiState.value.isPrintingReceipt) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPrintingReceipt = true, printFeedbackMessage = null) }
            val session = localStore.readCompanySession()
            val countryCode = session?.company?.countryCode ?: "PA"

            val feedback = printGateway?.print(countryCode = countryCode, facturaId = targetId)
            _uiState.update {
                it.copy(
                    isPrintingReceipt = false,
                    printFeedbackMessage = feedback?.displayMessage ?: "Impresión finalizada",
                    isPrintSuccess = feedback?.isSuccess ?: true,
                )
            }
        }
    }


    fun onReceivedAmountChange(amount: String) {
        _uiState.update { it.copy(receivedAmountText = amount) }
    }

    fun selectPaymentMethod(method: FormaPagoDto) {
        val isCash = isCashPaymentMethod(method)
        val currentTotal = _uiState.value.summary.total
        _uiState.update { current ->
            current.copy(
                selectedPaymentMethod = method,
                receivedAmountText = if (!isCash && currentTotal > 0.0) {
                    String.format(Locale.US, "%.2f", currentTotal)
                } else {
                    current.receivedAmountText.ifBlank {
                        if (currentTotal > 0.0) String.format(Locale.US, "%.2f", currentTotal) else "0.00"
                    }
                },
            )
        }
    }

    fun setExactAmount() {
        val total = _uiState.value.summary.total
        if (total > 0.0) {
            _uiState.update { it.copy(receivedAmountText = String.format(Locale.US, "%.2f", total)) }
        }
    }

    fun processSale() {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessingSale) return

        if (state.clientBranches.size > 1 && state.selectedClientBranch == null) {
            _uiState.update {
                it.copy(
                    branchSelectionRequiredError = true,
                    errorMessage = "Por favor selecciona la sucursal del cliente antes de cobrar",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingSale = true, errorMessage = null) }

            val session = localStore.readCompanySession()
            val sucursal = localStore.readActiveSucursal()
            val sucursalId = sucursal?.first?.toIntOrNull() ?: 1
            val summary = state.summary
            val cashMethod = state.paymentMethods.firstOrNull { isCashPaymentMethod(it) }
            val cashMethodId = cashMethod?.idFormaPago
            val cashEntered = if (cashMethodId != null) (state.paymentsMap[cashMethodId] ?: 0.0) else 0.0
            val nonCashSum = state.paymentsMap.filter { it.key != cashMethodId }.values.sum()
            val cashNeeded = (summary.total - nonCashSum).coerceAtLeast(0.0)
            val cashChange = if (cashEntered > cashNeeded) (cashEntered - cashNeeded) else 0.0
            val actualCashPaid = if (cashEntered > cashNeeded) cashNeeded else cashEntered
            val totalReceived = if (state.paymentsMap.isNotEmpty()) (nonCashSum + cashEntered) else (state.receivedAmountText.toDoubleOrNull() ?: summary.total)
            val change = if (state.paymentsMap.isNotEmpty()) cashChange else (totalReceived - summary.total).coerceAtLeast(0.0)

            val invoiceClientName = if (state.selectedClient?.code == "CF" && state.customClientName.isNotBlank()) {
                state.customClientName
            } else {
                state.selectedClient?.name ?: "CONSUMIDOR FINAL"
            }

            val primaryPaymentMethod = state.selectedPaymentMethod ?: state.paymentMethods.firstOrNull()
            val isSplitPayment = state.paymentsMap.size > 1

            val invoice = SaleInvoiceDto(
                idCliente = state.selectedClient?.id ?: "0",
                codCliente = state.selectedClient?.code ?: "CF",
                codVendedor = state.selectedSeller?.id ?: 1,
                idSucursal = sucursalId,
                idCaja = state.activeCajaId ?: "1",
                codigoCaja = state.activeCajaName ?: "01",
                idCajaSecuencia = state.activeCajaSecuenciaId ?: "0",
                subtotal = summary.subtotal,
                descuentosItemFactura = summary.itemDiscounts,
                ivaTotalFactura = summary.tax,
                totalTotalFactura = summary.total,
                montoItemsFactura = summary.subtotal,
                totalizarSubTotal = summary.subtotal,
                totalizarPDescuentoGlobal = state.globalDiscountPercent,
                totalizarDescuentoGlobal = summary.globalDiscountAmount,
                totalizarBaseImponible = summary.subtotal,
                totalizarMontoIva = summary.tax,
                totalizarTotalGeneral = summary.total,
                usuarioCreacion = session?.user?.username ?: "admin",
                facturarA = invoiceClientName,
                facturarARuc = state.selectedClient?.identification ?: "CF",
                facturarADireccion = state.selectedClientBranch?.direccion?.takeIf(String::isNotBlank) ?: state.selectedClient?.address ?: "",
                facturarATelefono = state.selectedClientBranch?.telefonoContacto?.takeIf(String::isNotBlank) ?: state.selectedClient?.phone ?: "",
                clienteSucursalId = state.selectedClientBranch?.sucursalId,
                formaPago = primaryPaymentMethod?.codigo ?: "EFECTIVO",
                observacion = state.observationText,
            )

            val saleItems = state.cart.map { item ->
                SaleItemDto(
                    idItem = item.product.id.toIntOrNull() ?: 1,
                    codVendedor = state.selectedSeller?.id ?: 1,
                    itemAlmacen = 1,
                    itemDescripcion = item.product.description,
                    itemCantidad = item.quantity,
                    itemPrecioSinIva = item.unitPriceWithoutTax,
                    itemDescuento = item.discountPercent,
                    itemMontoDescuento = item.discountAmountWithoutTax,
                    itemPIva = item.taxRate,
                    itemTotalSinIva = item.totalWithoutTax,
                    itemTotalConIva = item.totalWithTax,
                    itemCantidadTotal = item.quantity,
                    itemCodigo = item.product.code,
                    itemReferencia = item.product.reference,
                    esProductoFisico = true,
                    promocionTipo = item.promocionTipo,
                    promocionId = item.promocionId.orEmpty(),
                    promocionCantidad = if (item.isPromotionLine) item.quantity else 0.0,
                    promocionCodigo = item.promocionCodigo,
                    promocionNombre = item.promocionNombre,
                )
            }

            val payments = if (state.paymentsMap.isNotEmpty()) {
                state.paymentsMap.mapNotNull { (methodId, amount) ->
                    val method = state.paymentMethods.firstOrNull { it.idFormaPago == methodId }
                    val isCash = method != null && isCashPaymentMethod(method)
                    val paid = if (isCash) actualCashPaid else amount
                    val rec = if (isCash) cashEntered else amount
                    val chg = if (isCash) cashChange else 0.0
                    if (paid > 0.0 || rec > 0.0) {
                        SalePaymentDto(
                            idFormaPago = methodId,
                            monto = paid,
                            montoRecibido = rec,
                            efectivoCambio = chg,
                            siglas = method?.siglas,
                        )
                    } else null
                }
            } else {
                listOf(
                    SalePaymentDto(
                        idFormaPago = primaryPaymentMethod?.idFormaPago ?: 1,
                        monto = summary.total,
                        montoRecibido = totalReceived.coerceAtLeast(summary.total),
                        efectivoCambio = change,
                        siglas = primaryPaymentMethod?.siglas,
                    )
                )
            }

            val montosPorTipo = if (state.paymentsMap.isNotEmpty()) {
                state.paymentsMap.mapNotNull { (methodId, amount) ->
                    val method = state.paymentMethods.firstOrNull { it.idFormaPago == methodId }
                    val isCash = method != null && isCashPaymentMethod(method)
                    val code = method?.codigo ?: method?.descripcion ?: "PAGO_$methodId"
                    val paid = if (isCash) actualCashPaid else amount
                    code to paid
                }.toMap()
            } else {
                mapOf((primaryPaymentMethod?.codigo ?: "EFECTIVO") to summary.total)
            }

            val paymentSummary = SalePaymentSummaryDto(
                totalizarMontoCancelar = summary.total,
                totalizarMontoEfectivo = actualCashPaid,
                totalizarCambio = change,
                totalizarSaldoPendiente = 0.0,
                montosPorTipo = montosPorTipo,
            )

            val request = ProcessSaleRequestDto(
                factura = invoice,
                items = saleItems,
                pagoResumen = paymentSummary,
                pagos = payments,
            )

            val isOnline = networkMonitor?.isOnline() ?: true
            val tenantId = session?.company?.id?.toString() ?: ""
            val countryCode = session?.company?.countryCode.takeIf { !it.isNullOrBlank() }
                ?: runCatching { localStore.readSelectedCountry()?.code }.getOrNull()
                ?: BuildConfig.DEFAULT_COUNTRY_CODE

            val paymentMethodDescription = if (isSplitPayment && state.paymentsMap.size > 1) {
                "Mixto (${state.paymentsMap.size} formas)"
            } else {
                primaryPaymentMethod?.descripcion ?: primaryPaymentMethod?.codigo ?: "Efectivo"
            }

            // Ruta offline directa si no hay conexión a internet
            if (!isOnline && pendingInvoiceDao != null) {
                val now = System.currentTimeMillis()
                val localId = UUID.randomUUID().toString()
                val localNumber = "OFF-$now"
                val offlineRequest = request.copy(idFactura = localId, codFactura = localNumber)
                val total = summary.total

                val pendingEntity = PendingInvoiceEntity(
                    id = localId,
                    countryCode = countryCode,
                    payloadJson = AppJson.encodeToString(
                        ProcessSaleRequestDto.serializer(),
                        offlineRequest,
                    ),
                    localInvoiceNumber = localNumber,
                    clientName = invoiceClientName,
                    tenantId = tenantId,
                    total = total,
                    createdAt = now,
                    updatedAt = now,
                )
                pendingInvoiceDao.insert(pendingEntity)
                onOfflineInvoiceQueued?.invoke()

                val completedInfo = CompletedSaleInfo(
                    facturaId = localId,
                    numeroFactura = localNumber,
                    clientName = invoiceClientName,
                    total = total,
                    receivedAmount = totalReceived,
                    changeAmount = change,
                    paymentMethodName = paymentMethodDescription,
                )
                _uiState.update {
                    it.copy(
                        isProcessingSale = false,
                        showPaymentDialog = false,
                        cart = emptyList(),
                        paymentsMap = emptyMap(),
                        paymentInputTexts = emptyMap(),
                        activePaymentInputMethodId = null,
                        expandedCashDenominations = false,
                        receivedAmountText = "",
                        customClientName = "",
                        observationText = "",
                        globalDiscountPercent = 0.0,
                        completedSaleInvoice = localNumber,
                        completedSaleInfo = completedInfo,
                        printFeedbackMessage = null,
                        isPrintingReceipt = false,
                    )
                }
                customerDisplayManager?.showSaleSuccess(completedInfo)
                return@launch
            }

            val result = salesRepository.processSale(request)

            result.onSuccess { response ->
                val invoiceNumber = response.codFactura.ifBlank { response.idFactura }.ifBlank { "PROCESADA" }
                val targetFacturaId = response.idFactura.ifBlank { response.codFactura }
                val completedInfo = CompletedSaleInfo(
                    facturaId = targetFacturaId,
                    numeroFactura = invoiceNumber,
                    clientName = invoiceClientName,
                    total = summary.total,
                    receivedAmount = totalReceived,
                    changeAmount = change,
                    paymentMethodName = paymentMethodDescription,
                )
                _uiState.update {
                    it.copy(
                        isProcessingSale = false,
                        showPaymentDialog = false,
                        cart = emptyList(),
                        paymentsMap = emptyMap(),
                        paymentInputTexts = emptyMap(),
                        activePaymentInputMethodId = null,
                        expandedCashDenominations = false,
                        receivedAmountText = "",
                        customClientName = "",
                        observationText = "",
                        globalDiscountPercent = 0.0,
                        completedSaleInvoice = invoiceNumber,
                        completedSaleInfo = completedInfo,
                        printFeedbackMessage = null,
                        isPrintingReceipt = false,
                    )
                }

                customerDisplayManager?.showSaleSuccess(completedInfo)

                if (localStore.readAutoPrintReceipt()) {
                    printReceipt(targetFacturaId)
                }
            }.onFailure { ex ->
                val msg = ex.message.orEmpty()
                val isNetworkError = !isOnline ||
                    ex is java.io.IOException ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("failed to connect", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true)

                if (isNetworkError && pendingInvoiceDao != null) {
                    val now = System.currentTimeMillis()
                    val localId = UUID.randomUUID().toString()
                    val localNumber = "OFF-$now"
                    val offlineRequest = request.copy(idFactura = localId, codFactura = localNumber)
                    val total = summary.total

                    val pendingEntity = PendingInvoiceEntity(
                        id = localId,
                        countryCode = countryCode,
                        payloadJson = AppJson.encodeToString(
                            ProcessSaleRequestDto.serializer(),
                            offlineRequest,
                        ),
                        localInvoiceNumber = localNumber,
                        clientName = invoiceClientName,
                        tenantId = tenantId,
                        total = total,
                        createdAt = now,
                        updatedAt = now,
                    )
                    pendingInvoiceDao.insert(pendingEntity)
                    onOfflineInvoiceQueued?.invoke()

                    val completedInfo = CompletedSaleInfo(
                        facturaId = localId,
                        numeroFactura = localNumber,
                        clientName = invoiceClientName,
                        total = total,
                        receivedAmount = totalReceived,
                        changeAmount = change,
                        paymentMethodName = paymentMethodDescription,
                    )
                    _uiState.update {
                        it.copy(
                            isProcessingSale = false,
                            showPaymentDialog = false,
                            cart = emptyList(),
                            paymentsMap = emptyMap(),
                            paymentInputTexts = emptyMap(),
                            activePaymentInputMethodId = null,
                            expandedCashDenominations = false,
                            receivedAmountText = "",
                            customClientName = "",
                            observationText = "",
                            globalDiscountPercent = 0.0,
                            completedSaleInvoice = localNumber,
                            completedSaleInfo = completedInfo,
                            printFeedbackMessage = null,
                            isPrintingReceipt = false,
                        )
                    }
                    customerDisplayManager?.showSaleSuccess(completedInfo)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessingSale = false,
                            errorMessage = ex.message ?: "Error al procesar la venta",
                        )
                    }
                }
            }
        }
    }

    fun toggleCatalogDrawer() {
        _uiState.update { it.copy(isCatalogDrawerOpen = !it.isCatalogDrawerOpen) }
    }

    fun setCatalogDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isCatalogDrawerOpen = isOpen) }
    }

    fun onObservationChange(text: String) {
        _uiState.update { it.copy(observationText = text) }
    }

    fun onDocumentTypeSelected(type: String) {
        _uiState.update { it.copy(activeDocumentType = type) }
    }

    fun onGlobalDiscountChange(percent: Double) {
        _uiState.update { it.copy(globalDiscountPercent = percent.coerceIn(0.0, 100.0)) }
    }

    fun setShowPrintOptions(show: Boolean) {
        _uiState.update { it.copy(showPrintOptionsDialog = show) }
    }

    fun addProductByCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            openProductDialog("")
            return true
        }
        val product = _uiState.value.products.firstOrNull { p ->
            p.code.equals(trimmed, ignoreCase = true) ||
            p.barcode1.equals(trimmed, ignoreCase = true) ||
            p.reference.equals(trimmed, ignoreCase = true)
        }
        if (product != null) {
            addToCart(product)
            _uiState.update { it.copy(errorMessage = null) }
            return true
        }

        viewModelScope.launch {
            val results = productRepository.searchProducts(query = trimmed, page = 1, pageSize = 10).getOrElse { emptyList() }
            val match = results.firstOrNull { p ->
                p.code.equals(trimmed, ignoreCase = true) ||
                p.barcode1.equals(trimmed, ignoreCase = true) ||
                p.reference.equals(trimmed, ignoreCase = true)
            }
            if (match != null) {
                addToCart(match)
                _uiState.update { it.copy(errorMessage = null) }
            } else if (results.size == 1) {
                addToCart(results.first())
                _uiState.update { it.copy(errorMessage = null) }
            } else {
                openProductDialog(trimmed)
            }
        }
        return true
    }
}

private data class CajaInitBundle(
    val activeCaja: com.amaxonia.erp.domain.model.Caja?,
    val cajaId: String?,
    val isCajaOpen: Boolean,
    val isDiaAnterior: Boolean,
    val formattedFecha: String?,
    val usuarioApertura: String?,
    val activeCajaSecuenciaId: String?,
    val branchName: String,
    val warehouseName: String,
    val sellers: List<SellerSummary>,
    val defaultSeller: SellerSummary,
    val paymentMethods: List<FormaPagoDto>,
)
