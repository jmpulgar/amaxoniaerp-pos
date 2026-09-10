package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.codTipoPrecioToLabel
import com.amaxonia.pos.domain.model.computeFinancialSnapshot
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory cart state exposed through stable domain models. */
class CartRepository {
    private companion object {
        /** Conversión porcentaje → fracción para impuestos, y rango válido de descuento. */
        const val PERCENT_DIVISOR = 100.0
        const val MIN_DISCOUNT_PERCENT = 0.0
        const val MAX_DISCOUNT_PERCENT = 100.0
    }

    internal val cartItemsState = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = cartItemsState.asStateFlow()

    /**
     * Sesión de mesa en la que se enmarca el carrito cuando se opera desde la pantalla de
     * comanda. `null` cuando el carrito es "libre" (apertura directa de venta sin mesa).
     *
     * El carrito físico NO se duplica: el POS lo usa en ambos flujos (venta directa y comanda
     * de mesa). El ViewModel de comanda lee `cartItems` para mostrar los pendientes y los
     * persiste como pedidos contra esta sesión; para luego pasarlos a ENVIADA usa
     * `pedidosMesaRepository.enviarComanda`. Al salir de la comanda, el ViewModel invoca
     * [unbindSesionMesa] para que el siguiente carrito arranque limpio.
     */
    internal val sesionMesaIdState = MutableStateFlow<Int?>(null)
    val sesionMesaId: StateFlow<Int?> = sesionMesaIdState.asStateFlow()

    // Nuevo: Estado del cliente seleccionado para la transacción actual
    internal val selectedClientState = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = selectedClientState.asStateFlow()

    internal val selectedClientSucursalState = MutableStateFlow<ClientBranch?>(null)
    val selectedClientSucursal: StateFlow<ClientBranch?> = selectedClientSucursalState.asStateFlow()

    internal val clientSucursalesState = MutableStateFlow<List<ClientBranch>>(emptyList())
    val clientSucursales: StateFlow<List<ClientBranch>> = clientSucursalesState.asStateFlow()

    internal val availableSellersState = MutableStateFlow<List<Seller>>(emptyList())
    val availableSellers: StateFlow<List<Seller>> = availableSellersState.asStateFlow()

    internal val currentSellerState = MutableStateFlow<Seller?>(null)
    val currentSeller: StateFlow<Seller?> = currentSellerState.asStateFlow()

    private val _financialSnapshot = MutableStateFlow<SaleFinancialSnapshot?>(null)
    val financialSnapshot: StateFlow<SaleFinancialSnapshot?> = _financialSnapshot.asStateFlow()

    fun setFinancialSnapshot(snapshot: SaleFinancialSnapshot?) {
        _financialSnapshot.value = snapshot
    }

    internal fun invalidateFinancialSnapshot() {
        val items = cartItemsState.value
        _financialSnapshot.value = if (items.isEmpty()) null else items.computeFinancialSnapshot()
    }

    fun resolveItemPrice(
        product: Product,
        unit: String,
        targetLabel: String,
    ): Pair<String, Double> {
        val targetLevel = product.prices.firstOrNull { it.label.equals(targetLabel, ignoreCase = true) }
        val targetPrice = targetLevel?.let { calculatePriceForLevel(product, it, unit) } ?: 0.0
        if (targetPrice > 0.0) {
            return (targetLevel?.label ?: targetLabel) to targetPrice
        }
        val defaultLevel =
            product.prices.firstOrNull { it.label.equals("A", ignoreCase = true) }
                ?: product.prices.firstOrNull()
        val defaultPrice = defaultLevel?.let { calculatePriceForLevel(product, it, unit) } ?: 0.0
        return (defaultLevel?.label ?: "A") to defaultPrice
    }

    private fun applyTax(
        product: Product,
        amount: Double,
    ): Double = if (product.isExempt || product.taxRate <= 0.0) amount else amount * (1.0 + product.taxRate / PERCENT_DIVISOR)

    fun calculatePriceForLevel(
        product: Product,
        level: PriceLevel,
        unit: String,
    ): Double {
        val isUnidadBulk = unit == "UNIDAD" && product.bulkQuantity > 1.0
        return when {
            isUnidadBulk && level.unitPricePlusTax > 0.0 -> level.unitPricePlusTax
            isUnidadBulk && level.unitPrice > 0.0 -> applyTax(product, level.unitPrice)
            level.pricePlusTax > 0.0 -> level.pricePlusTax
            level.price > 0.0 -> applyTax(product, level.price)
            else -> 0.0
        }
    }

