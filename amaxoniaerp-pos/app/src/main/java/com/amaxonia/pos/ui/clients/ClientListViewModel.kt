package com.amaxonia.pos.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductSessionReader
import com.amaxonia.pos.domain.repository.appendClientToCurrentSeller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClientListViewModel(
    private val clientRepository: ClientRepository,
    private val sessionReader: ProductSessionReader,
    private val imageUrlResolver: ImageUrlResolver,
    private val cartRepository: CartRepository? = null,
) : ViewModel() {
    @Volatile
    private var adminDb: String = ""

    private val _state = MutableStateFlow(ClientListState())
    val state: StateFlow<ClientListState> = _state.asStateFlow()
    private val pageSize = 20

    init {
        viewModelScope.launch {
            adminDb = sessionReader.currentAdminDatabase()
        }
        observeSeller()
        loadClients(reset = true)
    }

    private fun observeSeller() {
        val repo = cartRepository ?: return
        viewModelScope.launch {
            repo.currentSeller.collect { seller ->
                val canCreate = seller?.canCreateClients() ?: true
                _state.update { it.copy(canCreateClients = canCreate) }
            }
        }
    }

    fun getClientPhotoUrl(client: Client): String {
        val filename = client.photoFilename.takeIf { it.isNotBlank() }
        return if (client.id.isBlank() || adminDb.isBlank() || filename == null) {
            ""
        } else {
            imageUrlResolver.client(adminDb, client.id, filename)
        }
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

    fun clearQuickClientError() {
        _state.update { it.copy(quickClientError = null) }
    }

    fun createQuickClient(
        name: String,
        onSuccess: (Client) -> Unit,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(quickClientError = "El nombre es obligatorio") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreatingQuickClient = true, quickClientError = null) }
            val seller = cartRepository?.currentSeller?.value
            val sellerId = seller?.id
            clientRepository.createQuickClient(trimmed, sellerId).fold(
                onSuccess = { created ->
                    cartRepository?.appendClientToCurrentSeller(created.id)
                    _state.update { current ->
                        current.copy(
                            isCreatingQuickClient = false,
                            quickClientError = null,
                            clients = listOf(created) + current.clients.filterNot { it.id == created.id },
                        )
                    }
                    onSuccess(created)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCreatingQuickClient = false,
                            quickClientError = error.message ?: "Error al crear cliente",
                        )
                    }
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
            val seller = cartRepository?.currentSeller?.value
            val sellerId = seller?.id
            val allowedClientIds = seller?.allowedClientIds().orEmpty()

            val result =
                if (query.isEmpty()) {
                    clientRepository.getAllClients(_state.value.page, pageSize, sellerId, allowedClientIds)
                } else {
                    clientRepository.searchClients(query, _state.value.page, pageSize, sellerId, allowedClientIds)
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
