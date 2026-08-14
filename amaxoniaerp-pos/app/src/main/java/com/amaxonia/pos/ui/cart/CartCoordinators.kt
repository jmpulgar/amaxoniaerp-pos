package com.amaxonia.pos.ui.cart

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientBranchRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import com.amaxonia.pos.domain.usecase.cart.LotRefreshPolicy
import com.amaxonia.pos.domain.usecase.cart.RefreshCartProductLotsUseCase
import com.amaxonia.pos.domain.usecase.cart.ResolveClientImageUrlUseCase
import com.amaxonia.pos.domain.usecase.cart.SaveDraftInvoiceInput
import com.amaxonia.pos.domain.usecase.cart.SaveDraftInvoiceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CartStateCoordinator(
    private val cartRepository: CartRepository,
    private val clientRepository: ClientRepository,
    private val sessionReader: DashboardSessionReader,
    private val clientBranchRepository: ClientBranchRepository,
    private val resolveClientImageUrl: ResolveClientImageUrlUseCase,
) {
    fun start(
        scope: CoroutineScope,
        state: MutableStateFlow<CartState>,
    ) {
        scope.launch {
            state.update {
                it.copy(isPanama = sessionReader.currentCountry()?.code.equals(PANAMA_CODE, ignoreCase = true))
            }
        }
        scope.launch {
            cartRepository.cartItems.collect { items ->
                updateCartState(state, items, cartRepository.selectedClient.value, cartRepository.financialSnapshot.value)
            }
        }
        scope.launch {
            cartRepository.financialSnapshot.collect { snapshot ->
                updateCartState(state, cartRepository.cartItems.value, cartRepository.selectedClient.value, snapshot)
            }
        }
        scope.launch {
            cartRepository.selectedClient.collect { client ->
                updateCartState(state, cartRepository.cartItems.value, client, cartRepository.financialSnapshot.value)
                state.update { it.copy(selectedClientPhotoUrl = client?.let { selected -> resolveClientImageUrl(selected) }.orEmpty()) }
                loadClientBranches(client, state)
            }
        }
        scope.launch {
            cartRepository.clientSucursales.collect { branches ->
                state.update {
                    it.copy(
                        clientSucursales = branches,
                        cartActionError = if (branches.isEmpty()) null else it.cartActionError,
                    )
                }
            }
        }
        scope.launch {
            cartRepository.selectedClientSucursal.collect { branch ->
                state.update {
                    it.copy(
                        selectedClientSucursal = branch,
                        cartActionError = if (branch != null) null else it.cartActionError,
                    )
                }
            }
        }
        scope.launch {
            cartRepository.currentSeller.collect { seller -> state.update { it.copy(currentSeller = seller) } }
        }
        scope.launch {
            cartRepository.availableSellers.collect { sellers -> state.update { it.copy(availableSellers = sellers) } }
        }
        ensureDefaultClient(scope)
    }

    private fun updateCartState(
        state: MutableStateFlow<CartState>,
        items: List<CartItem>,
        client: Client?,
        financialSnapshot: com.amaxonia.pos.domain.model.SaleFinancialSnapshot? = cartRepository.financialSnapshot.value,
    ) {
        state.update {
            it.copy(
                items = items,
                displayItems = cartRepository.getDisplayItems(),
                total = financialSnapshot?.total ?: items.sumOf { item -> item.total },
                selectedClient = client,
                selectedClientPhotoUrl = if (client == null) "" else it.selectedClientPhotoUrl,
                cartActionError = if (client == null) null else it.cartActionError,
            )
        }
    }

    private suspend fun loadClientBranches(
        client: Client?,
        state: MutableStateFlow<CartState>,
    ) {
        val isPanama = sessionReader.currentCountry()?.code.equals(PANAMA_CODE, ignoreCase = true)
        state.update { it.copy(isPanama = isPanama) }
        if (!isPanama || client == null) {
            cartRepository.setClientSucursales(emptyList())
        } else {
            cartRepository.setClientSucursales(clientBranchRepository.findFor(client))
        }
    }

    private fun ensureDefaultClient(scope: CoroutineScope) {
        if (cartRepository.selectedClient.value != null) return
        scope.launch {
            clientRepository.getDefaultClient().onSuccess(cartRepository::setClient)
        }
    }

    private companion object {
        const val PANAMA_CODE = "PA"
    }
}

class CartConfigurationCoordinator(
    private val settingsRepository: PosSettingsRepository,
    private val activeCajaReader: ActiveCajaReader,
) {
    fun start(
        scope: CoroutineScope,
        state: MutableStateFlow<CartState>,
    ) {
        scope.launch {
            settingsRepository.allowEditPrices.collect { enabled -> state.update { it.copy(allowEditPrices = enabled) } }
        }
        scope.launch {
            settingsRepository.allowDiscounts.collect { enabled -> state.update { it.copy(allowDiscounts = enabled) } }
        }
        scope.launch {
            activeCajaReader.activeCaja.collect { caja ->
                val currency = caja?.currency
                val isMultiCurrency = currency?.multiMoneda.equals("SI", ignoreCase = true)
                state.update {
                    it.copy(
                        tasa = if (isMultiCurrency) currency?.tasa?.takeIf { rate -> rate > 0.0 } ?: 0.0 else 0.0,
                        abrMonedaSecundaria = if (isMultiCurrency) currency?.abrMonedaSecundaria.orEmpty() else "",
                        isMultiCurrency = isMultiCurrency,
                    )
                }
            }
        }
    }
}

