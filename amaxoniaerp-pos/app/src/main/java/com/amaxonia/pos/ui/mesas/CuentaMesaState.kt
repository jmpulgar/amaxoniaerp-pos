package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesa

data class CuentaMesaState(
    val pedidos: List<PedidoMesa> = emptyList(),
    val cuentas: List<CuentaMesaResponse> = emptyList(),
    val cantidades: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val cuentasActivas: List<CuentaMesaResponse>
        get() = cuentas.filter { it.estado == com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa.ACTIVA }

    val historicas: List<CuentaMesaResponse>
        get() = cuentas.filterNot { it.estado == com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa.ACTIVA }

    val reservadoPorPedido: Map<Int, Double>
        get() =
            cuentasActivas
                .flatMap { it.detalle }
                .groupBy { it.pedidoMesaId }
                .mapValues { (_, lines) -> lines.sumOf { it.cantidad } }

    fun disponible(pedido: PedidoMesa): Double =
        (
            pedido.cantidadPendiente - (reservadoPorPedido[pedido.id] ?: 0.0)
        ).coerceAtLeast(0.0)
}

sealed interface CuentaMesaEffect {
    data class Pay(
        val cuenta: CuentaMesaResponse,
    ) : CuentaMesaEffect
}
