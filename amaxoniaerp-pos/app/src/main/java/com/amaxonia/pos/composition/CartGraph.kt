package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.usecase.cart.ResolveClientImageUrlUseCase
import com.amaxonia.pos.ui.cart.CartActionHandler
import com.amaxonia.pos.ui.cart.CartConfigurationCoordinator
import com.amaxonia.pos.ui.cart.CartStateCoordinator
import com.amaxonia.pos.ui.cart.CartViewModel

/**
 * Grafo del feature carrito (TASK-051/052): la misma construcción se usa en
 * `CartScreen` y en la comanda de mesas (vía navegación).
 */
object CartGraph {
    fun cartViewModel(): CartViewModel =
        CartViewModel(
            stateCoordinator =
                CartStateCoordinator(
                    DependencyContainer.cartRepository,
                    DependencyContainer.clientRepository,
                    DependencyContainer.posConfigurationRepository,
                    DependencyContainer.clientBranchRepository,
                    ResolveClientImageUrlUseCase(
                        DependencyContainer.posConfigurationRepository,
                        DependencyContainer.imageUrlResolver,
                    ),
                    DependencyContainer.selectedTableHolder,
                ),
            configurationCoordinator =
                CartConfigurationCoordinator(
                    DependencyContainer.posConfigurationRepository,
                    DependencyContainer.cajaRepository,
                ),
            actionHandler =
                CartActionHandler(
                    DependencyContainer.cartRepository,
                    DependencyContainer.refreshCartProductLotsUseCase,
                    DependencyContainer.saveDraftInvoiceUseCase,
                ),
        )
}
