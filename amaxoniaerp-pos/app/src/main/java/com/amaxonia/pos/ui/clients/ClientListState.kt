package com.amaxonia.pos.ui.clients

import com.amaxonia.pos.domain.model.Client

data class ClientListState(
    val clients: List<Client> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val endOfListReached: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)
