package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.mesas.PedidoMesa

/**
 * Estado de la pantalla de comanda. Los pedidos se separan en dos vistas para cumplir los
 * requerimientos de la fase:
 *
 * - [pendientes]: líneas en estado PENDIENTE, todavía no enviadas a cocina. El usuario puede
 *   agregar productos (vía el carrito compartido) y luego disparar [pendientes] para enviarlos
 *   en bloque como una comanda.
 * - [enviados]: líneas en estado ENVIADA, EN_PREPARACION, LISTA o ENTREGADA (no CANCELADA
 *   salvo explícitamente listadas). Cada una tiene su propio estado visible para cocina/mozo.
 */
data class ComandaState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val pendientes: List<PedidoMesa> = emptyList(),
    val enviados: List<PedidoMesa> = emptyList(),
    val error: String? = null,
    val info: String? = null,
) {
    val hasPendientes: Boolean get() = pendientes.isNotEmpty()

    val hasEnviados: Boolean get() = enviados.isNotEmpty()
}
