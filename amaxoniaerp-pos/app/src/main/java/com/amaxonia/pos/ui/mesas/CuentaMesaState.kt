package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa
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
    // Derivados de `cuentas` memoizados por instancia de estado: el filter y
    // el groupBy se evalúan UNA vez por emisión de estado, no en cada acceso
    // durante recomposición. Comportamiento idéntico (funciones puras).
    private val derivadas: CuentasDerivadas by lazy(LazyThreadSafetyMode.NONE) { CuentasDerivadas(cuentas) }

    val cuentasActivas: List<CuentaMesaResponse>
        get() = derivadas.activas

    val historicas: List<CuentaMesaResponse>
        get() = derivadas.historicas

    val reservadoPorPedido: Map<Int, Double>
        get() = derivadas.reservadoPorPedido

    fun disponible(pedido: PedidoMesa): Double =
        (
            pedido.cantidadPendiente - (reservadoPorPedido[pedido.id] ?: 0.0)
        ).coerceAtLeast(0.0)

    private class CuentasDerivadas(
        cuentas: List<CuentaMesaResponse>,
    ) {
        val activas = cuentas.filter { it.estado == EstadoCuentaMesa.ACTIVA }

        val historicas = cuentas.filterNot { it.estado == EstadoCuentaMesa.ACTIVA }

        val reservadoPorPedido =
            activas
                .flatMap { it.detalle }
                .groupBy { it.pedidoMesaId }
                .mapValues { (_, lines) -> lines.sumOf { it.cantidad } }
    }
}

sealed interface CuentaMesaEffect {
    data class Pay(
        val cuenta: CuentaMesaResponse,
    ) : CuentaMesaEffect
}
