package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.ClientSucursalDao
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.repository.ClientBranchRepository

class RoomClientBranchRepository(
    private val dao: ClientSucursalDao,
) : ClientBranchRepository {
    override suspend fun findFor(client: Client): List<ClientBranch> = dao.getForClient(client)
}

/** Longitud máxima del código de cliente usada como clave de sus sucursales. */
private const val CLIENT_CODE_MAX_LENGTH = 9

private suspend fun ClientSucursalDao.getForClient(client: Client): List<ClientBranch> {
    val candidates =
        buildList {
            add(client.code.trim())
            add(client.code.trim().take(CLIENT_CODE_MAX_LENGTH))
            add(client.id.trim())
            add(client.id.trim().take(CLIENT_CODE_MAX_LENGTH))
        }.filter { it.isNotBlank() }
            .distinct()

    if (candidates.isEmpty()) return emptyList()
    return getByClientCodes(candidates)
        .distinctBy { it.sucursalId }
        .map { entity ->
            ClientBranch(
                sucursalId = entity.sucursalId,
                clienteCodigo = entity.clienteCodigo,
                nombreSucursal = entity.nombreSucursal,
                nombreContacto = entity.nombreContacto,
                telefonoContacto = entity.telefonoContacto,
                correoContacto = entity.correoContacto,
                direccion = entity.direccion,
                observaciones = entity.observaciones,
            )
        }
}
