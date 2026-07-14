package com.amaxonia.pos.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CartState(
    val items: List<CartItem> = emptyList(),
    val displayItems: List<ItemCarrito> = emptyList(),
    val total: Double = 0.0,
    val selectedClient: Client? = null,
    val selectedClientPhotoUrl: String = "",
    val currentSeller: Seller? = null,
    val availableSellers: List<Seller> = emptyList(),
    val orderSuccessMessage: String? = null,
    val allowEditPrices: Boolean = false,
    val allowDiscounts: Boolean = false,
    val tasa: Double = 0.0,
    val abrMonedaSecundaria: String = "",
    val isMultiCurrency: Boolean = false,
    val isPanama: Boolean = false,
    val clientSucursales: List<ClientBranch> = emptyList(),
    val selectedClientSucursal: ClientBranch? = null,
    val cartActionError: String? = null,
) {
    val totalBsText: String
        get() = if (isMultiCurrency && tasa > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", total * tasa) else ""

    val requiresClientSucursal: Boolean
        get() = isPanama && selectedClient != null && clientSucursales.isNotEmpty()

    val isMissingRequiredClientSucursal: Boolean
        get() = requiresClientSucursal && selectedClientSucursal == null
}

sealed interface CartUiAction {
    data class IncreaseQuantity(
        val productId: String,
    ) : CartQuantityUiAction

    data class DecreaseQuantity(
        val productId: String,
    ) : CartQuantityUiAction

    data class UpdateItemQuantity(
        val productId: String,
        val quantity: Int,
    ) : CartQuantityUiAction

    data class RemoveItem(
        val productId: String,
    ) : CartRemovalUiAction

    data class RemovePromotion(
        val promotionId: String,
    ) : CartRemovalUiAction

    data class UpdatePromotionQuantity(
        val promotionId: String,
        val times: Int,
    ) : CartRemovalUiAction

    data class UpdateItemPrice(
        val productId: String,
        val unitPriceWithTax: Double,
    ) : CartEditableUiAction

    data class UpdateItemDiscount(
        val productId: String,
        val discountPercent: Double,
    ) : CartEditableUiAction

    data class UpdateItemUnit(
        val productId: String,
        val unit: String,
    ) : CartQuantityUiAction

    data class SelectClientBranch(
        val branchId: Int,
    ) : CartContextUiAction

    data class SelectSeller(
        val sellerId: Int,
    ) : CartContextUiAction

    data object ClearCart : CartContextUiAction

    data object RemoveClient : CartContextUiAction

    data object SaveDraft : CartUiAction

    data object ClearMessage : CartUiAction

    data object ClearActionError : CartUiAction

    data object Checkout : CartUiAction
}

sealed interface CartItemUiAction : CartUiAction

sealed interface CartQuantityUiAction : CartItemUiAction

sealed interface CartRemovalUiAction : CartItemUiAction

sealed interface CartEditableUiAction : CartItemUiAction

sealed interface CartContextUiAction : CartUiAction

sealed interface CartUiEffect {
    data class Checkout(
        val total: Double,
    ) : CartUiEffect
}

class CartViewModel(
    private val stateCoordinator: CartStateCoordinator,
    private val configurationCoordinator: CartConfigurationCoordinator,
    private val actionHandler: CartActionHandler,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<CartUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<CartUiEffect> = mutableEffects.asSharedFlow()

    init {
        stateCoordinator.start(viewModelScope, mutableState)
        configurationCoordinator.start(viewModelScope, mutableState)
    }

    fun onAction(action: CartUiAction) {
        actionHandler.onAction(action, viewModelScope, mutableState, mutableEffects)
    }
}
