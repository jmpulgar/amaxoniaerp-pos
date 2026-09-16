package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ClientSucursalDao
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.dto.ClientSucursalDto
import com.amaxonia.pos.data.remote.getClientSucursales
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.repository.ClientBranchRepository


class OfflineFirstClientBranchRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val dao: ClientSucursalDao,
    private val networkMonitor: NetworkMonitor,
    private val localFallback: ClientBranchRepository = RoomClientBranchRepository(dao),
) : ClientBranchRepository {

    override suspend fun findFor(client: Client): List<ClientBranch> {
        if (networkMonitor.isOnline()) {
            val token = localStore.readCompanySession()?.token
            val clientId = client.id.ifBlank { client.code }

            if (!token.isNullOrBlank() && clientId.isNotBlank()) {
                val remoteResult = runCatching {
                    apiService.getClientSucursales(token, clientId)
                }

                if (remoteResult.isSuccess) {
                    val remoteList = remoteResult.getOrThrow()
                    val clientCode =
                        client.code.trim().take(CLIENT_CODE_MAX_LENGTH)
                            .ifBlank { remoteList.firstOrNull()?.clienteCodigo?.trim()?.take(CLIENT_CODE_MAX_LENGTH).orEmpty() }

                    if (clientCode.isNotBlank()) {
                        dao.deleteByClientCode(clientCode)
                    }
                    if (remoteList.isNotEmpty()) {
                        dao.insertAll(remoteList.map { it.toEntity() })
                    }
                    return remoteList.map { it.toDomain() }
                }
            }
        }

        return localFallback.findFor(client)
    }

    private companion object {
        const val CLIENT_CODE_MAX_LENGTH = 9
    }
}

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
