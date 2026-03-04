package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaSummaryDto
import com.amaxonia.pos.domain.repository.TransactionRepository

/**
 * Real implementation of [TransactionRepository] that fetches invoices
 * from the backend GET /facturas endpoint via [SalesApi].
 */
class ApiTransactionRepository(
    private val salesApi: SalesApi,
    private val localStore: LocalStore
) : TransactionRepository {

    override suspend fun getAllTransactions(): Result<List<Transaction>> {
        return try {
            val authHeader = getAuthHeader()
            salesApi.getFacturas(authHeader = authHeader, limit = 200).map { response ->
                response.data.map { dto -> dto.toTransaction() }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransactionById(id: String): Result<Transaction> {
        return try {
            val authHeader = getAuthHeader()
            salesApi.getFacturas(authHeader = authHeader, search = id, limit = 10).map { response ->
                response.data
                    .firstOrNull { it.id == id || it.codigo == id }
                    ?.toTransaction()
                    ?: throw IllegalStateException("Transaccion no encontrada: $id")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveTransaction(transaction: Transaction): Result<Unit> {
        // Sales are created via processSale() in SalesRepository, not here.
        return Result.success(Unit)
    }

    suspend fun getFacturaDetalle(facturaId: String): Result<FacturaDetalleResponseDto> {
        return try {
            val authHeader = getAuthHeader()
            salesApi.getFacturaDetalle(authHeader, facturaId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAuthHeader(): String {
        val token = localStore.readCompanySession()?.token
            ?: throw IllegalStateException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}

/**
 * Maps the backend FacturaSummaryDto to the app's Transaction domain model.
 */
private fun FacturaSummaryDto.toTransaction(): Transaction {
    val status = when {
        estatus.equals("Anulada", ignoreCase = true) ||
        estatus.equals("Anulado", ignoreCase = true) -> TransactionStatus.CANCELLED
        estatus.equals("En Espera", ignoreCase = true) ||
        estatus.equals("Pendiente", ignoreCase = true) -> TransactionStatus.PENDING
        else -> TransactionStatus.PAID
    }

    // fecha comes as "dd/MM/yyyy" from the backend
    val dateHeader = fecha.ifBlank { "Sin fecha" }

    // Extract time from fechaCreacion (format "dd/MM/yyyy HH:mm:ss"), fallback to fechaDgi
    val time = extractTime(fechaCreacion)
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
    )
}

/**
 * Extracts "HH:mm" from a datetime string like "dd/MM/yyyy HH:mm:ss".
 */
private fun extractTime(dateTimeStr: String): String? {
    if (dateTimeStr.isBlank() || dateTimeStr.length <= 10) return null
    return dateTimeStr.substring(11).take(5) // "HH:mm"
}
