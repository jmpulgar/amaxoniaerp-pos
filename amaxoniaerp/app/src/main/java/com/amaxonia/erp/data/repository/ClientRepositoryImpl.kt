package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.createClient
import com.amaxonia.erp.data.remote.getClientSucursales
import com.amaxonia.erp.data.remote.dto.ClientDto
import com.amaxonia.erp.data.remote.dto.ClientSucursalDto
import com.amaxonia.erp.data.remote.dto.CreateClientRequest
import com.amaxonia.erp.data.remote.getClients
import com.amaxonia.erp.data.remote.updateClient
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.TaxpayerType
import com.amaxonia.erp.domain.repository.ClientRepository

class ClientRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : ClientRepository {

    private suspend fun requireToken(): String {
        return localStore.readCompanySession()?.token
            ?: error("No hay sesión de empresa activa")
    }

    override suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>> =
        runCatching {
            val token = requireToken()
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val response = apiService.getClients(token = token, limit = pageSize, offset = offset)
            response.data.map { it.toDomain() }
        }

    override suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> =
        runCatching {
            val token = requireToken()
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val response = apiService.getClients(token = token, limit = pageSize, offset = offset, search = query)
            response.data.map { it.toDomain() }
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
            response.toDomain()
        }

    override suspend fun getClientSucursales(clientId: String): Result<List<ClientBranch>> =
        runCatching {
            if (clientId.isBlank() || clientId == "0" || clientId.equals("CF", ignoreCase = true)) {
                return@runCatching emptyList()
            }
            val token = requireToken()
            apiService.getClientSucursales(token, clientId).map { it.toDomain() }
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

private fun ClientSucursalDto.toDomain(): ClientBranch =
    ClientBranch(
        sucursalId = sucursalId,
        clienteCodigo = clienteCodigo,
        nombreSucursal = nombreSucursal,
        nombreContacto = nombreContacto,
        telefonoContacto = telefonoContacto,
        correoContacto = correoContacto,
        direccion = direccion,
        observaciones = observaciones,
    )

