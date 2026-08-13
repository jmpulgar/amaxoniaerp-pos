package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaSummaryDto
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository

/**
 * Real implementation of [TransactionRepository] that fetches invoices
 * from the backend GET /facturas endpoint via [SalesApi].
 */
class ApiTransactionRepository(
    private val salesApi: SalesApi,
    private val localStore: LocalStore,
) : InvoiceHistoryRepository {
    override suspend fun getAllTransactions(): Result<List<Transaction>> =
        getTransactions().map { it.transactions }

    override suspend fun getTransactionById(id: String): Result<Transaction> =
        catchingResult {
            val authHeader = getAuthHeader()
            salesApi.getFacturas(
                authHeader = authHeader,
                limit = 10,
                filter = InvoiceHistoryFilter(search = id),
            ).map { response ->
                response.data
                    .firstOrNull { it.id == id || it.codigo == id }
                    ?.toTransaction()
                    ?: error("Transaccion no encontrada: $id")
            }
        }

    override suspend fun getTransactions(
        filter: InvoiceHistoryFilter,
        limit: Int,
        offset: Long,
    ): Result<InvoiceHistoryPage> =
        catchingResult {
            val authHeader = getAuthHeader()
            salesApi.getFacturas(
                authHeader = authHeader,
                limit = limit,
                offset = offset,
                filter = filter,
            ).map { response ->
                InvoiceHistoryPage(
                    transactions = response.data.map { dto -> dto.toTransaction() },
                    total = response.total,
                )
            }
        }

    override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> =
        catchingResult {
            val authHeader = getAuthHeader()
            salesApi.getFacturasResumen(authHeader = authHeader, filter = filter).map { summary ->
                InvoiceHistorySummary(
                    ventasNetas = summary.ventasNetas,
                    totalFacturas = summary.totalFacturas,
                    moneda = summary.moneda,
                )
            }
        }

    override suspend fun saveTransaction(transaction: Transaction): Result<Unit> {
        // Sales are created via processSale() in SalesRepository, not here.
        return Result.success(Unit)
    }

    override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> =
        catchingResult {
            val authHeader = getAuthHeader()
            salesApi.getFacturaDetalle(authHeader, invoiceId)
        }

    private suspend fun getAuthHeader(): String {
        val token =
            localStore.readCompanySession()?.token
                ?: error("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}

/**
 * Maps the backend FacturaSummaryDto to the app's Transaction domain model.
 */
private fun FacturaSummaryDto.toTransaction(): Transaction {
    val status =
        when {
            estatus.equals("Anulada", ignoreCase = true) ||
                estatus.equals("Anulado", ignoreCase = true) -> TransactionStatus.CANCELLED
            estatus.equals("En Espera", ignoreCase = true) ||
                estatus.equals("Pendiente", ignoreCase = true) -> TransactionStatus.PENDING
            else -> TransactionStatus.PAID
        }

    // fecha comes as "dd/MM/yyyy" from the backend
    val dateHeader = fecha.ifBlank { "Sin fecha" }

    // Extract time from fechaCreacion (format "dd/MM/yyyy HH:mm:ss"), fallback to fechaDgi
    val time =
        extractTime(fechaCreacion)
            ?: extractTime(fechaDgi)
            ?: "--:--"

    return Transaction(
        id = id,
        invoiceNumber = codigo,
        time = time,
        amount = total,
        currency = moneda,
        status = status,
        dateHeader = dateHeader,
        clienteNombre = clienteNombre,
        clienteIdentificacion = clienteIdentificacion,
        formaPago = formaPago,
        totalRef = totalRef,
        abrMonedaSecundaria = abrMonedaSecundaria,
    )
}

/**
 * Extracts "HH:mm" from a datetime string like "dd/MM/yyyy HH:mm:ss".
 */
private fun extractTime(dateTimeStr: String): String? {
    if (dateTimeStr.isBlank() || dateTimeStr.length <= 10) return null
    return dateTimeStr.substring(11).take(5) // "HH:mm"
}
