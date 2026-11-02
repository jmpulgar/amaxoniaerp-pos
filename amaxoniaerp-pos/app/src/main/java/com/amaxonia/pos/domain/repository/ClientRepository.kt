package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client

interface ClientRepository {
    suspend fun getAllClients(
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> = getAllClients(page, pageSize, sellerId = null)

    suspend fun getAllClients(
        page: Int,
        pageSize: Int,
        sellerId: Int?,
    ): Result<List<Client>> = getAllClients(page, pageSize)

    suspend fun getClientById(id: String): Result<Client>

    suspend fun getDefaultClient(sucursalId: Int? = null): Result<Client>

    suspend fun searchClients(query: String): Result<List<Client>>

    suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> = searchClients(query, page, pageSize, sellerId = null)

    suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
        sellerId: Int?,
    ): Result<List<Client>> =
        searchClients(query).map { clients ->
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= clients.size) emptyList() else clients.drop(startIndex).take(pageSize)
        }

    suspend fun saveClient(client: Client): Result<Unit>

    suspend fun createQuickClient(
        name: String,
        sellerId: Int?,
    ): Result<Client> = Result.failure(UnsupportedOperationException("createQuickClient not implemented"))

    suspend fun deleteClient(id: String): Result<Unit>
}
