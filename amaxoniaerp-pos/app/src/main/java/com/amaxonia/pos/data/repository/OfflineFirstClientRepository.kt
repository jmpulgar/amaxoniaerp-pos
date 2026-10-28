package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ClientDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.ClientQueryParams
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.createClient
import com.amaxonia.pos.data.remote.dto.CreateClientRequest
import com.amaxonia.pos.data.remote.getClients
import com.amaxonia.pos.data.remote.getDefaultClient
import com.amaxonia.pos.data.remote.updateClient
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import com.amaxonia.pos.domain.repository.ClientRepository

class OfflineFirstClientRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val clientDao: ClientDao,
    private val networkMonitor: NetworkMonitor,
    private val offlineScopeProvider: suspend () -> OfflineSyncScope = { OfflineSyncScope.ALL },
) : ClientRepository {
    override suspend fun getDefaultClient(sucursalId: Int?): Result<Client> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))

        return runCatching {
            val dto = apiService.getDefaultClient(token, sucursalId)
            val client = dto.toDomain()
            if (client.id.isBlank()) {
                error("Cliente por defecto sin ID válido")
            }
            val scope = offlineScopeProvider()
            if (scope.enabled) {
                clientDao.insertAll(listOf(dto.toEntity()))
            }
            client
        }.recoverCatching { error ->
            if (error.message?.contains("Default client not configured", ignoreCase = true) == true ||
                error.message?.contains("404") == true
            ) {
                throw error
            }
            val scope = offlineScopeProvider()
            val cached = getCachedClients(limit = 1, offset = 0, scope = scope).firstOrNull()
            if (cached != null && cached.id.isNotBlank()) {
                cached
            } else {
                throw error
            }
        }
    }

    override suspend fun getAllClients(
        page: Int,
        pageSize: Int,
    ): Result<List<Client>> = getAllClients(page, pageSize, sellerId = null, allowedClientIds = emptySet())

    override suspend fun getAllClients(
        page: Int,
        pageSize: Int,
        sellerId: Int?,
        allowedClientIds: Set<String>,
    ): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val scope = offlineScopeProvider()
        return when {
            !networkMonitor.isOnline() -> {
                val cached = getCachedClients(pageSize, offset, scope, allowedClientIds)
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(IllegalStateException("No hay empresa seleccionada"))
                }
            }
            token.isNullOrBlank() -> Result.failure(IllegalStateException("No hay empresa seleccionada"))
            else ->
                runCatching {
                    val branchIds = if (scope.clientFilterMode == ClientFilterMode.SUCURSAL) scope.branchIds.toList() else null
                    val zoneCodes = if (scope.clientFilterMode == ClientFilterMode.ZONE) scope.zoneCodes.toList() else null
                    val response =
                        apiService.getClients(
                            token = token,
                            params =
                                ClientQueryParams(
                                    limit = pageSize,
                                    offset = offset,
                                    search = null,
                                    branchIds = branchIds,
                                    sellerId = sellerId,
                                    zoneCodes = zoneCodes,
                                ),
                        )
                    if (scope.enabled) {
                        val entities = response.data.map { it.toEntity() }
                        val toCache =
                            when (scope.clientFilterMode) {
                                ClientFilterMode.ALL -> entities
                                ClientFilterMode.SUCURSAL -> entities.filter { it.idSucursal in scope.branchIds }
                                ClientFilterMode.ZONE -> entities.filter { it.codZona in scope.zoneCodes }
                            }
                        if (toCache.isNotEmpty()) clientDao.insertAll(toCache)
                    }
                    response.data.map { it.toDomain() }
                }.recoverCatching { error ->
                    val cached = getCachedClients(pageSize, offset, scope, allowedClientIds)
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
    ): Result<List<Client>> = searchClients(query, page, pageSize, sellerId = null, allowedClientIds = emptySet())

    override suspend fun searchClients(
        query: String,
        page: Int,
        pageSize: Int,
        sellerId: Int?,
        allowedClientIds: Set<String>,
    ): Result<List<Client>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val scope = offlineScopeProvider()
        return when {
            !networkMonitor.isOnline() -> {
                val cached = searchCachedClients(query, pageSize, offset, scope, allowedClientIds)
                Result.success(cached)
            }
            token.isNullOrBlank() -> Result.failure(IllegalStateException("No hay empresa seleccionada"))
            else ->
                runCatching {
                    val branchIds = if (scope.clientFilterMode == ClientFilterMode.SUCURSAL) scope.branchIds.toList() else null
                    val zoneCodes = if (scope.clientFilterMode == ClientFilterMode.ZONE) scope.zoneCodes.toList() else null
                    val response =
                        apiService.getClients(
                            token = token,
                            params =
                                ClientQueryParams(
                                    limit = pageSize,
                                    offset = offset,
                                    search = query,
                                    branchIds = branchIds,
                                    sellerId = sellerId,
                                    zoneCodes = zoneCodes,
                                ),
                        )
                    if (scope.enabled) {
                        val entities = response.data.map { it.toEntity() }
                        val toCache =
                            when (scope.clientFilterMode) {
                                ClientFilterMode.ALL -> entities
                                ClientFilterMode.SUCURSAL -> entities.filter { it.idSucursal in scope.branchIds }
                                ClientFilterMode.ZONE -> entities.filter { it.codZona in scope.zoneCodes }
                            }
                        if (toCache.isNotEmpty()) clientDao.insertAll(toCache)
                    }
                    response.data.map { it.toDomain() }
                }.recoverCatching { error ->
                    val cached = searchCachedClients(query, pageSize, offset, scope, allowedClientIds)
                    if (cached.isNotEmpty()) cached else throw error
                }
        }
    }

    private suspend fun getCachedClients(
        limit: Int,
        offset: Int,
        scope: OfflineSyncScope,
        allowedClientIds: Set<String> = emptySet(),
    ): List<Client> {
        val entities =
            when (scope.clientFilterMode) {
                ClientFilterMode.ALL -> clientDao.getPaged(limit, offset)
                ClientFilterMode.SUCURSAL -> clientDao.getPagedBySucursales(scope.branchIds.toList(), limit, offset)
                ClientFilterMode.ZONE -> clientDao.getPagedByZonas(scope.zoneCodes.toList(), limit, offset)
            }
        val filtered =
            if (allowedClientIds.isEmpty()) {
                entities
            } else {
                entities.filter { it.id in allowedClientIds || it.code in allowedClientIds }
            }
        return filtered.map { it.toDomain() }
    }

    private suspend fun searchCachedClients(
        query: String,
        limit: Int,
        offset: Int,
        scope: OfflineSyncScope,
        allowedClientIds: Set<String> = emptySet(),
    ): List<Client> {
        val normalized = normalizeQuery(query)
        val entities =
            when (scope.clientFilterMode) {
                ClientFilterMode.ALL ->
                    clientDao.searchPaged(normalized, limit = limit, offset = offset)
                ClientFilterMode.SUCURSAL ->
                    clientDao.searchPagedBySucursales(
                        normalized,
                        scope.branchIds.toList(),
                        limit = limit,
                        offset = offset,
                    )
                ClientFilterMode.ZONE ->
                    clientDao.searchPagedByZonas(
                        normalized,
                        scope.zoneCodes.toList(),
                        limit = limit,
                        offset = offset,
                    )
            }
        val filtered =
            if (allowedClientIds.isEmpty()) {
                entities
            } else {
                entities.filter { it.id in allowedClientIds || it.code in allowedClientIds }
            }
        return filtered.map { it.toDomain() }
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

    override suspend fun createQuickClient(
        name: String,
        sellerId: Int?,
    ): Result<Client> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        val request =
            CreateClientRequest(
                identification = "00000",
                name = name.trim().uppercase(),
                lastName = "",
                address = "PANAMA",
                phone = "",
                email = "",
                clientTypeId = 2,
                taxpayerTypeId = 1,
                countryId = 170,
                addressLevel1 = "",
                addressLevel2 = "",
                addressLevel3 = "",
                dv = "00",
                sellerId = sellerId,
            )
        return runCatching {
            val saved = apiService.createClient(token, request)
            if (saved.id.isNullOrBlank()) {
                error("No se pudo obtener el ID del cliente creado")
            }
            val scope = offlineScopeProvider()
            if (scope.enabled) {
                clientDao.insertAll(listOf(saved.toEntity()))
            }
            saved.toDomain()
        }
    }

    override suspend fun deleteClient(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Eliminar clientes no esta implementado"))

    private fun normalizeQuery(query: String): String {
        val normalized = query.trim()
        return if (normalized.isEmpty()) "%" else "%$normalized%"
    }
}
