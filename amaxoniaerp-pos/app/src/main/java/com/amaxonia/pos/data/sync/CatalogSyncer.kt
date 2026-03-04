package com.amaxonia.pos.data.sync

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AddressLevel1Dao
import com.amaxonia.pos.data.local.db.AddressLevel2Dao
import com.amaxonia.pos.data.local.db.AddressLevel3Dao
import com.amaxonia.pos.data.local.db.ClientDao
import com.amaxonia.pos.data.local.db.CountryDao
import com.amaxonia.pos.data.local.db.ClientTypeDao
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.db.toLevel1Entity
import com.amaxonia.pos.data.local.db.toLevel2Entity
import com.amaxonia.pos.data.local.db.toLevel3Entity
import com.amaxonia.pos.data.remote.ApiService

class CatalogSyncer(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val clientDao: ClientDao,
    private val productDao: ProductDao,
    private val countryDao: CountryDao,
    private val addressLevel1Dao: AddressLevel1Dao,
    private val addressLevel2Dao: AddressLevel2Dao,
    private val addressLevel3Dao: AddressLevel3Dao,
    private val clientTypeDao: ClientTypeDao
) {
    suspend fun syncAll(pageSize: Int = 1000): Result<Unit> {
        val session = localStore.readCompanySession()
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        return runCatching {
            syncCountries(session.token, pageSize)
            syncAddressLevels(session.token, pageSize)
            syncClientTypes(session.token, pageSize)
            syncClients(session.token, pageSize)
            syncProducts(session.token, pageSize)
            localStore.setInitialSyncCompleted(session.company.id, true)
        }
    }

    suspend fun isInitialSyncCompleted(): Boolean {
        val session = localStore.readCompanySession() ?: return false
        return localStore.isInitialSyncCompleted(session.company.id)
    }

    private suspend fun syncClients(token: String, pageSize: Int) {
        var offset = 0
        while (true) {
            val response = apiService.getClients(
                token = token,
                limit = pageSize,
                offset = offset,
                search = null,
                includeTotal = false
            )
            if (response.data.isEmpty()) break
            clientDao.insertAll(response.data.map { it.toEntity() })
            offset += pageSize
        }
    }

    private suspend fun syncProducts(token: String, pageSize: Int) {
        var offset = 0
        while (true) {
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                search = null,
                includeTotal = false
            )
            if (response.data.isEmpty()) break
            productDao.insertAll(response.data.map { it.toEntity() })
            offset += pageSize
        }
    }

    private suspend fun syncCountries(token: String, pageSize: Int) {
        var offset = 0
        while (true) {
            val response = apiService.getCountries(
                token = token,
                limit = pageSize,
                offset = offset,
                includeTotal = false
            )
            if (response.isEmpty()) break
            countryDao.insertAll(response.map { it.toEntity() })
            offset += pageSize
        }
    }

    private suspend fun syncAddressLevels(token: String, pageSize: Int) {
        syncAddressLevel(token, pageSize, 1)
        syncAddressLevel(token, pageSize, 2)
        syncAddressLevel(token, pageSize, 3)
    }

    private suspend fun syncAddressLevel(token: String, pageSize: Int, level: Int) {
        var offset = 0
        while (true) {
            val response = apiService.getAddressLevels(
                token = token,
                level = level,
                limit = pageSize,
                offset = offset,
                includeTotal = false
            )
            if (response.isEmpty()) break
            when (level) {
                1 -> addressLevel1Dao.insertAll(response.map { it.toLevel1Entity() })
                2 -> addressLevel2Dao.insertAll(response.map { it.toLevel2Entity() })
                3 -> addressLevel3Dao.insertAll(response.map { it.toLevel3Entity() })
            }
            offset += pageSize
        }
    }

    private suspend fun syncClientTypes(token: String, pageSize: Int) {
        var offset = 0
        while (true) {
            val response = apiService.getClientTypes(
                token = token,
                limit = pageSize,
                offset = offset,
                includeTotal = false
            )
            if (response.isEmpty()) break
            clientTypeDao.insertAll(response.map { it.toEntity() })
            offset += pageSize
        }
    }
}
