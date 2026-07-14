package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService

class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val apiConfigManager = ApiConfigManager.getInstance()
        val apiClient = ApiClient(apiConfigManager)
        val apiService = ApiService(apiClient)
        val localStore = LocalStore(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val syncer =
            CatalogSyncer(
                apiService = apiService,
                localStore = localStore,
                clientDao = database.clientDao(),
                clientSucursalDao = database.clientSucursalDao(),
                productDao = database.productDao(),
                countryDao = database.countryDao(),
                addressLevel1Dao = database.addressLevel1Dao(),
                addressLevel2Dao = database.addressLevel2Dao(),
                addressLevel3Dao = database.addressLevel3Dao(),
                clientTypeDao = database.clientTypeDao(),
                promocionDao = database.promocionDao(),
            )

        return syncer.syncAll().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
