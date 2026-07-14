package com.amaxonia.pos.data.sync

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
    internal const val PENDING_INVOICES_WORK_NAME = "pending_invoice_sync"

    fun getManualSyncWorkInfos(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

    fun schedulePeriodic(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            PeriodicWorkRequestBuilder<CatalogSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
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

    fun enqueuePendingInvoices(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request = pendingInvoiceRequest(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(
            PENDING_INVOICES_WORK_NAME,
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
        OneTimeWorkRequestBuilder<CatalogSyncWorker>()
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