    fun addToCart(
        product: Product,
        quantity: Int = 1,
    ) {
        val safeQuantity = quantity.coerceAtLeast(1)
        val currentSellerId = currentSellerState.value?.id ?: 0
        val defaultUnit = if (product.bulkQuantity > 1.0) "EMPAQUE" else "UNIDAD"
        val clientLabel = codTipoPrecioToLabel(selectedClientState.value?.codTipoPrecio)
        val (effectiveLabel, initialPrice) = resolveItemPrice(product, defaultUnit, clientLabel)
        val effectiveLevel = product.prices.firstOrNull { it.label.equals(effectiveLabel, ignoreCase = true) }
        val initialDiscount = effectiveLevel?.discountPercent ?: 0.0
        cartItemsState.update { currentItems ->
            val existingIndex = currentItems.indexOfFirst { it.product.id == product.id && !it.isPromotionLine }
            if (existingIndex != -1) {
                val mutable = currentItems.toMutableList()
                val existingItem = mutable[existingIndex]
                val newQuantity = existingItem.quantity + safeQuantity
                mutable[existingIndex] =
                    existingItem.copy(
                        quantity = newQuantity,
                        quantityDecimal = newQuantity.toDouble(),
                        codVendedor = if (currentSellerId > 0) currentSellerId else existingItem.codVendedor,
                    )
                mutable
            } else {
                currentItems +
                    CartItem(
                        product = product,
                        quantity = safeQuantity,
                        quantityDecimal = safeQuantity.toDouble(),
                        itemUnitPackage = defaultUnit,
                        codVendedor = currentSellerId,
                        unitPriceWithTax = initialPrice,
                        discountPercent = initialDiscount,
                        selectedPriceLabel = effectiveLabel,
                        isManualPrice = false,
                    )
            }
        }
        invalidateFinancialSnapshot()
    }

