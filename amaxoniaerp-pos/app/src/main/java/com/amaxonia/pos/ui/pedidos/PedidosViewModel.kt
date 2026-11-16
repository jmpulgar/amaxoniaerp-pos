package com.amaxonia.pos.ui.pedidos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailDto
import com.amaxonia.pos.domain.model.pedidos.PedidoSummaryDto
import com.amaxonia.pos.domain.repository.PedidoRepository
import com.amaxonia.pos.domain.usecase.pedidos.LoadPedidoIntoCartUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PedidosState(
    val isLoading: Boolean = true,
    val search: String = "",
    val pedidos: List<PedidoSummaryDto> = emptyList(),
    val expandedId: String? = null,
    val details: Map<String, PedidoDetailDto> = emptyMap(),
    val loadingDetailId: String? = null,
    val invoicingId: String? = null,
    val error: String? = null,
)

sealed interface PedidosAction {
    data object Refresh : PedidosAction

    data class Search(
        val query: String,
    ) : PedidosAction

    data class ToggleDetail(
        val idPedido: String,
    ) : PedidosAction

    data class Facturar(
        val pedido: PedidoSummaryDto,
    ) : PedidosAction

    data object DismissError : PedidosAction
}

sealed interface PedidosEffect {
    /** El pedido quedó cargado en el carrito: continuar al cobro. */
    data object OpenCart : PedidosEffect
}

/** Listado de pedidos pendientes del ERP y carga de uno al carrito para facturarlo. */
class PedidosViewModel(
    private val pedidoRepository: PedidoRepository,
    private val loadPedidoIntoCart: LoadPedidoIntoCartUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PedidosState())
    val state: StateFlow<PedidosState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<PedidosEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PedidosEffect> = mutableEffects.asSharedFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun onAction(action: PedidosAction) {
        when (action) {
            PedidosAction.Refresh -> load()
            is PedidosAction.Search -> search(action.query)
            is PedidosAction.ToggleDetail -> toggleDetail(action.idPedido)
            is PedidosAction.Facturar -> facturar(action.pedido)
            PedidosAction.DismissError -> mutableState.update { it.copy(error = null) }
        }
    }

    private fun search(query: String) {
        mutableState.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                fetch()
            }
    }

    private fun load() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        mutableState.update { it.copy(isLoading = true, error = null) }
        pedidoRepository
            .pendientes(
                mutableState.value.search
                    .trim()
                    .ifBlank { null },
            ).onSuccess { pedidos -> mutableState.update { it.copy(isLoading = false, pedidos = pedidos) } }
            .onFailure { error ->
                mutableState.update {
                    it.copy(isLoading = false, error = error.message ?: "No se pudieron cargar los pedidos")
                }
            }
    }

    private fun toggleDetail(idPedido: String) {
        val current = mutableState.value
        if (current.expandedId == idPedido) {
            mutableState.update { it.copy(expandedId = null) }
            return
        }
        mutableState.update { it.copy(expandedId = idPedido) }
        if (idPedido in current.details) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingDetailId = idPedido) }
            pedidoRepository
                .detail(idPedido)
                .onSuccess { detail ->
                    mutableState.update { it.copy(loadingDetailId = null, details = it.details + (idPedido to detail)) }
                }.onFailure { error ->
                    mutableState.update { it.copy(loadingDetailId = null, error = error.message ?: "No se pudo cargar el pedido") }
                }
        }
    }

    private fun facturar(pedido: PedidoSummaryDto) {
        if (mutableState.value.invoicingId != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(invoicingId = pedido.idPedido, error = null) }
            loadPedidoIntoCart(pedido.idPedido)
                .onSuccess {
                    mutableState.update { it.copy(invoicingId = null) }
                    mutableEffects.tryEmit(PedidosEffect.OpenCart)
                }.onFailure { error ->
                    mutableState.update {
                        it.copy(invoicingId = null, error = error.message ?: "No se pudo cargar el pedido en el carrito")
                    }
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
