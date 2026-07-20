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
    internal const val PENDING_INVOICES_WORK_NAME = "pending_invoice_sync"
    internal const val FISCAL_CONFIRMATION_WORK_NAME = "fiscal_confirmation_sync"
    internal const val GATEWAY_CALLBACK_WORK_NAME = "gateway_callback_sync"

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
        OneTimeWorkRequestBuilder<CatalogSyncWorker>()
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
