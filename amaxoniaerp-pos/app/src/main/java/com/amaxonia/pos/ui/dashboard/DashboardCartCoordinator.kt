package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.PromotionRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.usecase.ValidarAdicionPromocionUseCase
import com.amaxonia.pos.domain.usecase.cart.LotRefreshPolicy
import com.amaxonia.pos.domain.usecase.cart.RefreshCartProductLotsUseCase
import com.amaxonia.pos.domain.usecase.cart.ResolveClientBranchesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardCartCoordinator(
    private val promotionRepository: PromotionRepository,
    private val cartRepository: CartRepository,
    private val resolveClientBranches: ResolveClientBranchesUseCase,
    private val refreshProductLots: RefreshCartProductLotsUseCase,
    saleGate: DashboardSaleGate,
    clock: AppClock,
) {
    private val productHandler =
        DashboardProductActionHandler(
            promotionRepository,
            cartRepository,
            refreshProductLots,
            saleGate,
        )
    private val manualHandler = DashboardManualEntryHandler(productHandler, clock)

    fun start(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        scope.launch { promotionRepository.syncPromotions() }
        scope.launch {
            cartRepository.cartItems.collect { items ->
                state.update {
                    it.copy(
                        cartItems = items,
                        cartItemCount = items.sumOf { item -> item.quantity },
                        cartTotal = items.sumOf { item -> item.total },
                    )
                }
            }
        }
        scope.launch {
            cartRepository.selectedClient.collect { client ->
                if (client == null) {
                    cartRepository.setClientSucursales(emptyList())
                    state.update {
                        it.copy(
                            selectedClient = null,
                            clientSucursales = emptyList(),
                            selectedClientSucursal = null,
                        )
                    }
                } else {
                    val branches = resolveClientBranches(client)
                    cartRepository.setClientSucursales(branches)
                    state.update { it.copy(selectedClient = client) }
                }
            }
        }
        scope.launch {
            cartRepository.clientSucursales.collect { branches ->
                state.update { it.copy(clientSucursales = branches) }
            }
        }
        scope.launch {
            cartRepository.selectedClientSucursal.collect { branch ->
                state.update { it.copy(selectedClientSucursal = branch) }
            }
        }
        scope.launch {
            cartRepository.currentSeller.collect { seller ->
                state.update { it.copy(currentSeller = seller) }
            }
        }
        scope.launch {
            cartRepository.availableSellers.collect { sellers ->
                state.update { it.copy(availableSellers = sellers) }
            }
        }
    }

    fun onAction(
        action: DashboardSaleUiAction,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardSaleUiAction.Product -> productHandler.onAction(action, scope, state)
            is DashboardSaleUiAction.Manual -> manualHandler.onAction(action, scope, state)
            is DashboardSaleUiAction.Session -> handleSessionAction(action)
            DashboardSaleUiAction.Checkout -> Unit
        }
    }

    private fun handleSessionAction(action: DashboardSaleUiAction.Session) {
        when (action) {
            DashboardSaleUiAction.StartNewOrder -> cartRepository.clearCart()
            is DashboardSaleUiAction.SelectSeller -> {
                val seller = cartRepository.availableSellers.value.firstOrNull { it.id == action.sellerId } ?: return
                cartRepository.setCurrentSeller(seller)
            }
        }
    }
}

