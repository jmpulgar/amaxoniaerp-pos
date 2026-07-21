package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.api.SalesApiImpl
import com.amaxonia.pos.data.repository.SalesRepositoryImpl
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.system.SystemAppClock
import com.amaxonia.pos.domain.usecase.payment.QueueFiscalConfirmationUseCase

/**
 * Replays PATCH /facturas/{id}/confirmacion-fiscal for any PAID sale whose
 * fiscal confirmation could not complete in the initial payment flow (printer
 * offline, network blip, backend 5xx). Selection excludes rows already
 * leased by another worker instance (leasedUntil > now).
 *
 * Lease semantics: each claim sets leasedUntil = now + LEASE_DURATION_MS so a
 * second concurrent worker (or this same worker if it crashed) does not
 * reprocess the same row within the lease window. A row whose lease has
 * expired is eligible again — the confirmation is idempotent on the backend.
 *
 * Does NOT use lastError IS NULL filter: a row with a stored error from a
 * previous failure is explicitly retryable until MAX_RETRIES.
 */
class FiscalConfirmationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val apiConfigManager = ApiConfigManager.getInstance()
        val localStore = LocalStore(applicationContext)
        localStore.readSelectedCountry()?.let { apiConfigManager.updateBaseUrl(it) }
        val salesRepository =
            SalesRepositoryImpl(
                salesApi = SalesApiImpl(ApiClient(apiConfigManager)),
                localStore = localStore,
            )
        val dao = AppDatabase.getInstance(applicationContext).transactionLogDao()
        val now = SystemAppClock().now().toEpochMilli()
        val activeTenant = localStore.currentTenantId()
        val pending =
            (activeTenant?.let { tenantId ->
                dao.findFiscalConfirmableForTenant(tenantId = tenantId, now = now, limit = BATCH_LIMIT)
            } ?: emptyList())
                .filter { it.remoteInvoiceId != null }

        if (pending.isEmpty()) {
            SafeLog.d(TAG, "No fiscal confirmations pending")
            return Result.success()
        }

        var anyRetry = false
        for (entry in pending) {
            if (processEntry(dao, salesRepository, entry, now)) anyRetry = true
        }
        return if (anyRetry) Result.retry() else Result.success()
    }

    private suspend fun processEntry(
        dao: TransactionLogDao,
        salesRepository: SalesRepositoryImpl,
        entry: TransactionLogEntity,
        now: Long,
    ): Boolean {
        val remoteId = entry.remoteInvoiceId ?: return false
        val leaseUntil = now + QueueFiscalConfirmationUseCase.LEASE_DURATION_MS
        dao.leaseFiscal(entry.clientCorrelationId, leaseUntil)
        val payload =
            ConfirmFacturaFiscalRequestDto(
                numeroDocumentoFiscal = entry.fiscalNumber.orEmpty(),
                impresoraSerial = entry.printerSerial.orEmpty(),
            )
        val result = salesRepository.confirmFacturaFiscal(remoteId, payload)
        val message = result.exceptionOrNull()?.message ?: "Confirmación fiscal fallida"
        val nextRetry = entry.fiscalConfirmationRetryCount + 1
        return when {
            result.isSuccess -> {
                dao.markFiscalConfirmed(
                    id = entry.clientCorrelationId,
                    status = QueueFiscalConfirmationUseCase.STATUS_CONFIRMED,
                    fiscalNumber = entry.fiscalNumber,
                    printerSerial = entry.printerSerial,
                )
                SafeLog.i(TAG, "Fiscal confirmation succeeded for ${entry.clientCorrelationId}")
                false
            }
            nextRetry >= QueueFiscalConfirmationUseCase.MAX_RETRIES -> {
                dao.markFiscalTerminal(
                    id = entry.clientCorrelationId,
                    status = QueueFiscalConfirmationUseCase.STATUS_TERMINAL_FAILED,
                    message = message,
                )
                SafeLog.w(TAG, "Fiscal confirmation terminal failure for ${entry.clientCorrelationId}: $message")
                false
            }
            else -> {
                val delay = QueueFiscalConfirmationUseCase.nextAttempt(nextRetry)
                dao.markFiscalRetriable(
                    id = entry.clientCorrelationId,
                    status = QueueFiscalConfirmationUseCase.STATUS_RETRYABLE_PENDING,
                    remoteInvoiceId = remoteId,
                    fiscalNumber = entry.fiscalNumber,
                    printerSerial = entry.printerSerial,
                    nextAttemptAt = now + delay,
                    message = message,
                )
                SafeLog.w(TAG, "Fiscal confirmation retryable for ${entry.clientCorrelationId}: $message (next in ${delay}ms)")
                true
            }
        }
    }

    private companion object {
        const val TAG = "FiscalConfirmationWorker"
        const val BATCH_LIMIT = 25
    }
}
