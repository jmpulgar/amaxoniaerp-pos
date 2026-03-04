package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ClientDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ClientRepository

class OfflineFirstClientRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val clientDao: ClientDao,
    private val networkMonitor: NetworkMonitor
) : ClientRepository {

    override suspend fun getDefaultClient(): Result<Client> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))

        return runCatching {
            val dto = apiService.getDefaultClient(token)
            val client = dto.toDomain()
            clientDao.insertAll(listOf(dto.toEntity()))
            client
        }.recoverCatching { error ->
            val cached = clientDao.getPaged(limit = 1, offset = 0).firstOrNull()?.toDomain()
            cached ?: throw error
        }
    }

    override suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        if (!networkMonitor.isOnline()) {
            val cached = clientDao.getPaged(pageSize, offset).map { it.toDomain() }
            return if (cached.isNotEmpty()) Result.success(cached) else {
                Result.failure(IllegalStateException("No hay empresa seleccionada"))
            }
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getClients(token, limit = pageSize, offset = offset, search = null)
            clientDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = clientDao.getPaged(pageSize, offset).map { it.toDomain() }
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    override suspend fun getClientById(id: String): Result<Client> {
        val client = clientDao.getById(id)
        return if (client != null) Result.success(client.toDomain()) else {
            Result.failure(IllegalArgumentException("Cliente no encontrado"))
        }
    }

    override suspend fun searchClients(query: String): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        if (!networkMonitor.isOnline()) {
            val cached = clientDao.searchPaged(normalizeQuery(query), limit = 100, offset = 0).map { it.toDomain() }
            return Result.success(cached)
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getClients(token, limit = 100, offset = 0, search = query)
            clientDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = clientDao.searchPaged(normalizeQuery(query), limit = 100, offset = 0).map { it.toDomain() }
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    override suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        if (!networkMonitor.isOnline()) {
            val cached = clientDao.searchPaged(normalizeQuery(query), limit = pageSize, offset = offset)
            return Result.success(cached.map { it.toDomain() })
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getClients(token, limit = pageSize, offset = offset, search = query)
            clientDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = clientDao.searchPaged(normalizeQuery(query), limit = pageSize, offset = offset)
            val mapped = cached.map { it.toDomain() }
            if (mapped.isNotEmpty()) mapped else throw error
        }
    }

    override suspend fun saveClient(client: Client): Result<Unit> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        val request = client.toCreateRequest()
        return runCatching {
            val saved = if (client.id.isBlank()) {
                apiService.createClient(token, request)
            } else {
                apiService.updateClient(token, client.id, request)
            }
            clientDao.insertAll(listOf(saved.toEntity()))
        }
    }

    override suspend fun deleteClient(id: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Eliminar clientes no esta implementado"))
    }

    private fun normalizeQuery(query: String): String {
        val normalized = query.trim()
        return if (normalized.isEmpty()) "%" else "%$normalized%"
    }
}
