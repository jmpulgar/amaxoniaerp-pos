package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.ClientDao
import com.amaxonia.erp.data.local.db.ClientEntity
import com.amaxonia.erp.data.local.db.ClientSucursalDao
import com.amaxonia.erp.data.local.db.ClientSucursalEntity
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.createClient
import com.amaxonia.erp.data.remote.dto.ClientDto
import com.amaxonia.erp.data.remote.dto.ClientSucursalDto
import com.amaxonia.erp.data.remote.dto.CreateClientRequest
import com.amaxonia.erp.data.remote.getClientSucursales
import com.amaxonia.erp.data.remote.getClients
import com.amaxonia.erp.data.remote.updateClient
import com.amaxonia.erp.data.sync.OfflineSyncScope
import com.amaxonia.erp.data.sync.OfflineSyncSettingsStore
import com.amaxonia.erp.data.sync.toDomain
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.TaxpayerType
import com.amaxonia.erp.domain.repository.ClientRepository

class ClientRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val clientDao: ClientDao? = null,
    private val clientSucursalDao: ClientSucursalDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val scopeStore: OfflineSyncSettingsStore? = null,
) : ClientRepository {

    private suspend fun requireToken(): String {
        return localStore.readCompanySession()?.token
            ?: error("No hay sesión de empresa activa")
    }

    private suspend fun currentScope(): OfflineSyncScope =
        runCatching { scopeStore?.load() }.getOrNull() ?: OfflineSyncScope.ALL

    override suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>> {
        val scope = currentScope()
        val isOnline = networkMonitor?.isOnline() ?: true
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        if (!isOnline && clientDao != null) {
            val entities = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                clientDao.getPagedBySucursales(scope.branchIds.toList(), pageSize, offset)
            } else {
                clientDao.getPaged(pageSize, offset)
            }
            return Result.success(entities.map { it.toDomain() })
        }

        return runCatching {
            val token = requireToken()
            val response = apiService.getClients(token = token, limit = pageSize, offset = offset)
            if (scope.enabled && clientDao != null) {
                val entities = response.data.map { it.toEntity() }
                val toCache = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                    entities.filter { it.idSucursal in scope.branchIds }
                } else {
                    entities
                }
                if (toCache.isNotEmpty()) {
                    runCatching { clientDao?.insertAll(toCache) }
                }
            }
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            if (clientDao != null) {
                val entities = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                    clientDao.getPagedBySucursales(scope.branchIds.toList(), pageSize, offset)
                } else {
                    clientDao.getPaged(pageSize, offset)
                }
                if (entities.isNotEmpty()) {
                    entities.map { it.toDomain() }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    override suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> {
        val scope = currentScope()
        val isOnline = networkMonitor?.isOnline() ?: true
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        if (!isOnline && clientDao != null) {
            val entities = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                clientDao.searchPagedBySucursales(query, scope.branchIds.toList(), pageSize, offset)
            } else {
                clientDao.searchPaged(query, pageSize, offset)
            }
            return Result.success(entities.map { it.toDomain() })
        }

        return runCatching {
            val token = requireToken()
            val response = apiService.getClients(token = token, limit = pageSize, offset = offset, search = query)
            if (scope.enabled && clientDao != null) {
                val entities = response.data.map { it.toEntity() }
                val toCache = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                    entities.filter { it.idSucursal in scope.branchIds }
                } else {
                    entities
                }
                if (toCache.isNotEmpty()) {
                    runCatching { clientDao?.insertAll(toCache) }
                }
            }
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            if (clientDao != null) {
                val entities = if (!scope.allClients && scope.branchIds.isNotEmpty()) {
                    clientDao.searchPagedBySucursales(query, scope.branchIds.toList(), pageSize, offset)
                } else {
                    clientDao.searchPaged(query, pageSize, offset)
                }
                if (entities.isNotEmpty()) {
                    entities.map { it.toDomain() }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    override suspend fun createClient(client: Client): Result<Client> =
        runCatching {
            val token = requireToken()
            val request = CreateClientRequest(
                identification = client.identification,
                name = client.name,
                lastName = client.lastName,
                address = client.address,
                phone = client.phone,
                email = client.email,
                clientTypeId = client.clientTypeId,
                taxpayerTypeId = if (client.taxpayerType == TaxpayerType.JURIDICO) 2 else 1,
            )
            val response = apiService.createClient(token, request)
            if (clientDao != null) {
                runCatching { clientDao?.insertAll(listOf(response.toEntity())) }
            }
            response.toDomain()
        }

    override suspend fun updateClient(id: String, client: Client): Result<Client> =
        runCatching {
            val token = requireToken()
            val request = CreateClientRequest(
                identification = client.identification,
                name = client.name,
                lastName = client.lastName,
                address = client.address,
                phone = client.phone,
                email = client.email,
                clientTypeId = client.clientTypeId,
                taxpayerTypeId = if (client.taxpayerType == TaxpayerType.JURIDICO) 2 else 1,
            )
            val response = apiService.updateClient(token, id, request)
            if (clientDao != null) {
                runCatching { clientDao?.insertAll(listOf(response.toEntity())) }
            }
            response.toDomain()
        }

    override suspend fun getClientSucursales(clientId: String): Result<List<ClientBranch>> =
        runCatching {
            if (clientId.isBlank() || clientId == "0" || clientId.equals("CF", ignoreCase = true)) {
                return@runCatching emptyList()
            }
            val isOnline = networkMonitor?.isOnline() ?: true
            if (!isOnline && clientSucursalDao != null) {
                val branches = clientSucursalDao.getByClientCode(clientId)
                return@runCatching branches.map { it.toDomain() }
            }
            val token = requireToken()
            val response = apiService.getClientSucursales(token, clientId)
            if (clientSucursalDao != null) {
                runCatching { clientSucursalDao?.insertAll(response.map { it.toEntity() }) }
            }
            response.map { it.toDomain() }
        }.recoverCatching { error ->
            if (clientSucursalDao != null) {
                val branches = clientSucursalDao.getByClientCode(clientId)
                if (branches.isNotEmpty()) {
                    branches.map { it.toDomain() }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
}

private fun ClientDto.toDomain(): Client =
    Client(
        id = id ?: code ?: "",
        code = code ?: "",
        identification = identification ?: "",
        dv = dv ?: "",
        name = name ?: "",
        lastName = lastName ?: "",
        email = email ?: "",
        phone = phone ?: "",
        address = address ?: "",
        status = status ?: true,
        taxpayerType = if (taxpayerTypeId == 2) TaxpayerType.JURIDICO else TaxpayerType.NATURAL,
        clientTypeId = clientTypeId ?: 1,
        permiteCredito = permiteCredito,
        diasCredito = diasCredito,
    )

private fun ClientDto.toEntity(): ClientEntity =
    ClientEntity(
        id = id ?: code ?: "",
        code = code ?: "",
        identification = identification ?: "",
        dv = dv ?: "0",
        name = name ?: "",
        lastName = lastName ?: "",
        address = address ?: "",
        phone = phone ?: "",
        email = email ?: "",
        status = status ?: true,
        clientTypeId = clientTypeId ?: 1,
        taxpayerTypeId = taxpayerTypeId ?: 1,
        countryId = countryId ?: 170,
        addressLevel1 = "",
        addressLevel2 = "",
        addressLevel3 = "",
        permiteCredito = permiteCredito,
        diasCredito = diasCredito,
        codTipoPrecio = codTipoPrecio ?: 2,
        idSucursal = null,
    )

private fun ClientSucursalDto.toDomain(): ClientBranch =
    ClientBranch(
        sucursalId = sucursalId,
        clienteCodigo = clienteCodigo,
        nombreSucursal = nombreSucursal,
        nombreContacto = nombreContacto ?: "",
        telefonoContacto = telefonoContacto ?: "",
        correoContacto = correoContacto ?: "",
        direccion = direccion ?: "",
        observaciones = observaciones ?: "",
    )

private fun ClientSucursalDto.toEntity(): ClientSucursalEntity =
    ClientSucursalEntity(
        sucursalId = sucursalId,
        clienteCodigo = clienteCodigo,
        nombreSucursal = nombreSucursal,
        nombreContacto = nombreContacto ?: "",
        telefonoContacto = telefonoContacto ?: "",
        correoContacto = correoContacto ?: "",
        direccion = direccion ?: "",
        observaciones = observaciones ?: "",
    )
