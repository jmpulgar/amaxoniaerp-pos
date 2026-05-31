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

class PendingInvoiceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val apiConfigManager = ApiConfigManager.getInstance()
        val localStore = LocalStore(applicationContext)
        localStore.readSelectedCountry()?.let { apiConfigManager.updateBaseUrl(it) }
        val salesRepository = SalesRepositoryImpl(
            salesApi = SalesApiImpl(ApiClient(apiConfigManager)),
            localStore = localStore
        )
        val dao = AppDatabase.getInstance(applicationContext).pendingInvoiceDao()
        val pending = dao.getPending()
        if (pending.isEmpty()) return Result.success()

        var hasFailure = false
        pending.forEach { invoice ->
            dao.markSending(invoice.id)
            val payload = runCatching {
                AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), invoice.payloadJson)
            }.getOrElse { error ->
                hasFailure = true
                dao.markFailed(invoice.id, error.message ?: "Payload local inválido")
                return@forEach
            }

            salesRepository.processSale(payload).fold(
                onSuccess = { response ->
                    dao.markSent(
                        id = invoice.id,
                        remoteInvoiceId = response.idFactura,
                        remoteInvoiceNumber = response.codFactura
                    )
                },
                onFailure = { error ->
                    hasFailure = true
                    dao.markFailed(invoice.id, error.message ?: "No se pudo reenviar la factura")
                }
            )
        }

        return if (hasFailure) Result.retry() else Result.success()
    }
}
