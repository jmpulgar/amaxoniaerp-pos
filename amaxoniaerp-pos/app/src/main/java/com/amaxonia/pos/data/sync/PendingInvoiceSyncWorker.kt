package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.currentTenantId
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.api.SalesApiImpl
import com.amaxonia.pos.data.repository.SalesRepositoryImpl
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.system.SystemAppClock
import com.amaxonia.pos.domain.usecase.sync.PendingInvoiceQueue
import com.amaxonia.pos.domain.usecase.sync.PendingInvoiceRecord
import com.amaxonia.pos.domain.usecase.sync.PendingInvoiceSyncResult
import com.amaxonia.pos.domain.usecase.sync.PendingSaleDecoder
import com.amaxonia.pos.domain.usecase.sync.PendingSaleGateway
import com.amaxonia.pos.domain.usecase.sync.SyncRetryConfig
import com.amaxonia.pos.domain.usecase.sync.SynchronizePendingInvoicesUseCase
import com.amaxonia.pos.domain.usecase.sync.SynchronizedInvoice

class PendingInvoiceSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val localStore = LocalStore(applicationContext)
        val companyCountry =
            localStore.readCompanySession()?.company?.countryCode?.takeIf { it.isNotBlank() }
                ?: localStore.currentCountryCode()
        if (!companyCountry.equals("PA", ignoreCase = true)) {
            return Result.success()
        }

        val apiConfigManager = ApiConfigManager.getInstance()
        localStore.readSelectedCountry()?.let { apiConfigManager.updateBaseUrl(it) }
        val salesApi = SalesApiImpl(ApiClient(apiConfigManager))
        val salesRepository =
            SalesRepositoryImpl(
                salesApi = salesApi,
                localStore = localStore,
            )
        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.pendingInvoiceDao()
        val transactionLogDao = database.transactionLogDao()
        val queue =
            object : PendingInvoiceQueue {
                override suspend fun recoverInterrupted(
                    staleBeforeEpochMillis: Long,
                    nowEpochMillis: Long,
                ) {
                    dao.recoverInterrupted(staleBeforeEpochMillis, nowEpochMillis)
                }

                override suspend fun pending(tenantId: String?): List<PendingInvoiceRecord> =
                    if (tenantId == null) {
                        emptyList()
                    } else {
                        dao.getPendingForTenant(tenantId).map { invoice ->
                            PendingInvoiceRecord(
                                id = invoice.id,
                                countryCode = invoice.countryCode,
                                localInvoiceNumber = invoice.localInvoiceNumber,
                                payloadJson = invoice.payloadJson,
                                retryCount = invoice.retryCount,
                            )
                        }
                    }

                override suspend fun tryClaim(
                    id: String,
                    now: Long,
                    leasedUntil: Long,
                ): Int = dao.tryClaim(id, now, leasedUntil)

                override suspend fun markSending(
                    id: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markSending(id, nowEpochMillis)
                }

                override suspend fun markSent(
                    id: String,
                    result: SynchronizedInvoice,
                    nowEpochMillis: Long,
                ) {
                    dao.markSent(id, result.remoteId, result.remoteNumber, nowEpochMillis)
                    transactionLogDao.markConfirmed(
                        id = id,
                        status = "CONFIRMED",
                        remoteInvoiceId = result.remoteId,
                        remoteInvoiceNumber = result.remoteNumber,
                        updatedAt = nowEpochMillis,
                    )
                }

                override suspend fun markRecoverableFailure(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markFailed(id, message, nowEpochMillis)
                    transactionLogDao.markFailed(
                        id = id,
                        status = "RETRYABLE_PENDING",
                        message = message,
                        updatedAt = nowEpochMillis,
                    )
                }

                /** Rechazo de dominio (400): terminal, visible con motivo, jamás se reenvía. */
                override suspend fun markRejected(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markRejected(id, message, nowEpochMillis)
                    transactionLogDao.markFailed(
                        id = id,
                        status = "REJECTED",
                        message = message,
                        updatedAt = nowEpochMillis,
                    )
                }

                /** Tope de reintentos alcanzado: excluida del loop hasta intervención. */
                override suspend fun markSuspended(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markFailed(id, message, nowEpochMillis)
                    transactionLogDao.markFailed(
                        id = id,
                        status = "SUSPENDED",
                        message = message,
                        updatedAt = nowEpochMillis,
                    )
                }

                override suspend fun markPermanentFailure(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markInvalid(id, message, nowEpochMillis)
                    transactionLogDao.markFailed(
                        id = id,
                        status = "FAILED",
                        message = message,
                        updatedAt = nowEpochMillis,
                    )
                }
            }
        val useCase =
            SynchronizePendingInvoicesUseCase(
                queue = queue,
                decoder =
                    PendingSaleDecoder { json ->
                        runCatching { AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), json) }
                    },
                gateway =
                    object : PendingSaleGateway {
                        override suspend fun submit(payload: ProcessSaleRequestDto): kotlin.Result<SynchronizedInvoice> =
                            salesRepository.processSale(payload).map { response ->
                                SynchronizedInvoice(response.idFactura, response.codFactura)
                            }
                    },
                clock = SystemAppClock(),
                config =
                    SyncRetryConfig(
                        reconciler = { correlationId ->
                            val token = localStore.readCompanySession()?.token.orEmpty()
                            salesApi
                                .findByCorrelationId(authHeader = "Bearer $token", clientCorrelationId = correlationId)
                                .map { reconciled ->
                                    checkNotNull(reconciled) {
                                        "La factura ya existe pero no pudo recuperarse del servidor"
                                    }.let { SynchronizedInvoice(it.idFactura, it.codFactura) }
                                }
                        },
                    ),
            )

        return when (useCase(localStore.currentTenantId())) {
            PendingInvoiceSyncResult.Success -> Result.success()
            PendingInvoiceSyncResult.Retry -> Result.retry()
        }
    }
}
