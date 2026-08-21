package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.LotAssignment
import kotlinx.coroutines.flow.update

// Configuración de lotes del carrito: marcado de items con lote y asignación FEFO.
// Extensiones sobre CartRepository: mismo comportamiento que cuando eran miembros.

/** Marca un producto como que tiene configuracion de lote */
fun CartRepository.setItemHasLotConfig(
    productId: String,
    hasLotConfig: Boolean,
) {
    cartItemsState.update { items ->
        items.map { item ->
            if (item.product.id == productId && !item.isPromotionLine) {
                item.copy(hasLotConfig = hasLotConfig)
            } else {
                item
            }
        }
    }
}

/** Asigna lotes FEFO a un item del carrito */
fun CartRepository.assignLots(
    productId: String,
    lots: List<LotAssignment>,
) {
    cartItemsState.update { items ->
        items.map { item ->
            if (item.product.id == productId && !item.isPromotionLine) {
                item.copy(lotAssignments = lots)
            } else {
                item
            }
        }
    }
}
