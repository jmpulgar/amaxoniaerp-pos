package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

object SyncScheduler {
    internal const val PERIODIC_WORK_NAME = "catalog_sync_periodic"
    internal const val MANUAL_WORK_NAME = "catalog_sync_manual"
    internal const val BOOTSTRAP_WORK_NAME = "offline_sync_bootstrap"
    internal const val RECONCILE_WORK_NAME = "offline_sync_reconcile_weekly"
    internal const val PENDING_INVOICES_WORK_NAME = "pending_invoice_sync"
    internal const val FISCAL_CONFIRMATION_WORK_NAME = "fiscal_confirmation_sync"
    internal const val GATEWAY_CALLBACK_WORK_NAME = "gateway_callback_sync"

    /** Intervalo del sync periódico incremental en segundo plano. */
    private const val CATALOG_SYNC_INTERVAL_HOURS = 12L

    /** Reconciliación de integridad semanal (PLAN §7.3, Q16). */
    private const val RECONCILE_INTERVAL_DAYS = 7L

    fun getManualSyncWorkInfos(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

    /**
     * Sync incremental periódico (O(cambios), barato): cualquier red.
     * El motor resuelve solo: si falta cursor, ejecuta bootstrap.
     */
    fun schedulePeriodic(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            PeriodicWorkRequestBuilder<OfflineSyncWorker>(CATALOG_SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Reconciliación semanal (Q16): delta + comparación de conteos contra el
     * manifest → bootstrap automático si hay divergencia. Solo Wi-Fi + cargador.
     */
    fun scheduleWeeklyReconcile(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()
        val request =
            PeriodicWorkRequestBuilder<OfflineSyncWorker>(RECONCILE_INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(
                    androidx.work.Data
                        .Builder()
                        .putBoolean(OfflineSyncWorker.KEY_RECONCILE, true)
                        .build(),
                ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECONCILE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Sync incremental manual inmediato ("Actualizar datos"). */
    fun enqueueManual(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request = catalogSyncRequest(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Bootstrap completo (o resync tras cambio de alcance): descarga grande →
     * solo Wi-Fi (red no medida) con el dispositivo cargando (Q10/§17), o
     * invocación manual explícita desde "Ajustes Offline".
     */
    fun enqueueBootstrap(
        context: Context,
        requiresCharging: Boolean = true,
        unmeteredOnly: Boolean = false,
    ) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(requiresCharging)
                .build()
        val request =
            OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setInputData(
                    androidx.work.Data
                        .Builder()
                        .putBoolean(OfflineSyncWorker.KEY_FORCE_BOOTSTRAP, true)
                        .build(),
                ).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOTSTRAP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueuePendingInvoices(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request = pendingInvoiceRequest(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(
            PENDING_INVOICES_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Schedules an immediate one-shot replay of pending fiscal confirmations
     * for invoices whose fiscalNumber could not be confirmed in the flow.
     * Idempotent with KEEP policy so multiple triggers collapse into one.
     */
    fun enqueueFiscalConfirmations(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request = fiscalConfirmationRequest(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(
            FISCAL_CONFIRMATION_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedules an immediate one-shot reconciliation of any Rapid Pay
     * callback that is still awaiting after the lease window. Idempotent
     * with KEEP policy so multiple triggers collapse into one. Does not
     * require network: the watchdog only inspects local rows.
     */
    fun enqueueGatewayCallbacks(context: Context) {
        val request = gatewayCallbackRequest()
        WorkManager.getInstance(context).enqueueUniqueWork(
            GATEWAY_CALLBACK_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun pendingInvoiceRequest(constraints: Constraints = connectedConstraints()) =
        OneTimeWorkRequestBuilder<PendingInvoiceSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            ).build()

    internal fun catalogSyncRequest(constraints: Constraints = connectedConstraints()) =
        OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            ).build()

    internal fun fiscalConfirmationRequest(constraints: Constraints = connectedConstraints()) =
        OneTimeWorkRequestBuilder<FiscalConfirmationWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            ).build()

    internal fun gatewayCallbackRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<GatewayCallbackWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            ).build()

    private fun connectedConstraints() =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
