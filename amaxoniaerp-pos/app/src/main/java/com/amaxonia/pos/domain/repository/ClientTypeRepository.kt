package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.ClientTypeOption

interface ClientTypeRepository {
    suspend fun getClientTypes(): List<ClientTypeOption>
}
