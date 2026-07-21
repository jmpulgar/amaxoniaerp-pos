package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.system.SystemAppClock
import com.amaxonia.pos.domain.usecase.payment.QueueGatewayCallbackUseCase

/**
 * Watchdog that expires hung Rapid Pay callback awaits.
 *
 * The gateway result arrives out-of-band via an Android Intent re-launching
 * MainActivity. When MainActivity lands it flips the row to RESOLVED via
 * [QueueGatewayCallbackUseCase.markResolved]. If the process dies between
 * HKA returning and our receiver running (low-memory kill, OEM background
 * limits), the row stays AWAITING with an ever-growing retryCount. This
 * worker surfaces those rows so the UI can prompt the cashier for manual
 * reconciliation instead of silently hanging the sale.
 *
 * The worker does NOT itself fetch from HKA — there is no API to poll — it
 * only escalates rows whose lease has expired into RETRYABLE_AWAITING (UI
 * re-prompt) or TERMINAL_AWAITING (manual dispute).
 *
 * Lease semantics mirror [FiscalConfirmationWorker]: each claim sets
 * leasedUntil = now + LEASE_DURATION_MS so concurrent workers do not
 * double-process. A row whose lease ran out becomes eligible again.
 */
class GatewayCallbackWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val localStore = LocalStore(applicationContext)
        val dao = AppDatabase.getInstance(applicationContext).transactionLogDao()
        val now = SystemAppClock().now().toEpochMilli()
        val activeTenant = localStore.currentTenantId()
        val pending =
            activeTenant?.let { tenantId ->
                dao.findGatewayReconcilableForTenant(tenantId = tenantId, now = now, limit = BATCH_LIMIT)
            } ?: emptyList()

        if (pending.isEmpty()) {
            SafeLog.d(TAG, "No gateway callbacks awaiting reconciliation")
            return Result.success()
        }

        var anyRetry = false
        for (entry in pending) {
            if (processEntry(dao, entry, now)) anyRetry = true
        }
        return if (anyRetry) Result.retry() else Result.success()
    }

    private suspend fun processEntry(
        dao: TransactionLogDao,
        entry: TransactionLogEntity,
        now: Long,
    ): Boolean {
        val leaseUntil = now + QueueGatewayCallbackUseCase.LEASE_DURATION_MS
        dao.leaseGateway(entry.clientCorrelationId, leaseUntil)
        // Re-read after the lease; MainActivity may have just resolved it.
        val fresh = dao.findById(entry.clientCorrelationId)
        if (fresh == null || fresh.gatewayCallbackStatus != QueueGatewayCallbackUseCase.STATUS_AWAITING) {
            SafeLog.d(TAG, "Gateway callback for ${entry.clientCorrelationId} resolved by MainActivity")
            return false
        }
        val nextRetry = entry.gatewayCallbackRetryCount + 1
        return if (nextRetry >= QueueGatewayCallbackUseCase.MAX_RETRIES) {
            dao.markGatewayTerminal(
                id = entry.clientCorrelationId,
                status = QueueGatewayCallbackUseCase.STATUS_TERMINAL_AWAITING,
                rawResponse = entry.gatewayRawResponse,
                message = "Reconciliacion manual requerida: callback de pasarela no recibido",
            )
            SafeLog.w(TAG, "Gateway callback terminal for ${entry.clientCorrelationId} after $nextRetry cycles")
            false
        } else {
            val delay = QueueGatewayCallbackUseCase.nextAttempt(nextRetry)
            dao.markGatewayRetriable(
                id = entry.clientCorrelationId,
                status = QueueGatewayCallbackUseCase.STATUS_RETRYABLE_AWAITING,
                nextAttemptAt = now + delay,
                rawResponse = entry.gatewayRawResponse,
                message = null,
            )
            SafeLog.w(TAG, "Gateway callback retryable for ${entry.clientCorrelationId} (next in ${delay}ms)")
            true
        }
    }

    private companion object {
        const val TAG = "GatewayCallbackWorker"
        const val BATCH_LIMIT = 10
    }
}
