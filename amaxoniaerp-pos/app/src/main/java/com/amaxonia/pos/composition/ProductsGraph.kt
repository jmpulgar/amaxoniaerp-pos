package com.amaxonia.pos.composition

import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.products.ProductFormViewModel
import com.amaxonia.pos.ui.products.ProductListViewModel

/** Grafo del feature productos (TASK-051/052): listado y formulario. */
object ProductsGraph {
    fun productListViewModel(): ProductListViewModel =
        ProductListViewModel(
            DependencyContainer.productRepository,
            DependencyContainer.posConfigurationRepository,
            DependencyContainer.imageUrlResolver,
        )

    fun productFormViewModel(): ProductFormViewModel = ProductFormViewModel(DependencyContainer.productRepository)
}
