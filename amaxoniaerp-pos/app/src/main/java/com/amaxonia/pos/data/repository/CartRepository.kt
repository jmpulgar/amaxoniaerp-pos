package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CartRepository {
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // Nuevo: Estado del cliente seleccionado para la transacción actual
    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()

    private val _availableSellers = MutableStateFlow<List<Seller>>(emptyList())
    val availableSellers: StateFlow<List<Seller>> = _availableSellers.asStateFlow()

    private val _currentSeller = MutableStateFlow<Seller?>(null)
    val currentSeller: StateFlow<Seller?> = _currentSeller.asStateFlow()

    fun addToCart(product: Product) {
        val currentSellerId = _currentSeller.value?.id ?: 0
        _cartItems.update { currentItems ->
            val existingIndex = currentItems.indexOfFirst { it.product.id == product.id }
            if (existingIndex != -1) {
                val mutable = currentItems.toMutableList()
                val existingItem = mutable[existingIndex]
                mutable[existingIndex] = existingItem.copy(
                    quantity = existingItem.quantity + 1,
                    codVendedor = if (currentSellerId > 0) currentSellerId else existingItem.codVendedor,
                )
                mutable
            } else {
                currentItems + CartItem(
                    product = product,
                    quantity = 1,
                    codVendedor = currentSellerId,
                    unitPriceWithTax = product.prices.firstOrNull()?.pricePlusTax ?: 0.0
                )
            }
        }
    }

    fun increaseQuantity(productId: String) {
        _cartItems.update { items ->
            items.map { if (it.product.id == productId) it.copy(quantity = it.quantity + 1) else it }
        }
    }

    fun decreaseQuantity(productId: String) {
        _cartItems.update { items ->
            items.mapNotNull {
                if (it.product.id == productId) {
                    if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else null
                } else it
            }
        }
    }

    fun removeItem(productId: String) {
        _cartItems.update { items -> items.filter { it.product.id != productId } }
    }

    fun updateItemPrice(productId: String, unitPriceWithTax: Double) {
        val safePrice = unitPriceWithTax.coerceAtLeast(0.0)
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId) {
                    item.copy(unitPriceWithTax = safePrice)
                } else {
                    item
                }
            }
        }
    }

    fun updateItemDiscount(productId: String, discountPercent: Double) {
        val safeDiscount = discountPercent.coerceIn(0.0, 100.0)
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId) {
                    item.copy(discountPercent = safeDiscount)
                } else {
                    item
                }
            }
        }
    }

    /** Marca un producto como que tiene configuracion de lote */
    fun setItemHasLotConfig(productId: String, hasLotConfig: Boolean) {
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId) item.copy(hasLotConfig = hasLotConfig)
                else item
            }
        }
    }

    /** Asigna lotes FEFO a un item del carrito */
    fun assignLots(productId: String, lots: List<LotAssignment>) {
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId) item.copy(lotAssignments = lots)
                else item
            }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _selectedClient.value = null
    }

    // Función para limpiar solo items (por ejemplo, si quieres mantener el cliente)
    fun clearItemsOnly() {
        _cartItems.value = emptyList()
    }

    // Nuevas funciones para manejar el cliente
    fun setClient(client: Client) {
        _selectedClient.value = client
    }

    fun removeClient() {
        _selectedClient.value = null
    }

    fun setSellerContext(
        defaultSellerId: Int?,
        defaultSellerName: String?,
        sellers: List<Seller>,
    ) {
        val normalized = sellers
            .filter { it.id > 0 }
            .distinctBy { it.id }

        val fallback = if (!defaultSellerName.isNullOrBlank() && (defaultSellerId ?: 0) > 0) {
            Seller(id = defaultSellerId!!, nombre = defaultSellerName)
        } else {
            null
        }

        val available = buildList {
            addAll(normalized)
            if (fallback != null && none { it.id == fallback.id }) {
                add(fallback)
            }
        }

        _availableSellers.value = available

        val selected = available.firstOrNull { it.id == defaultSellerId }
            ?: _currentSeller.value?.let { current -> available.firstOrNull { it.id == current.id } }
            ?: available.firstOrNull()

        if (selected != null) {
            applyCurrentSeller(selected)
        }
    }

    fun setCurrentSeller(seller: Seller) {
        applyCurrentSeller(seller)
    }

    private fun applyCurrentSeller(seller: Seller) {
        val changed = _currentSeller.value?.id != seller.id
        _currentSeller.value = seller
        if (!changed) return

        _cartItems.update { items ->
            items.map { item -> item.copy(codVendedor = seller.id) }
        }
    }

    // Simulación de guardar pedido
    fun saveOrder(client: Client, items: List<CartItem>, total: Double): Result<String> {
        // Aquí conectarías con tu API o Base de Datos para guardar el pedido
        // Retornamos éxito simulado
        clearCart()

        // CORRECCIÓN: Usar firstName y lastName en lugar de .name
        val clientName = "${client.firstName} ${client.lastName}"

        return Result.success("Pedido #ORDER-${System.currentTimeMillis()} creado para $clientName")
    }

    fun getTotal(): Double {
        return _cartItems.value.sumOf { it.total }
    }
}