class CartActionHandler(
    private val cartRepository: CartRepository,
    private val refreshProductLots: RefreshCartProductLotsUseCase,
    private val saveDraftInvoice: SaveDraftInvoiceUseCase,
) {
    fun onAction(
        action: CartUiAction,
        scope: CoroutineScope,
        state: MutableStateFlow<CartState>,
        effects: MutableSharedFlow<CartUiEffect>,
    ) {
        when (action) {
            is CartItemUiAction -> handleItemAction(action, scope, state)
            is CartContextUiAction -> handleContextAction(action, state)
            CartUiAction.SaveDraft -> saveDraft(scope, state)
            CartUiAction.ClearMessage -> state.update { it.copy(orderSuccessMessage = null) }
            CartUiAction.ClearActionError -> state.update { it.copy(cartActionError = null) }
            CartUiAction.Checkout -> {
                if (validateClientBranch(state, "Selecciona la sucursal del cliente antes de cobrar.")) {
                    effects.tryEmit(CartUiEffect.Checkout(state.value.total))
                }
            }
        }
    }

    private fun handleItemAction(
        action: CartItemUiAction,
        scope: CoroutineScope,
        state: StateFlow<CartState>,
    ) {
        when (action) {
            is CartQuantityUiAction -> handleQuantityAction(action, scope)
            is CartRemovalUiAction -> handleRemovalAction(action)
            is CartEditableUiAction -> handleEditableAction(action, state)
        }
    }

    private fun handleQuantityAction(
        action: CartQuantityUiAction,
        scope: CoroutineScope,
    ) {
        val productId =
            when (action) {
                is CartUiAction.IncreaseQuantity -> action.productId.also(cartRepository::increaseQuantity)
                is CartUiAction.DecreaseQuantity -> action.productId.also(cartRepository::decreaseQuantity)
                is CartUiAction.UpdateItemQuantity -> action.productId.also { cartRepository.updateItemQuantity(it, action.quantity) }
                is CartUiAction.UpdateItemUnit -> action.productId.also { cartRepository.updateItemUnit(it, action.unit) }
            }
        scope.launch { refreshProductLots(productId, LotRefreshPolicy.KNOWN_CONFIGURATION_ONLY) }
    }

    private fun handleRemovalAction(action: CartRemovalUiAction) {
        when (action) {
            is CartUiAction.RemoveItem -> cartRepository.removeItem(action.productId)
            is CartUiAction.RemovePromotion -> cartRepository.removePromotion(action.promotionId)
            is CartUiAction.UpdatePromotionQuantity -> cartRepository.updatePromotionQuantity(action.promotionId, action.times)
        }
    }

    private fun handleEditableAction(
        action: CartEditableUiAction,
        state: StateFlow<CartState>,
    ) {
        when (action) {
            is CartUiAction.UpdateItemPrice -> {
                if (state.value.allowEditPrices) cartRepository.updateItemPrice(action.productId, action.unitPriceWithTax)
            }
            is CartUiAction.UpdateItemDiscount -> {
                if (state.value.allowDiscounts) cartRepository.updateItemDiscount(action.productId, action.discountPercent)
            }
        }
    }

    private fun handleContextAction(
        action: CartContextUiAction,
        state: MutableStateFlow<CartState>,
    ) {
        when (action) {
            CartUiAction.ClearCart -> cartRepository.clearCart()
            CartUiAction.RemoveClient -> cartRepository.removeClient()
            is CartUiAction.SelectClientBranch -> {
                val branch = state.value.clientSucursales.firstOrNull { it.sucursalId == action.branchId }
                cartRepository.setClientSucursal(branch)
                state.update { it.copy(cartActionError = null) }
            }
            is CartUiAction.SelectSeller -> {
                cartRepository.availableSellers.value
                    .firstOrNull { it.id == action.sellerId }
                    ?.let(cartRepository::setCurrentSeller)
            }
        }
    }

    private fun saveDraft(
        scope: CoroutineScope,
        state: MutableStateFlow<CartState>,
    ) {
        val items = state.value.items
        if (items.isEmpty()) return
        if (!validateClientBranch(state, "Selecciona la sucursal del cliente antes de guardar la venta.")) return
        scope.launch {
            val client = state.value.selectedClient
            val seller = state.value.currentSeller
            saveDraftInvoice(SaveDraftInvoiceInput(items, client, seller, state.value.total))
            cartRepository.clearCart()
            val clientLabel = if (client != null) "${client.firstName} ${client.lastName}" else "Sin cliente"
            state.update { it.copy(orderSuccessMessage = "Borrador guardado para $clientLabel (${items.size} productos)") }
        }
    }

    private fun validateClientBranch(
        state: MutableStateFlow<CartState>,
        message: String,
    ): Boolean {
        if (!state.value.isMissingRequiredClientSucursal) return true
        state.update { it.copy(cartActionError = message) }
        return false
    }
}
