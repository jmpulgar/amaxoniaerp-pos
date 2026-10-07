package com.amaxonia.erp.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.AppDatabase
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.repository.SalesRepositoryImpl

class PendingInvoiceSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val localStore = LocalStore(applicationContext)
        val session = localStore.readCompanySession() ?: return Result.success()
        val tenantId = session.company.id.toString()

        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.pendingInvoiceDao()
        val apiService = ApiService(ApiClient())
        val salesRepository = SalesRepositoryImpl(apiService, localStore)

        val now = System.currentTimeMillis()
        val staleBefore = now - 60_000L
        dao.recoverInterrupted(staleBefore, now)

        val pending = dao.getPendingForTenant(tenantId, limit = 50)
        var hasRetries = false

        for (invoice in pending) {
            val claimDeadline = now + 30_000L
            val claimed = dao.tryClaim(invoice.id, now = now, leasedUntil = claimDeadline, updatedAt = now)
            if (claimed == 0) continue

            dao.markSending(invoice.id, System.currentTimeMillis())

            val request =
                try {
                    AppJson.decodeFromString<ProcessSaleRequestDto>(invoice.payloadJson)
                } catch (error: Throwable) {
                    dao.markInvalid(invoice.id, error.message ?: "Payload inválido", System.currentTimeMillis())
                    continue
                }

            val sanitizedRequest =
                request.copy(
                    idFactura = request.idFactura ?: invoice.id,
                    codFactura = request.codFactura ?: invoice.localInvoiceNumber,
                )

            val result = salesRepository.processSale(sanitizedRequest)
            result.fold(
                onSuccess = { response ->
                    val remoteId = response.idFactura.ifBlank { response.codFactura }
                    val remoteNum = response.codFactura.ifBlank { response.idFactura }
                    dao.markSent(invoice.id, remoteId, remoteNum, System.currentTimeMillis())
                },
                onFailure = { error ->
                    val message = error.message ?: "Error al procesar venta"
                    if (message.contains("400") || message.contains("invalida", ignoreCase = true)) {
                        dao.markRejected(invoice.id, message, System.currentTimeMillis())
                    } else {
                        dao.markFailed(invoice.id, message, System.currentTimeMillis())
                        hasRetries = true
                    }
                },
            )
        }

        return if (hasRetries) Result.retry() else Result.success()
    }
}
