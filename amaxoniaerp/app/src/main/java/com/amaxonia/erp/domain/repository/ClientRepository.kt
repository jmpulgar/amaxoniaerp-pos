package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch

interface ClientRepository {
    suspend fun getAllClients(page: Int = 1, pageSize: Int = 20): Result<List<Client>>
    suspend fun searchClients(query: String, page: Int = 1, pageSize: Int = 20): Result<List<Client>>
    suspend fun createClient(client: Client): Result<Client>
    suspend fun updateClient(id: String, client: Client): Result<Client>
    suspend fun getClientSucursales(clientId: String): Result<List<ClientBranch>>
}

