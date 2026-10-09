package com.amaxonia.erp.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.AppDatabase
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.SyncApi
import com.amaxonia.erp.data.remote.getCajas

class OfflineSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = OfflineSyncSettingsStore(applicationContext)
        val forceBootstrap = inputData.getBoolean(KEY_FORCE_BOOTSTRAP, false)
        val currentScope = settings.load()
        val localStore = LocalStore(applicationContext)
        val session = localStore.readCompanySession()

        if ((!currentScope.enabled && !forceBootstrap) || session == null) {
            return Result.success()
        }

        val apiService = ApiService(ApiClient())
        val database = AppDatabase.getInstance(applicationContext)
        val tenantId = session.company.id.toString()
        val token = session.token

        val engine =
            SyncEngine(
                database = database,
                api = SyncApi(apiService),
                scopeProvider = { settings.load() },
                tenantProvider = {
                    localStore.readCompanySession()?.company?.id?.toString() ?: tenantId
                },
                tokenProvider = { localStore.readCompanySession()?.token ?: token },
            )
        val reconcile = inputData.getBoolean(KEY_RECONCILE, false)
        val outcome =
            when {
                reconcile -> engine.reconcile()
                forceBootstrap -> engine.runBootstrap()
                else -> engine.runIncremental()
            }

        if (outcome is SyncEngine.Outcome.Success) {
            runCatching {
                val adminDb = session.company.adminDb.ifBlank { "default" }
                val cajas = apiService.getCajas(token, adminDb, all = true)
                localStore.saveCajas(cajas)
            }
        }

        return when (outcome) {
            is SyncEngine.Outcome.Success -> Result.success()
            is SyncEngine.Outcome.Error ->
                if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE_BOOTSTRAP = "force_bootstrap"
        const val KEY_RECONCILE = "reconcile"
        private const val MAX_RUN_ATTEMPTS = 5
    }
}
