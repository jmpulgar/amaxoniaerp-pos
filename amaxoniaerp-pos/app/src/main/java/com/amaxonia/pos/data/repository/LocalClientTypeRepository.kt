package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.ClientTypeDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.repository.ClientTypeRepository

class LocalClientTypeRepository(
    private val clientTypeDao: ClientTypeDao,
) : ClientTypeRepository {
    override suspend fun getClientTypes(): List<ClientTypeOption> = clientTypeDao.getAll().map { it.toDomain() }
}
