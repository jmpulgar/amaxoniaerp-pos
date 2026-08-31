package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.currentTenantId
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.sales.FacturaDetalleItemDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaSummaryDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary

/**
 * Real implementation of [TransactionRepository] that fetches invoices
 * from the backend GET /facturas endpoint via [SalesApi], reconciling with
 * local pending offline invoices when available.
 */
class ApiTransactionRepository(
    private val salesApi: SalesApi,
    private val localStore: LocalStore,
    private val pendingInvoiceDao: PendingInvoiceDao? = null,
) : InvoiceHistoryRepository {
    override suspend fun getAllTransactions(): Result<List<Transaction>> = getTransactions().map { it.transactions }

    override suspend fun getTransactionById(id: String): Result<Transaction> =
        catchingResult {
            val localPending = getLocalPendingTransactions()
            val localMatch = localPending.firstOrNull { it.id == id || it.invoiceNumber == id }
            if (localMatch != null) return@catchingResult Result.success(localMatch)

            val authHeader = getAuthHeader()
            salesApi
                .getFacturas(
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
            val localPending = if (offset == 0L) getLocalPendingTransactions() else emptyList()
            val authHeader = runCatching { getAuthHeader() }.getOrNull()

            if (authHeader == null) {
                return@catchingResult Result.success(
                    InvoiceHistoryPage(
                        transactions = localPending,
                        total = localPending.size.toLong(),
                    ),
                )
            }

            val remoteResult =
                salesApi.getFacturas(
                    authHeader = authHeader,
                    limit = limit,
                    offset = offset,
                    filter = filter,
                )

            remoteResult.fold(
                onSuccess = { response ->
                    val remoteTransactions = response.data.map { dto -> dto.toTransaction() }
                    val remoteIds = remoteTransactions.map { it.id }.toSet()
                    val remoteCodes = remoteTransactions.map { it.invoiceNumber }.toSet()
                    val uniquePending = localPending.filter { it.id !in remoteIds && it.invoiceNumber !in remoteCodes }
                    Result.success(
                        InvoiceHistoryPage(
                            transactions = uniquePending + remoteTransactions,
                            total = response.total + uniquePending.size.toLong(),
                        ),
                    )
                },
                onFailure = { error ->
                    if (localPending.isNotEmpty()) {
                        Result.success(
                            InvoiceHistoryPage(
                                transactions = localPending,
                                total = localPending.size.toLong(),
                            ),
                        )
                    } else {
                        Result.failure(error)
                    }
                },
            )
        }

    override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> =
        catchingResult {
            val authHeader = runCatching { getAuthHeader() }.getOrNull()
            if (authHeader == null) {
                val localPending = getLocalPendingTransactions()
                return@catchingResult Result.success(
                    InvoiceHistorySummary(
                        ventasNetas = localPending.sumOf { it.amount },
                        totalFacturas = localPending.size,
                        moneda = localPending.firstOrNull()?.currency ?: "USD",
                    ),
                )
            }
            salesApi.getFacturasResumen(authHeader = authHeader, filter = filter).fold(
                onSuccess = { summary ->
                    Result.success(
                        InvoiceHistorySummary(
                            ventasNetas = summary.ventasNetas,
                            totalFacturas = summary.totalFacturas,
                            moneda = summary.moneda,
                        ),
                    )
                },
                onFailure = { error ->
                    val localPending = getLocalPendingTransactions()
                    if (localPending.isNotEmpty()) {
                        Result.success(
                            InvoiceHistorySummary(
                                ventasNetas = localPending.sumOf { it.amount },
                                totalFacturas = localPending.size,
                                moneda = localPending.firstOrNull()?.currency ?: "USD",
                            ),
                        )
                    } else {
                        Result.failure(error)
                    }
                },
            )
        }

    override suspend fun saveTransaction(transaction: Transaction): Result<Unit> {
        // Sales are created via processSale() in SalesRepository, not here.
        return Result.success(Unit)
    }

    override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> =
        catchingResult {
            val authHeader = runCatching { getAuthHeader() }.getOrNull()
            if (authHeader != null && !invoiceId.startsWith("OFF-")) {
                val remote = salesApi.getFacturaDetalle(authHeader, invoiceId)
                if (remote.isSuccess) return@catchingResult remote
            }
            val tenantId = localStore.currentTenantId()
            if (tenantId != null && pendingInvoiceDao != null) {
                val pending = pendingInvoiceDao.getPendingForTenant(tenantId).firstOrNull { it.id == invoiceId }
                if (pending != null) {
                    val req = AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), pending.payloadJson)
                    return@catchingResult Result.success(
                        FacturaDetalleResponseDto(
                            idFactura = pending.id,
                            codFactura = pending.localInvoiceNumber,
                            items =
                                req.items.map { item ->
                                    FacturaDetalleItemDto(
                                        id = item.idItem.toString(),
                                        descripcion = item.itemDescripcion,
                                        cantidad = item.itemCantidadTotal,
                                        precioUnitario = item.itemPrecioSinIva,
                                        totalConIva = item.itemTotalConIva,
                                        codigo = item.itemCodigo,
                                    )
                                },
                        ),
                    )
                }
            }
            Result.failure(IllegalStateException("No se pudo obtener el detalle de la factura $invoiceId"))
        }

    private suspend fun getLocalPendingTransactions(): List<Transaction> {
        val tenantId = localStore.currentTenantId() ?: return emptyList()
        val dao = pendingInvoiceDao ?: return emptyList()
        return dao.getPendingForTenant(tenantId).mapNotNull { entity ->
            runCatching {
                val req = AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), entity.payloadJson)
                Transaction(
                    id = entity.id,
                    invoiceNumber = entity.localInvoiceNumber,
                    time = "--:--",
                    amount = req.factura.totalTotalFactura,
                    currency = req.moneda?.abrMonedaBase ?: "USD",
                    status = TransactionStatus.PENDING,
                    dateHeader = "Pendientes de sincronización",
                    clienteNombre = req.factura.facturarA,
                    clienteIdentificacion = req.factura.facturarARuc,
                    formaPago = req.factura.formaPago,
                    totalRef = req.moneda?.totalRef,
                    abrMonedaSecundaria = req.moneda?.abrMonedaSecundaria,
                )
            }.getOrNull()
        }
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
    if (dateTimeStr.isBlank() || dateTimeStr.length <= DATE_PART_LENGTH) return null
    return dateTimeStr.substring(DATE_PART_LENGTH + 1).take(TIME_LENGTH) // "HH:mm"
}

/** Longitudes fijas del formato "dd/MM/yyyy HH:mm:ss". */
private const val DATE_PART_LENGTH = 10
private const val TIME_LENGTH = 5
