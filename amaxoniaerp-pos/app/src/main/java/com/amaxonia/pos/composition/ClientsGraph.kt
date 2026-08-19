package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.clients.ClientFormViewModel
import com.amaxonia.pos.ui.clients.ClientListViewModel

/**
 * Grafo del feature clientes (TASK-051/052): listado, formulario y selección.
 * La selección de cliente reutiliza el ViewModel del listado.
 */
object ClientsGraph {
    fun clientListViewModel(): ClientListViewModel =
        ClientListViewModel(
            DependencyContainer.clientRepository,
            DependencyContainer.posConfigurationRepository,
            DependencyContainer.imageUrlResolver,
        )

    fun clientFormViewModel(): ClientFormViewModel =
        ClientFormViewModel(
            DependencyContainer.clientRepository,
            DependencyContainer.addressCatalogRepository,
            DependencyContainer.clientFormCatalogSource,
        )
}
