package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Client
import kotlinx.coroutines.flow.Flow

interface ClientRepository {
    suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>>
    suspend fun getClientById(id: String): Result<Client>
    suspend fun getDefaultClient(): Result<Client>
    suspend fun searchClients(query: String): Result<List<Client>>
    suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> {
        return searchClients(query).map { clients ->
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= clients.size) emptyList() else clients.drop(startIndex).take(pageSize)
        }
    }
    suspend fun saveClient(client: Client): Result<Unit>
    suspend fun deleteClient(id: String): Result<Unit>
}
