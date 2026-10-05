package com.amaxonia.erp.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientListState(
    val clients: List<Client> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 1,
    val endOfListReached: Boolean = false,
    val error: String? = null,
    val editingClient: Client? = null,
    val isFormOpen: Boolean = false,
)

class ClientListViewModel(
    private val clientRepository: ClientRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClientListState())
    val state: StateFlow<ClientListState> = _state.asStateFlow()
    private val pageSize = 20
    private var searchJob: Job? = null

    init {
        loadClients(reset = true)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            loadClients(reset = true)
        }
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.endOfListReached) return
        loadClients(reset = false)
    }

    fun retry() {
        loadClients(reset = true)
    }

    fun openCreateForm() {
        _state.update { it.copy(isFormOpen = true, editingClient = null) }
    }

    fun openEditForm(client: Client) {
        _state.update { it.copy(isFormOpen = true, editingClient = client) }
    }

    fun closeForm() {
        _state.update { it.copy(isFormOpen = false, editingClient = null) }
    }

    fun saveClient(client: Client) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = if (client.id.isNotBlank()) {
                clientRepository.updateClient(client.id, client)
            } else {
                clientRepository.createClient(client)
            }
            result.fold(
                onSuccess = {
                    closeForm()
                    loadClients(reset = true)
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Error al guardar cliente") }
                },
            )
        }
    }

    private fun loadClients(reset: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    page = if (reset) 1 else it.page + 1,
                    clients = if (reset) emptyList() else it.clients,
                    endOfListReached = if (reset) false else it.endOfListReached,
                    error = null,
                )
            }
            val query = _state.value.searchQuery.trim()
            val result = if (query.isEmpty()) {
                clientRepository.getAllClients(page = _state.value.page, pageSize = pageSize)
            } else {
                clientRepository.searchClients(query = query, page = _state.value.page, pageSize = pageSize)
            }
            result.fold(
                onSuccess = { newClients ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            clients = if (reset) newClients else it.clients + newClients,
                            endOfListReached = newClients.size < pageSize,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Error al cargar clientes",
                        )
                    }
                },
            )
        }
    }
}
