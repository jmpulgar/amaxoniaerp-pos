package com.amaxonia.pos.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartState(
    val items: List<CartItem> = emptyList(),
    val total: Double = 0.0,
    val selectedClient: Client? = null,
    val currentSeller: Seller? = null,
    val availableSellers: List<Seller> = emptyList(),
    val orderSuccessMessage: String? = null,
    val allowEditPrices: Boolean = false,
    val allowDiscounts: Boolean = false
)

class CartViewModel(
    private val cartRepository: CartRepository,
    private val clientRepository: ClientRepository,
    private val localStore: com.amaxonia.pos.data.local.LocalStore,
    private val apiConfigManager: com.amaxonia.pos.data.remote.ApiConfigManager
) : ViewModel() {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    @Volatile
    private var adminDb: String = ""

    init {
        viewModelScope.launch {
            adminDb = localStore.readCompanySession()?.company?.adminDb ?: ""
        }
        viewModelScope.launch {
            cartRepository.cartItems.collect { items ->
                updateState(items, cartRepository.selectedClient.value)
            }
        }
        viewModelScope.launch {
            cartRepository.selectedClient.collect { client ->
                updateState(cartRepository.cartItems.value, client)
            }
        }
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
        viewModelScope.launch {
            localStore.allowEditPricesFlow().collect { enabled ->
                _state.update { it.copy(allowEditPrices = enabled) }
            }
        }
        viewModelScope.launch {
            localStore.allowDiscountsFlow().collect { enabled ->
                _state.update { it.copy(allowDiscounts = enabled) }
            }
        }
        ensureDefaultClient()
    }

    private fun updateState(items: List<CartItem>, client: Client?) {
        _state.update {
            it.copy(
                items = items,
                total = items.sumOf { item -> item.total },
                selectedClient = client
            )
        }
    }

    fun increaseQuantity(productId: String) = cartRepository.increaseQuantity(productId)
    fun decreaseQuantity(productId: String) = cartRepository.decreaseQuantity(productId)
    fun removeItem(productId: String) = cartRepository.removeItem(productId)

    fun updateItemPrice(productId: String, unitPriceWithTax: Double) {
        if (!_state.value.allowEditPrices) return
        cartRepository.updateItemPrice(productId, unitPriceWithTax)
    }

    fun updateItemDiscount(productId: String, discountPercent: Double) {
        if (!_state.value.allowDiscounts) return
        cartRepository.updateItemDiscount(productId, discountPercent)
    }

    fun clearCart() {
        cartRepository.clearCart()
    }

    fun removeClient() {
        cartRepository.removeClient()
    }

    fun selectSeller(sellerId: Int) {
        val seller = cartRepository.availableSellers.value.firstOrNull { it.id == sellerId } ?: return
        cartRepository.setCurrentSeller(seller)
    }

    fun createOrder() {
        val client = _state.value.selectedClient ?: return
        val items = _state.value.items
        if (items.isEmpty()) return

        val result = cartRepository.saveOrder(client, items, _state.value.total)
        result.onSuccess { msg ->
            _state.update { it.copy(orderSuccessMessage = msg) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(orderSuccessMessage = null) }
    }

    fun getClientPhotoUrl(client: Client): String {
        if (client.id.isBlank() || adminDb.isBlank()) return ""
        val filename = client.photoFilename.takeIf { it.isNotBlank() }
            ?: return ""
        return com.amaxonia.pos.data.remote.ImageUrlHelper.clientPhotoUrl(
            baseUrl = apiConfigManager.baseUrl.value,
            countryCode = apiConfigManager.getCurrentCountryCode(),
            companyDb = adminDb,
            idCliente = client.id,
            photoFilename = filename
        )
    }

    private fun ensureDefaultClient() {
        if (cartRepository.selectedClient.value != null) return
        viewModelScope.launch {
            clientRepository.getDefaultClient()
                .onSuccess { defaultClient -> cartRepository.setClient(defaultClient) }
        }
    }
}
