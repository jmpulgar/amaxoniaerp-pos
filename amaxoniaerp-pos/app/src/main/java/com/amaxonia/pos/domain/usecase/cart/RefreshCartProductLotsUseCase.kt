package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ProductLotAvailability
import com.amaxonia.pos.domain.repository.ProductLotRepository

enum class LotRefreshPolicy {
    DISCOVER_CONFIGURATION,
    KNOWN_CONFIGURATION_ONLY,
}

class RefreshCartProductLotsUseCase(
    private val cartRepository: CartRepository,
    private val productLotRepository: ProductLotRepository,
) {
    suspend operator fun invoke(
        productId: String,
        policy: LotRefreshPolicy,
    ) {
        val existingItem = cartRepository.cartItems.value.firstOrNull { it.product.id == productId }
        if (policy == LotRefreshPolicy.KNOWN_CONFIGURATION_ONLY && existingItem?.hasLotConfig != true) return

        productLotRepository.getForProduct(productId).onSuccess { configuration ->
            if (configuration.isConfigured) {
                if (policy == LotRefreshPolicy.DISCOVER_CONFIGURATION) {
                    cartRepository.setItemHasLotConfig(productId, true)
                }
                val currentItem = cartRepository.cartItems.value.firstOrNull { it.product.id == productId }
                val quantity = currentItem?.quantityTotal?.toInt() ?: 0
                if (quantity > 0 && configuration.lots.isNotEmpty()) {
                    cartRepository.assignLots(productId, assignFefo(configuration.lots, quantity))
                }
            }
        }
    }

    private fun assignFefo(
        lots: List<ProductLotAvailability>,
        totalQuantity: Int,
    ): List<LotAssignment> {
        val assignments = mutableListOf<LotAssignment>()
        var remaining = totalQuantity
        for (lot in lots) {
            if (remaining <= 0) break
            val assigned = minOf(remaining, lot.availableQuantity)
            if (assigned > 0) {
                assignments +=
                    LotAssignment(
                        idLoteItem = lot.id.toString(),
                        codigoLote = lot.code,
                        vencimiento = lot.expiration,
                        cantidad = assigned,
                        almacen = lot.warehouseId,
                    )
                remaining -= assigned
            }
        }
        return assignments
    }
}
