package com.amaxonia.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val catalogCoordinator: DashboardCatalogCoordinator,
    private val cartCoordinator: DashboardCartCoordinator,
    private val cajaCoordinator: DashboardCajaCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<DashboardUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<DashboardUiEffect> = mutableEffects.asSharedFlow()

    init {
        catalogCoordinator.start(viewModelScope, mutableState)
        cartCoordinator.start(viewModelScope, mutableState)
        cajaCoordinator.start(viewModelScope, mutableState)
    }

    fun onAction(action: DashboardUiAction) {
        when (action) {
            is DashboardCatalogUiAction -> catalogCoordinator.onAction(action, viewModelScope, mutableState)
            is DashboardCajaUiAction -> cajaCoordinator.onAction(action, viewModelScope, mutableState)
            DashboardSaleUiAction.Checkout -> {
                if (cajaCoordinator.canProceed(viewModelScope, mutableState)) {
                    mutableEffects.tryEmit(DashboardUiEffect.NavigateToCart)
                }
            }
            is DashboardSaleUiAction -> cartCoordinator.onAction(action, viewModelScope, mutableState)
        }
    }
}
