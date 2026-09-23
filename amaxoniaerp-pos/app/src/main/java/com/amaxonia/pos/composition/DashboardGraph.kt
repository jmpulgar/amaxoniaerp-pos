package com.amaxonia.pos.composition

import com.amaxonia.pos.data.local.clearActiveCaja
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.usecase.cart.ResolveClientBranchesUseCase
import com.amaxonia.pos.ui.dashboard.DashboardCajaCoordinator
import com.amaxonia.pos.ui.dashboard.DashboardCartCoordinator
import com.amaxonia.pos.ui.dashboard.DashboardCatalogCoordinator
import com.amaxonia.pos.ui.dashboard.DashboardProductMapper
import com.amaxonia.pos.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Grafo del feature dashboard (TASK-051/052): construcción del ViewModel y
 * eventos one-shot de apertura/selector de caja que cruzan pantallas.
 */
object DashboardGraph {
    fun dashboardViewModel(): DashboardViewModel {
        val cajaCoordinator =
            DashboardCajaCoordinator(
                DependencyContainer.cajaRepository,
                DependencyContainer.cartRepository,
                DependencyContainer.cashClosePrintingService,
                DependencyContainer.cashCloseTicketPayloadBuilder,
                DependencyContainer.networkMonitor,
            )
        return DashboardViewModel(
            catalogCoordinator =
                DashboardCatalogCoordinator(
                    DependencyContainer.productRepository,
                    DependencyContainer.reportRepository,
                    DependencyContainer.posConfigurationRepository,
                    DependencyContainer.serverEnvironment,
                    DashboardProductMapper(DependencyContainer.imageUrlResolver),
                    DependencyContainer.offlineSyncSettingsStore.scopeSelectionFlow,
                ),
            cartCoordinator =
                DashboardCartCoordinator(
                    DependencyContainer.promotionRepository,
                    DependencyContainer.cartRepository,
                    ResolveClientBranchesUseCase(
                        DependencyContainer.posConfigurationRepository,
                        DependencyContainer.clientBranchRepository,
                    ),
                    DependencyContainer.refreshCartProductLotsUseCase,
                    cajaCoordinator,
                    DependencyContainer.appClock,
                ),
            cajaCoordinator = cajaCoordinator,
        )
    }

    /** Limpia la sesión local al cerrar sesión desde el drawer. */
    suspend fun logout() {
        DependencyContainer.cartRepository.clearCart()
        DependencyContainer.cajaRepository.clearActiveCaja()
        DependencyContainer.authRepository.logout()
    }

    /** Evento one-shot: pedir al Dashboard que abra el diálogo de apertura de caja. */
    fun requestAperturaOnDashboard() = DependencyContainer.requestAperturaOnDashboard()

    /** Evento one-shot: pedir al Dashboard que abra el selector de caja. */
    fun requestCajaSelectorOnDashboard() = DependencyContainer.requestCajaSelectorOnDashboard()

    val pendingAperturaRequest: StateFlow<Boolean> get() = DependencyContainer.pendingAperturaRequest

    fun consumeAperturaRequest() = DependencyContainer.consumeAperturaRequest()

    val pendingCajaSelectorRequest: StateFlow<Boolean> get() = DependencyContainer.pendingCajaSelectorRequest

    fun consumeCajaSelectorRequest() = DependencyContainer.consumeCajaSelectorRequest()

    /** Carrito activo: la navegación lo lee/limpia en callbacks globales. */
    val cartRepository: CartRepository get() = DependencyContainer.cartRepository
}
