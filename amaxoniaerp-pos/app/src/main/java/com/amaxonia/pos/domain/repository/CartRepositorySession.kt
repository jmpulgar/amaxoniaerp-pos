package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.flow.update

// Contexto de sesión del carrito: mesa, cliente/sucursal y vendedor.
// Extensiones sobre CartRepository: mismo comportamiento que cuando eran miembros.

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
    selectedClientState.value = client
    selectedClientSucursalState.value = null
    clientSucursalesState.value = emptyList()
}

/** Quita el cliente y su sucursal de la transacción. */
fun CartRepository.removeClient() {
    selectedClientState.value = null
    selectedClientSucursalState.value = null
    clientSucursalesState.value = emptyList()
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
