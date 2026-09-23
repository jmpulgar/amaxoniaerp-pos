package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ClientDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.createClient
import com.amaxonia.pos.data.remote.getClients
import com.amaxonia.pos.data.remote.getDefaultClient
import com.amaxonia.pos.data.remote.updateClient
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ClientRepository

class OfflineFirstClientRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val clientDao: ClientDao,
    private val networkMonitor: NetworkMonitor,
    private val offlineScopeProvider: suspend () -> OfflineSyncScope = { OfflineSyncScope.ALL },
) : ClientRepository {
    override suspend fun getDefaultClient(): Result<Client> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))

        return runCatching {
            val dto = apiService.getDefaultClient(token)
            val client = dto.toDomain()
            val scope = offlineScopeProvider()
            if (scope.enabled) {
                clientDao.insertAll(listOf(dto.toEntity()))
            }
            client
        }.recoverCatching { error ->
            val scope = offlineScopeProvider()
            val cached = getCachedClients(limit = 1, offset = 0, scope = scope).firstOrNull()
            cached ?: throw error
        }
    }

    override suspend fun getAllClients(
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val scope = offlineScopeProvider()
        return when {
            !networkMonitor.isOnline() -> {
                val cached = getCachedClients(pageSize, offset, scope)
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(IllegalStateException("No hay empresa seleccionada"))
                }
            }
            token.isNullOrBlank() -> Result.failure(IllegalStateException("No hay empresa seleccionada"))
            else ->
                runCatching {
                    val branchIds = if (scope.allClients) null else scope.branchIds.toList()
                    val response = apiService.getClients(token, limit = pageSize, offset = offset, search = null, branchIds = branchIds)
                    if (scope.enabled) {
                        val entities = response.data.map { it.toEntity() }
                        val toCache = if (!scope.allClients) entities.filter { it.idSucursal in scope.branchIds } else entities
                        if (toCache.isNotEmpty()) clientDao.insertAll(toCache)
                    }
                    response.data.map { it.toDomain() }
                }.recoverCatching { error ->
                    val cached = getCachedClients(pageSize, offset, scope)
                    if (cached.isNotEmpty()) cached else throw error
                }
        }
    }

    override suspend fun getClientById(id: String): Result<Client> {
        val client = clientDao.getById(id)
        return if (client != null) {
            Result.success(client.toDomain())
        } else {
            Result.failure(IllegalArgumentException("Cliente no encontrado"))
        }
    }

    override suspend fun searchClients(query: String): Result<List<Client>> = searchClients(query, page = 1, pageSize = 100)

    override suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val scope = offlineScopeProvider()
        return when {
            !networkMonitor.isOnline() -> {
                val cached = searchCachedClients(query, pageSize, offset, scope)
                Result.success(cached)
            }
            token.isNullOrBlank() -> Result.failure(IllegalStateException("No hay empresa seleccionada"))
            else ->
                runCatching {
                    val branchIds = if (scope.allClients) null else scope.branchIds.toList()
                    val response = apiService.getClients(token, limit = pageSize, offset = offset, search = query, branchIds = branchIds)
                    if (scope.enabled) {
                        val entities = response.data.map { it.toEntity() }
                        val toCache = if (!scope.allClients) entities.filter { it.idSucursal in scope.branchIds } else entities
                        if (toCache.isNotEmpty()) clientDao.insertAll(toCache)
                    }
                    response.data.map { it.toDomain() }
                }.recoverCatching { error ->
                    val cached = searchCachedClients(query, pageSize, offset, scope)
                    if (cached.isNotEmpty()) cached else throw error
                }
        }
    }

    private suspend fun getCachedClients(
        limit: Int,
        offset: Int,
        scope: OfflineSyncScope,
    ): List<Client> {
        val entities =
            if (scope.allClients) {
                clientDao.getPaged(limit, offset)
            } else {
                clientDao.getPagedBySucursales(scope.branchIds.toList(), limit, offset)
            }
        return entities.map { it.toDomain() }
    }

    private suspend fun searchCachedClients(
        query: String,
        limit: Int,
        offset: Int,
        scope: OfflineSyncScope,
    ): List<Client> {
        val normalized = normalizeQuery(query)
        val entities =
            if (scope.allClients) {
                clientDao.searchPaged(normalized, limit = limit, offset = offset)
            } else {
                clientDao.searchPagedBySucursales(normalized, scope.branchIds.toList(), limit = limit, offset = offset)
            }
        return entities.map { it.toDomain() }
    }

    override suspend fun saveClient(client: Client): Result<Unit> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        val request = client.toCreateRequest()
        return runCatching {
            val saved =
                if (client.id.isBlank()) {
                    apiService.createClient(token, request)
                } else {
                    apiService.updateClient(token, client.id, request)
                }
            val scope = offlineScopeProvider()
            if (scope.enabled) {
                clientDao.insertAll(listOf(saved.toEntity()))
            }
        }
    }

    override suspend fun deleteClient(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Eliminar clientes no esta implementado"))

    private fun normalizeQuery(query: String): String {
        val normalized = query.trim()
        return if (normalized.isEmpty()) "%" else "%$normalized%"
    }
}
