package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
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

    private val _selectedClientSucursal = MutableStateFlow<ClientSucursalEntity?>(null)
    val selectedClientSucursal: StateFlow<ClientSucursalEntity?> = _selectedClientSucursal.asStateFlow()

    private val _clientSucursales = MutableStateFlow<List<ClientSucursalEntity>>(emptyList())
    val clientSucursales: StateFlow<List<ClientSucursalEntity>> = _clientSucursales.asStateFlow()

    private val _availableSellers = MutableStateFlow<List<Seller>>(emptyList())
    val availableSellers: StateFlow<List<Seller>> = _availableSellers.asStateFlow()

    private val _currentSeller = MutableStateFlow<Seller?>(null)
    val currentSeller: StateFlow<Seller?> = _currentSeller.asStateFlow()

    fun addToCart(product: Product, quantity: Int = 1) {
        val safeQuantity = quantity.coerceAtLeast(1)
        val currentSellerId = _currentSeller.value?.id ?: 0
        val defaultUnit = if (product.bulkQuantity > 1.0) "EMPAQUE" else "UNIDAD"
        val defaultPrice = priceForUnit(product, defaultUnit)
        _cartItems.update { currentItems ->
                val existingIndex = currentItems.indexOfFirst { it.product.id == product.id && !it.isPromotionLine }
                if (existingIndex != -1) {
                val mutable = currentItems.toMutableList()
                val existingItem = mutable[existingIndex]
                val newQuantity = existingItem.quantity + safeQuantity
                mutable[existingIndex] = existingItem.copy(
                    quantity = newQuantity,
                    quantityDecimal = newQuantity.toDouble(),
                    codVendedor = if (currentSellerId > 0) currentSellerId else existingItem.codVendedor,
                )
                mutable
            } else {
                currentItems + CartItem(
                    product = product,
                    quantity = safeQuantity,
                    quantityDecimal = safeQuantity.toDouble(),
                    itemUnitPackage = defaultUnit,
                    codVendedor = currentSellerId,
                    unitPriceWithTax = defaultPrice
                )
            }
        }
    }

    private fun priceForUnit(product: Product, unit: String): Double {
        val price = product.prices.firstOrNull()
        return if (unit == "UNIDAD" && product.bulkQuantity > 1.0) {
            price?.unitPricePlusTax?.takeIf { it > 0.0 }
                ?: price?.unitPrice?.takeIf { it > 0.0 }?.let { unitPrice ->
                    if (product.isExempt || product.taxRate <= 0.0) unitPrice else unitPrice * (1.0 + product.taxRate / 100.0)
                }
                ?: 0.0
        } else {
            price?.pricePlusTax ?: 0.0
        }
    }

    fun addPromotionToCart(promocion: Promocion, times: Int = 1) {
        val safeTimes = times.coerceAtLeast(1)
        val currentSellerId = _currentSeller.value?.id ?: 0
        _cartItems.update { currentItems ->
            if (currentItems.any { it.promocionId == promocion.id }) {
                return@update updatePromotionLines(currentItems, promocion.id, safeTimes, append = true)
            }
            val promotionLines = promocion.detalles.map { detalle ->
                val baseQuantity = detalle.cantidadTotal.toDouble().takeIf { it > 0.0 } ?: detalle.cantidad.toDouble().coerceAtLeast(1.0)
                val quantity = baseQuantity * safeTimes
                CartItem(
                    product = detalle.product.copy(
                        isExempt = detalle.iva.toDouble() <= 0.0,
                        taxRate = detalle.iva.toDouble(),
                        prices = listOf(com.amaxonia.pos.domain.model.PriceLevel(label = "PROMO", pricePlusTax = detalle.totalConIva.toDouble() / baseQuantity))
                    ),
                    quantity = quantity.toInt().coerceAtLeast(1),
                    quantityDecimal = quantity,
                    codVendedor = currentSellerId,
                    unitPriceWithTax = detalle.totalConIva.toDouble() / quantity,
                    discountPercent = detalle.descuento.toDouble(),
                    promocionId = promocion.id,
                    promocionCodigo = promocion.codigo,
                    promocionNombre = promocion.nombre,
                    promocionTipo = promocion.tipo,
                    promocionGrupo = detalle.grupo,
                    promocionDetalleId = detalle.id,
                    promocionVeces = safeTimes
                )
            }
            currentItems + promotionLines
        }
    }

    fun getDisplayItems(): List<ItemCarrito> {
        val grouped = mutableListOf<ItemCarrito>()
        val promotionIds = mutableSetOf<String>()
        _cartItems.value.forEach { item ->
            val promoId = item.promocionId
            if (promoId.isNullOrBlank()) {
                grouped.add(ItemCarrito.ProductoIndividual(item))
            } else if (promotionIds.add(promoId)) {
                val promoItems = _cartItems.value.filter { it.promocionId == promoId }
                val first = promoItems.first()
                grouped.add(
                    ItemCarrito.PromocionAgrupada(
                        promocionId = promoId,
                        promocionCodigo = first.promocionCodigo,
                        promocionNombre = first.promocionNombre,
                        promocionTipo = first.promocionTipo,
                        promocionGrupo = first.promocionGrupo,
                        items = promoItems
                    )
                )
            }
        }
        return grouped
    }

    fun increaseQuantity(productId: String) {
        updateItemQuantity(productId, (_cartItems.value.firstOrNull { it.product.id == productId && !it.isPromotionLine }?.quantity ?: 0) + 1)
    }

    fun decreaseQuantity(productId: String) {
        updateItemQuantity(productId, (_cartItems.value.firstOrNull { it.product.id == productId && !it.isPromotionLine }?.quantity ?: 1) - 1)
    }

    fun updateItemQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(quantity = quantity, quantityDecimal = quantity.toDouble())
                } else {
                    item
                }
            }
        }
    }

    fun updatePromotionQuantity(promocionId: String, times: Int) {
        if (times <= 0) {
            removePromotion(promocionId)
            return
        }
        _cartItems.update { items ->
            updatePromotionLines(items, promocionId, times, append = false)
        }
    }

    fun removeItem(productId: String) {
        _cartItems.update { items -> items.filter { it.product.id != productId || it.isPromotionLine } }
    }

    fun removePromotion(promotionId: String) {
        _cartItems.update { items -> items.filter { it.promocionId != promotionId } }
    }

    private fun updatePromotionLines(
        items: List<CartItem>,
        promotionId: String,
        times: Int,
        append: Boolean
    ): List<CartItem> {
        val safeTimes = times.coerceAtLeast(1)
        return items.map { item ->
            if (item.promocionId != promotionId) return@map item
            val currentTimes = item.promocionVeces.coerceAtLeast(1)
            val nextTimes = if (append) currentTimes + safeTimes else safeTimes
            val baseQuantity = item.quantityDecimal / currentTimes
            val nextQuantity = baseQuantity * nextTimes
            item.copy(
                quantity = nextQuantity.toInt().coerceAtLeast(1),
                quantityDecimal = nextQuantity,
                promocionVeces = nextTimes
            )
        }
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

    fun updateItemUnit(productId: String, unit: String) {
        val normalizedUnit = if (unit == "UNIDAD") "UNIDAD" else "EMPAQUE"
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine && item.product.canSwitchUnit) {
                    item.copy(
                        itemUnitPackage = normalizedUnit,
                        unitPriceWithTax = priceForUnit(item.product, normalizedUnit)
                    )
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
                if (item.product.id == productId && !item.isPromotionLine) item.copy(hasLotConfig = hasLotConfig)
                else item
            }
        }
    }

    /** Asigna lotes FEFO a un item del carrito */
    fun assignLots(productId: String, lots: List<LotAssignment>) {
        _cartItems.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) item.copy(lotAssignments = lots)
                else item
            }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _selectedClient.value = null
        _selectedClientSucursal.value = null
        _clientSucursales.value = emptyList()
        _currentSeller.value = null
        _availableSellers.value = emptyList()
    }

    // Función para limpiar solo items (por ejemplo, si quieres mantener el cliente)
    fun clearItemsOnly() {
        _cartItems.value = emptyList()
    }

    // Nuevas funciones para manejar el cliente
    fun setClient(client: Client) {
        _selectedClient.value = client
        _selectedClientSucursal.value = null
        _clientSucursales.value = emptyList()
    }

    fun removeClient() {
        _selectedClient.value = null
        _selectedClientSucursal.value = null
        _clientSucursales.value = emptyList()
    }

    fun setClientSucursal(sucursal: ClientSucursalEntity?) {
        _selectedClientSucursal.value = sucursal
    }

    fun setClientSucursales(sucursales: List<ClientSucursalEntity>) {
        val normalized = sucursales.distinctBy { it.sucursalId }
        _clientSucursales.value = normalized

        val current = _selectedClientSucursal.value
            ?.takeIf { selected -> normalized.any { it.sucursalId == selected.sucursalId } }
        _selectedClientSucursal.value = current ?: normalized.singleOrNull()
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
