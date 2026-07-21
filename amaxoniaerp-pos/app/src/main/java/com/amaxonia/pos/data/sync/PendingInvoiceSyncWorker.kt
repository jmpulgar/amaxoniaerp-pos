package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.LocalStore
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
import com.amaxonia.pos.domain.usecase.sync.SynchronizePendingInvoicesUseCase
import com.amaxonia.pos.domain.usecase.sync.SynchronizedInvoice

class PendingInvoiceSyncWorker(
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
        val dao = AppDatabase.getInstance(applicationContext).pendingInvoiceDao()
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
                            PendingInvoiceRecord(invoice.id, invoice.localInvoiceNumber, invoice.payloadJson)
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
                }

                override suspend fun markRecoverableFailure(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markFailed(id, message, nowEpochMillis)
                }

                override suspend fun markPermanentFailure(
                    id: String,
                    message: String,
                    nowEpochMillis: Long,
                ) {
                    dao.markInvalid(id, message, nowEpochMillis)
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
            )

        return when (useCase(localStore.currentTenantId())) {
            PendingInvoiceSyncResult.Success -> Result.success()
            PendingInvoiceSyncResult.Retry -> Result.retry()
        }
    }
}
