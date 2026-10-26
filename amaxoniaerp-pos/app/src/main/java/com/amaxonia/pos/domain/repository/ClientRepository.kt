package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client

interface ClientRepository {
    suspend fun getAllClients(
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> = getAllClients(page, pageSize, sellerId = null, allowedClientIds = emptySet())

    suspend fun getAllClients(
        page: Int,
        pageSize: Int,
        sellerId: Int?,
        allowedClientIds: Set<String> = emptySet(),
    ): Result<List<Client>> = getAllClients(page, pageSize)

    suspend fun getClientById(id: String): Result<Client>

    suspend fun getDefaultClient(): Result<Client>

    suspend fun searchClients(query: String): Result<List<Client>>

    suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> = searchClients(query, page, pageSize, sellerId = null, allowedClientIds = emptySet())

    suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
        sellerId: Int?,
        allowedClientIds: Set<String> = emptySet(),
    ): Result<List<Client>> =
        searchClients(query).map { clients ->
            val filtered =
                if (allowedClientIds.isEmpty()) {
                    clients
                } else {
                    clients.filter { it.id in allowedClientIds || it.code in allowedClientIds }
                }
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= filtered.size) emptyList() else filtered.drop(startIndex).take(pageSize)
        }

    suspend fun saveClient(client: Client): Result<Unit>

    suspend fun createQuickClient(
        name: String,
        sellerId: Int?,
    ): Result<Client> = Result.failure(UnsupportedOperationException("createQuickClient not implemented"))

    suspend fun deleteClient(id: String): Result<Unit>
}
