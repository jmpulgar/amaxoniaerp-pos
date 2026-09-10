package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.codTipoPrecioToLabel
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.flow.update

// Contexto de sesión del carrito: mesa, cliente/sucursal y vendedor.
// Extensiones sobre CartRepository: mismo comportamiento que cuando eran miembros.

/** Ítems del carrito agrupados para display (productos individuales y promociones). */
fun CartRepository.getDisplayItems(): List<ItemCarrito> {
    val grouped = mutableListOf<ItemCarrito>()
    val promotionIds = mutableSetOf<String>()
    cartItemsState.value.forEach { item ->
        val promoId = item.promocionId
        if (promoId.isNullOrBlank()) {
            grouped.add(ItemCarrito.ProductoIndividual(item))
        } else if (promotionIds.add(promoId)) {
            val promoItems = cartItemsState.value.filter { it.promocionId == promoId }
            val first = promoItems.first()
            grouped.add(
                ItemCarrito.PromocionAgrupada(
                    promocionId = promoId,
                    promocionCodigo = first.promocionCodigo,
                    promocionNombre = first.promocionNombre,
                    promocionTipo = first.promocionTipo,
                    promocionGrupo = first.promocionGrupo,
                    items = promoItems,
                ),
            )
        }
    }
    return grouped
}

/** Enmarca el carrito en la sesión de mesa indicada. */
fun CartRepository.bindSesionMesa(sesionId: Int) {
    sesionMesaIdState.value = sesionId
}

/** Libera la sesión de mesa (el siguiente carrito arranca limpio). */
fun CartRepository.unbindSesionMesa() {
    sesionMesaIdState.value = null
}

/** Selecciona el cliente de la transacción y resetea su sucursal. */
fun CartRepository.setClient(client: Client) {
    runCatching {
        com.amaxonia.pos.core.logging.SafeLog.d(
            "POS-CLIENT-PRICE",
            "setClient -> id=${client.id}, code=${client.code}, name=${client.firstName} ${client.lastName}, " +
                "codTipoPrecio=${client.codTipoPrecio} -> label=${codTipoPrecioToLabel(client.codTipoPrecio)}",
        )
    }
    selectedClientState.value = client
    selectedClientSucursalState.value = null
    clientSucursalesState.value = emptyList()
    recalculateCartPricesForClient(client)
}

/** Quita el cliente y su sucursal de la transacción. */
fun CartRepository.removeClient() {
    runCatching {
        com.amaxonia.pos.core.logging.SafeLog
            .d("POS-CLIENT-PRICE", "removeClient -> reset to default price level (A)")
    }
    selectedClientState.value = null
    selectedClientSucursalState.value = null
    clientSucursalesState.value = emptyList()
    recalculateCartPricesForClient(null)
}

/** Recalcula los precios de los productos en el carrito según la lista de precios del cliente. */
fun CartRepository.recalculateCartPricesForClient(client: Client?) {
    val targetLabel = codTipoPrecioToLabel(client?.codTipoPrecio)
    runCatching {
        com.amaxonia.pos.core.logging.SafeLog.d(
            "POS-CLIENT-PRICE",
            "recalculateCartPricesForClient: client=${client?.code}, " +
                "codTipoPrecio=${client?.codTipoPrecio} -> targetLabel=$targetLabel, itemsCount=${cartItemsState.value.size}",
        )
    }
    cartItemsState.update { items ->
        items.map { item ->
            if (!item.isPromotionLine && !item.isManualPrice) {
                val (effectiveLabel, newPrice) = resolveItemPrice(item.product, item.itemUnitPackage, targetLabel)
                val levelDiscount =
                    item.product.prices
                        .firstOrNull { it.label.equals(effectiveLabel, ignoreCase = true) }
                        ?.discountPercent ?: 0.0
                val newDiscount = if (levelDiscount > 0.0 || item.discountPercent == 0.0) levelDiscount else item.discountPercent
                runCatching {
                    com.amaxonia.pos.core.logging.SafeLog.d(
                        "POS-CLIENT-PRICE",
                        "Item [${item.product.id}] ${item.product.description}: " +
                            "oldPrice=${item.unitPriceWithTax} (${item.selectedPriceLabel}) -> " +
                            "newPrice=$newPrice ($effectiveLabel), disc=$newDiscount",
                    )
                }
                item.copy(
                    selectedPriceLabel = effectiveLabel,
                    unitPriceWithTax = newPrice,
                    discountPercent = newDiscount,
                )
            } else {
                item
            }
        }
    }
    invalidateFinancialSnapshot()
}

/** Fija la sucursal seleccionada del cliente actual. */
fun CartRepository.setClientSucursal(sucursal: ClientBranch?) {
    selectedClientSucursalState.value = sucursal
}

/** Normaliza las sucursales disponibles y conserva/selecciona la vigente. */
fun CartRepository.setClientSucursales(sucursales: List<ClientBranch>) {
    val normalized = sucursales.distinctBy { it.sucursalId }
    clientSucursalesState.value = normalized

    val current =
        selectedClientSucursalState.value
            ?.takeIf { selected -> normalized.any { it.sucursalId == selected.sucursalId } }
    selectedClientSucursalState.value = current ?: normalized.singleOrNull()
}

/** Establece el contexto de vendedores disponible y el vendedor por defecto. */
fun CartRepository.setSellerContext(
    defaultSellerId: Int?,
    defaultSellerName: String?,
    sellers: List<Seller>,
) {
    val normalized =
        sellers
            .filter { it.id > 0 }
            .distinctBy { it.id }

    val fallback =
        defaultSellerId
            ?.takeIf { it > 0 }
            ?.let { id -> defaultSellerName?.takeIf(String::isNotBlank)?.let { Seller(id, it) } }

    val available =
        buildList {
            addAll(normalized)
            if (fallback != null && none { it.id == fallback.id }) {
                add(fallback)
            }
        }

    availableSellersState.value = available

    val selected =
        available.firstOrNull { it.id == defaultSellerId }
            ?: currentSellerState.value?.let { current -> available.firstOrNull { it.id == current.id } }
            ?: available.firstOrNull()

    if (selected != null) {
        applyCurrentSeller(selected)
    }
}

/** Cambia el vendedor actual y re-etiqueta los ítems del carrito. */
fun CartRepository.setCurrentSeller(seller: Seller) {
    applyCurrentSeller(seller)
}

private fun CartRepository.applyCurrentSeller(seller: Seller) {
    val changed = currentSellerState.value?.id != seller.id
    currentSellerState.value = seller
    if (!changed) return

    cartItemsState.update { items ->
        items.map { item -> item.copy(codVendedor = seller.id) }
    }
}