private class DashboardProductActionHandler(
    private val promotionRepository: PromotionRepository,
    private val cartRepository: CartRepository,
    private val refreshProductLots: RefreshCartProductLotsUseCase,
    private val saleGate: DashboardSaleGate,
) {
    private val validatePromotion = ValidarAdicionPromocionUseCase()

    fun onAction(
        action: DashboardSaleUiAction.Product,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardSaleUiAction.AddProduct -> addToCart(action.product, action.quantity, scope, state)
            is DashboardSaleUiAction.ShowQuantityPicker -> showQuantityPicker(action.product, scope, state)
            DashboardSaleUiAction.DismissQuantityPicker -> state.update { it.copy(quantityPickerProduct = null) }
            is DashboardSaleUiAction.ConfirmProductQuantity -> confirmQuantity(action, scope, state)
            is DashboardSaleUiAction.AddProductIndividualFromPromotionChoice -> addIndividualChoice(action.quantity, scope, state)
            is DashboardSaleUiAction.AddPromotionFromChoice -> addPromotionChoice(action.promotion, action.times, state)
            DashboardSaleUiAction.DismissPromotionChoice -> dismissPromotionChoice(state)
            DashboardSaleUiAction.ClearPromotionMessage -> state.update { it.copy(promotionMessage = null) }
        }
    }

    fun addToCart(
        product: DashboardProduct,
        quantity: Int,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        if (!saleGate.canProceed(scope, state)) return
        val safeQuantity = quantity.coerceAtLeast(1)
        scope.launch {
            promotionRepository.getActivePromotionsForProduct(product.id).fold(
                onSuccess = { promotions ->
                    val validPromotions =
                        promotions.filter { promotion ->
                            val cartWithoutSamePromotion =
                                cartRepository.cartItems.value.filter { it.promocionId != promotion.id }
                            validatePromotion(promotion, cartWithoutSamePromotion).isSuccess
                        }
                    if (validPromotions.isNotEmpty()) {
                        state.update {
                            it.copy(
                                promotionOptions = validPromotions,
                                pendingPromotionProduct = product,
                                showPromotionChoice = true,
                                quantityPickerProduct = null,
                                promotionMessage = null,
                            )
                        }
                    } else {
                        addProductIndividual(product, safeQuantity, scope, state)
                    }
                },
                onFailure = { addProductIndividual(product, safeQuantity, scope, state) },
            )
        }
    }

    private fun showQuantityPicker(
        product: DashboardProduct,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        if (!saleGate.canProceed(scope, state)) return
        state.update { it.copy(quantityPickerProduct = product, promotionMessage = null) }
    }

    private fun confirmQuantity(
        action: DashboardSaleUiAction.ConfirmProductQuantity,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        if (action.quantity < 1) {
            state.update { it.copy(promotionMessage = "La cantidad minima es 1") }
            return
        }
        state.update { it.copy(quantityPickerProduct = null) }
        addToCart(action.product, action.quantity, scope, state)
    }

    private fun addIndividualChoice(
        quantity: Int,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        val product = state.value.pendingPromotionProduct ?: return
        dismissPromotionChoice(state)
        addProductIndividual(product, quantity.coerceAtLeast(1), scope, state)
    }

    private fun addPromotionChoice(
        promotion: Promocion,
        times: Int,
        state: MutableStateFlow<DashboardState>,
    ) {
        val safeTimes = times.coerceAtLeast(1)
        val cartWithoutSamePromotion = cartRepository.cartItems.value.filter { it.promocionId != promotion.id }
        validatePromotion(promotion, cartWithoutSamePromotion).fold(
            onSuccess = {
                cartRepository.addPromotionToCart(promotion, safeTimes)
                state.update { current ->
                    current.copy(
                        showPromotionChoice = false,
                        pendingPromotionProduct = null,
                        promotionOptions = emptyList(),
                        quantityPickerProduct = null,
                        promotionMessage = "Promoción agregada: ${promotion.nombre} x$safeTimes",
                    )
                }
            },
            onFailure = { error -> state.update { it.copy(promotionMessage = error.message) } },
        )
    }

    private fun dismissPromotionChoice(state: MutableStateFlow<DashboardState>) {
        state.update {
            it.copy(
                showPromotionChoice = false,
                pendingPromotionProduct = null,
                promotionOptions = emptyList(),
            )
        }
    }

    private fun addProductIndividual(
        dashboardProduct: DashboardProduct,
        quantity: Int,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        if (!saleGate.canProceed(scope, state)) return
        val product = dashboardProduct.sourceProduct ?: dashboardProduct.toManualProduct()
        cartRepository.addToCart(product, quantity.coerceAtLeast(1))
        scope.launch { refreshProductLots(product.id, LotRefreshPolicy.DISCOVER_CONFIGURATION) }
    }
}

private class DashboardManualEntryHandler(
    private val productHandler: DashboardProductActionHandler,
    private val clock: AppClock,
) {
    fun onAction(
        action: DashboardSaleUiAction.Manual,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardSaleUiAction.ManualKey -> appendKey(action.key, state)
            DashboardSaleUiAction.ManualClear -> state.update { it.copy(manualEntryValue = "") }
            DashboardSaleUiAction.ManualBackspace -> state.update { it.copy(manualEntryValue = it.manualEntryValue.dropLast(1)) }
            DashboardSaleUiAction.ManualSubmit -> submit(scope, state)
        }
    }

    private fun appendKey(
        key: String,
        state: MutableStateFlow<DashboardState>,
    ) {
        state.update { current ->
            when {
                key == "." && current.manualEntryValue.contains(".") -> current
                current.manualEntryValue.length > MANUAL_ENTRY_MAX_LENGTH -> current
                else -> current.copy(manualEntryValue = current.manualEntryValue + key)
            }
        }
    }

    private fun submit(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        val value = state.value.manualEntryValue.toDoubleOrNull()
        if (value != null && value > 0) {
            val product =
                DashboardProduct(
                    id = "manual_${clock.now().toEpochMilli()}",
                    name = "Entrada Manual",
                    price = value,
                    taxRate = 0.0,
                    isExempt = true,
                    category = "Manual",
                )
            productHandler.addToCart(product, quantity = 1, scope, state)
            state.update { it.copy(manualEntryValue = "") }
        }
    }

    private companion object {
        const val MANUAL_ENTRY_MAX_LENGTH = 10
    }
}

private fun DashboardProduct.toManualProduct(): Product =
    Product(
        id = id,
        description = name,
        prices = listOf(PriceLevel(label = "A", pricePlusTax = price)),
        taxRate = taxRate,
        isExempt = isExempt,
        department = category,
        code = code.orEmpty(),
        barcode1 = barcode.orEmpty(),
    )
