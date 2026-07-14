package com.amaxonia.pos.domain.repository

interface CatalogSynchronization {
    suspend fun syncAll(pageSize: Int = 300): Result<Unit>

    suspend fun isInitialSyncCompleted(): Boolean
}
