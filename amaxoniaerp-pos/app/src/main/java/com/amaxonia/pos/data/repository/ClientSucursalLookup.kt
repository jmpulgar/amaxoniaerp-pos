package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.ClientSucursalDao
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.domain.model.Client

suspend fun ClientSucursalDao.getForClient(client: Client): List<ClientSucursalEntity> {
    val candidates = buildList {
        add(client.code.trim())
        add(client.code.trim().take(9))
        add(client.id.trim())
        add(client.id.trim().take(9))
    }
        .filter { it.isNotBlank() }
        .distinct()

    if (candidates.isEmpty()) return emptyList()
    return getByClientCodes(candidates).distinctBy { it.sucursalId }
}
