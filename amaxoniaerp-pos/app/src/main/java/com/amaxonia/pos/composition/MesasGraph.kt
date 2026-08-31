package com.amaxonia.pos.composition

import com.amaxonia.pos.domain.repository.SelectedTableHolder
import com.amaxonia.pos.domain.repository.TableAccountPayment
import com.amaxonia.pos.domain.repository.TableAccountPaymentHolder
import com.amaxonia.pos.ui.mesas.AreasMesasViewModel
import com.amaxonia.pos.ui.mesas.ComandaViewModel
import com.amaxonia.pos.ui.mesas.CuentaMesaViewModel

/**
 * Grafo del feature mesas (TASK-051/052): sesiones, comanda y cuenta. La
 * selección de mesa y la cuenta que viaja al pago viven en holders en memoria
 * expuestos aquí para que navegación y pantallas compartan el mismo estado.
 */
object MesasGraph {
    fun areasMesasViewModel(): AreasMesasViewModel =
        AreasMesasViewModel(
            DependencyContainer.areaRepository,
            DependencyContainer.cajaRepository,
            DependencyContainer.networkMonitor,
            DependencyContainer.selectedTableHolder,
            DependencyContainer.sesionMesaRepository,
        )

    fun comandaViewModel(
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): ComandaViewModel =
        ComandaViewModel(
            areaId = areaId,
            mesaId = mesaId,
            sesionId = sesionId,
            pedidosMesaRepository = DependencyContainer.pedidosMesaRepository,
            cartRepository = DependencyContainer.cartRepository,
            activeCajaReader = DependencyContainer.cajaRepository,
            connectivity = DependencyContainer.networkMonitor,
        )

    fun cuentaMesaViewModel(
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): CuentaMesaViewModel =
        CuentaMesaViewModel(
            areaId = areaId,
            mesaId = mesaId,
            sesionId = sesionId,
            cuentasRepository = DependencyContainer.cuentaMesaRepository,
            pedidosRepository = DependencyContainer.pedidosMesaRepository,
            activeCajaReader = DependencyContainer.cajaRepository,
        )

    val selectedTableHolder: SelectedTableHolder get() = DependencyContainer.selectedTableHolder

    val sesionMesaIdState get() = DependencyContainer.cartRepository.sesionMesaIdState

    val tableAccountPaymentHolder: TableAccountPaymentHolder get() = DependencyContainer.tableAccountPaymentHolder

    /** Selecciona la cuenta de mesa que atravesará el flujo de pago estándar. */
    fun selectTableAccountForPayment(payment: TableAccountPayment) {
        DependencyContainer.tableAccountPaymentHolder.select(payment)
    }

    /** Limpia mesa y carrito cuando la venta de mesa cerró la sesión. */
    fun clearTableAfterPaidSession() {
        DependencyContainer.selectedTableHolder.clear()
        DependencyContainer.cartRepository.clearCart()
    }
}
