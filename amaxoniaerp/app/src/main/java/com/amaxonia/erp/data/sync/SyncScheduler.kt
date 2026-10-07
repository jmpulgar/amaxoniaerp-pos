package com.amaxonia.erp.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

    private const val CATALOG_SYNC_INTERVAL_HOURS = 12L
    private const val RECONCILE_INTERVAL_DAYS = 7L

    fun getManualSyncWorkInfos(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

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

    fun enqueueBootstrap(
        context: Context,
        requiresCharging: Boolean = false,
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

    fun cancelCatalogSync(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        wm.cancelUniqueWork(MANUAL_WORK_NAME)
        wm.cancelUniqueWork(BOOTSTRAP_WORK_NAME)
        wm.cancelUniqueWork(RECONCILE_WORK_NAME)
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

    private fun connectedConstraints() =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
