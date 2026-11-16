package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.usecase.cobranza.SubmitCobranzaUseCase
import com.amaxonia.pos.domain.usecase.pedidos.LoadPedidoIntoCartUseCase
import com.amaxonia.pos.ui.cobranza.CobranzaViewModel
import com.amaxonia.pos.ui.pedidos.PedidosViewModel

/** Grafo de pedidos del ERP (listado + facturación vía carrito). */
object PedidosGraph {
    fun pedidosViewModel(): PedidosViewModel =
        PedidosViewModel(
            pedidoRepository = DependencyContainer.pedidoRepository,
            loadPedidoIntoCart =
                LoadPedidoIntoCartUseCase(
                    pedidoRepository = DependencyContainer.pedidoRepository,
                    productRepository = DependencyContainer.productRepository,
                    clientRepository = DependencyContainer.clientRepository,
                    cart = DependencyContainer.cartRepository,
                ),
        )
}

/** Grafo de cobranza de facturas pendientes a clientes. */
object CobranzaGraph {
    fun cobranzaViewModel(): CobranzaViewModel =
        CobranzaViewModel(
            cobranzaRepository = DependencyContainer.cobranzaRepository,
            formaPagoRepository = DependencyContainer.formaPagoRepository,
            cajaContext = DependencyContainer.activeCajaContext,
            submitCobranza =
                SubmitCobranzaUseCase(
                    repository = DependencyContainer.cobranzaRepository,
                    cajaContext = DependencyContainer.activeCajaContext,
                ),
        )
}