    fun addPromotionToCart(
        promocion: Promocion,
        times: Int = 1,
    ) {
        val safeTimes = times.coerceAtLeast(1)
        val currentSellerId = currentSellerState.value?.id ?: 0
        cartItemsState.update { currentItems ->
            if (currentItems.any { it.promocionId == promocion.id }) {
                return@update updatePromotionLines(currentItems, promocion.id, safeTimes, append = true)
            }
            val promotionLines =
                promocion.detalles.map { detalle ->
                    val baseQuantity =
                        detalle.cantidadTotal.toDouble().takeIf { it > 0.0 } ?: detalle.cantidad.toDouble().coerceAtLeast(1.0)
                    val quantity = baseQuantity * safeTimes
                    CartItem(
                        product =
                            detalle.product.copy(
                                isExempt = detalle.iva.toDouble() <= 0.0,
                                taxRate = detalle.iva.toDouble(),
                                prices =
                                    listOf(
                                        com.amaxonia.pos.domain.model.PriceLevel(
                                            label = "PROMO",
                                            pricePlusTax =
                                                detalle.totalConIva.toDouble() / baseQuantity,
                                        ),
                                    ),
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
                        promocionVeces = safeTimes,
                    )
                }
            currentItems + promotionLines
        }
        invalidateFinancialSnapshot()
    }

    fun increaseQuantity(productId: String) {
        updateItemQuantity(
            productId,
            (cartItemsState.value.firstOrNull { it.product.id == productId && !it.isPromotionLine }?.quantity ?: 0) + 1,
        )
    }

    fun decreaseQuantity(productId: String) {
        updateItemQuantity(
            productId,
            (cartItemsState.value.firstOrNull { it.product.id == productId && !it.isPromotionLine }?.quantity ?: 1) - 1,
        )
    }

    fun updateItemQuantity(
        productId: String,
        quantity: Int,
    ) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        cartItemsState.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    item.copy(quantity = quantity, quantityDecimal = quantity.toDouble())
                } else {
                    item
                }
            }
        }
        invalidateFinancialSnapshot()
    }

    fun updatePromotionQuantity(
        promocionId: String,
        times: Int,
    ) {
        if (times <= 0) {
            removePromotion(promocionId)
            return
        }
        cartItemsState.update { items ->
            updatePromotionLines(items, promocionId, times, append = false)
        }
        invalidateFinancialSnapshot()
    }

    fun removeItem(productId: String) {
        cartItemsState.update { items -> items.filter { it.product.id != productId || it.isPromotionLine } }
        invalidateFinancialSnapshot()
    }

    fun removePromotion(promotionId: String) {
        cartItemsState.update { items -> items.filter { it.promocionId != promotionId } }
        invalidateFinancialSnapshot()
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
            val baseQuantity = item.quantityDecimal / currentTimes
            val nextQuantity = baseQuantity * nextTimes
            item.copy(
                quantity = nextQuantity.toInt().coerceAtLeast(1),
                quantityDecimal = nextQuantity,
                promocionVeces = nextTimes,
            )
        }
    }

    fun updateItemPrice(
        productId: String,
        unitPriceWithTax: Double,
    ) {
        val safePrice = unitPriceWithTax.coerceAtLeast(0.0)
        cartItemsState.update { items ->
            items.map { item ->
                if (item.product.id == productId) {
                    item.copy(unitPriceWithTax = safePrice, isManualPrice = true)
                } else {
                    item
                }
            }
        }
        invalidateFinancialSnapshot()
    }

    fun updateItemPriceLevel(
        productId: String,
        priceLevelLabel: String,
    ) {
        cartItemsState.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine) {
                    val (effectiveLabel, newPrice) = resolveItemPrice(item.product, item.itemUnitPackage, priceLevelLabel)
                    val effectiveLevel = item.product.prices.firstOrNull { it.label.equals(effectiveLabel, ignoreCase = true) }
                    val levelDiscount = effectiveLevel?.discountPercent ?: 0.0
                    item.copy(
                        selectedPriceLabel = effectiveLabel,
                        unitPriceWithTax = newPrice,
                        discountPercent = if (levelDiscount > 0.0 || item.discountPercent == 0.0) levelDiscount else item.discountPercent,
                        isManualPrice = false,
                    )
                } else {
                    item
                }
            }
        }
        invalidateFinancialSnapshot()
    }

    fun updateItemDiscount(
        productId: String,
        discountPercent: Double,
    ) {
        val safeDiscount = discountPercent.coerceIn(MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT)
        runCatching {
            com.amaxonia.pos.core.logging.SafeLog.d(
                "POS-TOTALS",
                "updateItemDiscount: productId=$productId, requested=$discountPercent, safeDiscount=$safeDiscount",
            )
        }
        cartItemsState.update { items ->
            items.map { item ->
                if (item.product.id == productId) {
                    item.copy(discountPercent = safeDiscount)
                } else {
                    item
                }
            }
        }
        invalidateFinancialSnapshot()
    }

    fun updateItemUnit(
        productId: String,
        unit: String,
    ) {
        val normalizedUnit = if (unit == "UNIDAD") "UNIDAD" else "EMPAQUE"
        cartItemsState.update { items ->
            items.map { item ->
                if (item.product.id == productId && !item.isPromotionLine && item.product.canSwitchUnit) {
                    val targetLabel = if (item.isManualPrice) "A" else item.selectedPriceLabel
                    val (effectiveLabel, newPrice) = resolveItemPrice(item.product, normalizedUnit, targetLabel)
                    item.copy(
                        itemUnitPackage = normalizedUnit,
                        selectedPriceLabel = if (item.isManualPrice) item.selectedPriceLabel else effectiveLabel,
                        unitPriceWithTax = if (item.isManualPrice) item.unitPriceWithTax else newPrice,
                    )
                } else {
                    item
                }
            }
        }
        invalidateFinancialSnapshot()
    }

    fun clearCart() {
        cartItemsState.value = emptyList()
        _financialSnapshot.value = null
        selectedClientState.value = null
        selectedClientSucursalState.value = null
        clientSucursalesState.value = emptyList()
        currentSellerState.value = null
        availableSellersState.value = emptyList()
    }

    // Función para limpiar solo items (por ejemplo, si quieres mantener el cliente)
    fun clearItemsOnly() {
        cartItemsState.value = emptyList()
        _financialSnapshot.value = null
    }
}
