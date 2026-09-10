package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.SyncApi
import com.amaxonia.pos.domain.model.tenant.SaleTenant

/**
 * Worker del sync incremental offline (sustituye el full-download de
 * [CatalogSyncWorker] para el motor ADR-007/008):
 *  - `runIncremental()` resuelve solo: si no hay cursor, ejecuta bootstrap.
 *  - Un cursor expirado (410) dispara resync completo automático.
 * El bootstrap "forzado" (Wi-Fi/cargador o manual) usa KEY_FORCE_BOOTSTRAP.
 */
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

        val apiConfigManager = ApiConfigManager.getInstance()
        val apiService = ApiService(ApiClient(apiConfigManager))
        val database = AppDatabase.getInstance(applicationContext)
        val tenantId = SaleTenant.idFor(session.company.id)
        val token = session.token

        val engine =
            SyncEngine(
                database = database,
                api = SyncApi(apiService),
                scopeProvider = { settings.load() },
                tenantProvider = {
                    localStore.readCompanySession()?.let { SaleTenant.idFor(it.company.id) } ?: tenantId
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

        return when (outcome) {
            is SyncEngine.Outcome.Success -> Result.success()
            is SyncEngine.Outcome.Error ->
                if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE_BOOTSTRAP = "force_bootstrap"
        const val KEY_RECONCILE = "reconcile"
        private const val MAX_RUN_ATTEMPTS = 10
    }
}
