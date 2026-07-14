package com.amaxonia.pos.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductSessionReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClientListViewModel(
    private val clientRepository: ClientRepository,
    private val sessionReader: ProductSessionReader,
    private val imageUrlResolver: ImageUrlResolver,
) : ViewModel() {
    @Volatile
    private var adminDb: String = ""

    init {
        viewModelScope.launch {
            adminDb = sessionReader.currentAdminDatabase()
        }
    }

    fun getClientPhotoUrl(client: Client): String {
        if (client.id.isBlank() || adminDb.isBlank()) return ""
        val filename =
            client.photoFilename.takeIf { it.isNotBlank() }
                ?: return ""
        return imageUrlResolver.client(adminDb, client.id, filename)
    }

    private val _state = MutableStateFlow(ClientListState())
    val state: StateFlow<ClientListState> = _state.asStateFlow()
    private val pageSize = 20

    init {
        loadClients(reset = true)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
        loadClients(reset = true)
    }

    fun loadMoreClients() {
        if (_state.value.isLoading || _state.value.endOfListReached) return
        loadClients(reset = false)
    }

    fun retry() {
        loadClients(reset = true)
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
            val result =
                if (query.isEmpty()) {
                    clientRepository.getAllClients(_state.value.page, pageSize)
                } else {
                    clientRepository.searchClients(query, _state.value.page, pageSize)
                }
            result.fold(
                onSuccess = { clients ->
                    val newClients = if (reset) clients else _state.value.clients + clients
                    _state.update {
                        it.copy(
                            isLoading = false,
                            clients = newClients,
                            endOfListReached = clients.size < pageSize,
                            error = null,
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Error al cargar clientes",
                        )
                    }
                },
            )
        }
    }
}
