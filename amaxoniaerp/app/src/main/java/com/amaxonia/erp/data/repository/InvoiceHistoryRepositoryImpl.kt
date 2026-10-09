package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.PendingInvoiceDao
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.dto.ElectronicInvoiceResultDto
import com.amaxonia.erp.data.remote.dto.FacturaDetalleItemDto
import com.amaxonia.erp.data.remote.dto.FacturaDetalleResponseDto
import com.amaxonia.erp.data.remote.dto.FacturaSummaryDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.getFacturaDetalle
import com.amaxonia.erp.data.remote.getFacturas
import com.amaxonia.erp.data.remote.getFacturasResumen
import com.amaxonia.erp.data.remote.getInvoicePdf
import com.amaxonia.erp.data.remote.resendElectronicInvoice
import com.amaxonia.erp.domain.model.ElectronicInvoiceStatus
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import com.amaxonia.erp.domain.model.InvoiceHistoryPage
import com.amaxonia.erp.domain.model.InvoiceHistorySummary
import com.amaxonia.erp.domain.model.Transaction
import com.amaxonia.erp.domain.model.TransactionFiscalItem
import com.amaxonia.erp.domain.model.TransactionPaymentMethod
import com.amaxonia.erp.domain.model.TransactionStatus
import com.amaxonia.erp.domain.model.resolveElectronicInvoiceStatus
import com.amaxonia.erp.domain.repository.InvoiceHistoryRepository
import java.io.IOException

class InvoiceHistoryRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val pendingInvoiceDao: PendingInvoiceDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
) : InvoiceHistoryRepository {

    private suspend fun getContext(): Pair<String, String> {
        val session = localStore.readCompanySession()
            ?: error("No hay sesión de empresa activa")
        val adminDb = session.company.adminDb.ifBlank { "default" }
        return Pair(session.token, adminDb)
    }

    private suspend fun tenantId(): String =
        localStore.readCompanySession()?.company?.id?.toString().orEmpty()

    override suspend fun getTransactions(
        filter: InvoiceHistoryFilter,
        limit: Int,
        offset: Long,
    ): Result<InvoiceHistoryPage> = runCatching {
        val localPending = if (offset == 0L) getLocalPendingTransactions() else emptyList()
        val isOnline = networkMonitor?.isOnline() ?: true

        if (!isOnline) {
            return@runCatching InvoiceHistoryPage(
                transactions = localPending,
                total = localPending.size.toLong(),
                isOffline = true,
            )
        }

        val (token, adminDb) = try {
            getContext()
        } catch (e: Exception) {
            return@runCatching InvoiceHistoryPage(
                transactions = localPending,
                total = localPending.size.toLong(),
                isOffline = true,
            )
        }

        try {
            val response = apiService.getFacturas(
                token = token,
                companyDb = adminDb,
                limit = limit,
                offset = offset,
                filter = filter,
            )
            val remoteTransactions = response.data.map { it.toTransaction() }
            val remoteIds = remoteTransactions.map { it.id }.toSet()
            val remoteCodes = remoteTransactions.map { it.invoiceNumber }.toSet()
            val uniquePending = localPending.filter { it.id !in remoteIds && it.invoiceNumber !in remoteCodes }

            InvoiceHistoryPage(
                transactions = uniquePending + remoteTransactions,
                total = response.total + uniquePending.size.toLong(),
                isOffline = false,
            )
        } catch (error: Throwable) {
            if (isNetworkError(error) || localPending.isNotEmpty()) {
                InvoiceHistoryPage(
                    transactions = localPending,
                    total = localPending.size.toLong(),
                    isOffline = true,
                )
            } else {
                throw error
            }
        }
    }

    override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> = runCatching {
        val isOnline = networkMonitor?.isOnline() ?: true
        if (!isOnline) {
            val localPending = getLocalPendingTransactions()
            return@runCatching InvoiceHistorySummary(
                ventasNetas = localPending.sumOf { it.amount },
                totalFacturas = localPending.size,
                moneda = localPending.firstOrNull()?.currency ?: "USD",
            )
        }

        val (token, adminDb) = try {
            getContext()
        } catch (e: Exception) {
            val localPending = getLocalPendingTransactions()
            return@runCatching InvoiceHistorySummary(
                ventasNetas = localPending.sumOf { it.amount },
                totalFacturas = localPending.size,
                moneda = localPending.firstOrNull()?.currency ?: "USD",
            )
        }

        try {
            val summary = apiService.getFacturasResumen(token, adminDb, filter)
            InvoiceHistorySummary(
                ventasNetas = summary.ventasNetas,
                totalFacturas = summary.totalFacturas,
                moneda = summary.moneda,
            )
        } catch (error: Throwable) {
            val localPending = getLocalPendingTransactions()
            if (isNetworkError(error) || localPending.isNotEmpty()) {
                InvoiceHistorySummary(
                    ventasNetas = localPending.sumOf { it.amount },
                    totalFacturas = localPending.size,
                    moneda = localPending.firstOrNull()?.currency ?: "USD",
                )
            } else {
                throw error
            }
        }
    }

    override suspend fun getTransactionById(id: String): Result<Transaction> = runCatching {
        val localPending = getLocalPendingTransactions()
        val localMatch = localPending.firstOrNull { it.id == id || it.invoiceNumber == id }
        if (localMatch != null) return@runCatching localMatch

        val (token, adminDb) = getContext()
        val facturas = apiService.getFacturas(
            token = token,
            companyDb = adminDb,
            limit = 20,
            offset = 0,
            filter = InvoiceHistoryFilter(search = id),
        )
        val remoteMatch = facturas.data.firstOrNull { it.id == id || it.codigo == id }?.toTransaction()
        remoteMatch ?: error("Transacción no encontrada: $id")
    }

    override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> = runCatching {
        val isOnline = networkMonitor?.isOnline() ?: true
        if (isOnline && !invoiceId.startsWith("OFF-")) {
            val (token, adminDb) = try {
                getContext()
            } catch (e: Exception) {
                Pair("", "")
            }
            if (token.isNotBlank()) {
                val remoteDetail = runCatching {
                    apiService.getFacturaDetalle(token, adminDb, invoiceId)
                }
                if (remoteDetail.isSuccess) return@runCatching remoteDetail.getOrThrow()
            }
        }

        val tenant = tenantId()
        val dao = pendingInvoiceDao
        if (tenant.isNotBlank() && dao != null) {
            val pending = dao.getPendingForTenant(tenant).firstOrNull { it.id == invoiceId }
            if (pending != null) {
                val req = AppJson.decodeFromString<ProcessSaleRequestDto>(pending.payloadJson)
                return@runCatching FacturaDetalleResponseDto(
                    idFactura = pending.id,
                    codFactura = pending.localInvoiceNumber,
                    items = req.items.map { item ->
                        FacturaDetalleItemDto(
                            id = item.idItem.toString(),
                            descripcion = item.itemDescripcion,
                            cantidad = item.itemCantidadTotal,
                            precioUnitario = item.itemPrecioSinIva,
                            totalConIva = item.itemTotalConIva,
                            codigo = item.itemCodigo,
                            referencia = item.itemReferencia,
                        )
                    },
                )
            }
        }
        error("No se pudo obtener el detalle de la factura $invoiceId")
    }

    override suspend fun getInvoicePdf(invoiceId: String): Result<ByteArray> = runCatching {
        val (token, adminDb) = getContext()
        apiService.getInvoicePdf(token, adminDb, invoiceId)
    }

    override suspend fun resendElectronicInvoice(invoiceId: String): Result<ElectronicInvoiceResultDto> = runCatching {
        val (token, adminDb) = getContext()
        apiService.resendElectronicInvoice(token, adminDb, invoiceId)
    }

    private suspend fun getLocalPendingTransactions(): List<Transaction> {
        val tenant = tenantId()
        val dao = pendingInvoiceDao
        if (tenant.isBlank() || dao == null) return emptyList()

        return dao.getPendingForTenant(tenant).mapNotNull { entity ->
            runCatching {
                val req = AppJson.decodeFromString<ProcessSaleRequestDto>(entity.payloadJson)
                Transaction(
                    id = entity.id,
                    invoiceNumber = entity.localInvoiceNumber,
                    time = "--:--",
                    amount = req.factura.totalTotalFactura,
                    currency = req.moneda?.abrMonedaBase ?: "USD",
                    status = TransactionStatus.PENDING,
                    electronicStatus = ElectronicInvoiceStatus.PENDING,
                    dateHeader = "Pendientes de sincronización",
                    clienteNombre = req.factura.facturarA,
                    clienteIdentificacion = req.factura.facturarARuc,
                    formaPago = req.factura.formaPago,
                    paymentMethods = req.pagos.map { p ->
                        TransactionPaymentMethod(
                            description = p.siglas.orEmpty().ifBlank { "Pago" },
                            sigla = p.siglas.orEmpty(),
                            amount = p.monto,
                        )
                    },
                    fiscalItems = req.items.map { item ->
                        TransactionFiscalItem(
                            description = item.itemDescripcion,
                            quantity = item.itemCantidadTotal,
                            unitPriceWithoutTax = item.itemPrecioSinIva,
                            iva = item.itemPIva,
                        )
                    },
                    totalRef = req.moneda?.totalRef,
                    abrMonedaSecundaria = req.moneda?.abrMonedaSecundaria,
                )
            }.getOrNull()
        }
    }

    private fun FacturaSummaryDto.toTransaction(): Transaction {
        val transStatus = resolveTransactionStatus(estatus)
        val elecStatus = resolveElectronicInvoiceStatus(estatus, transStatus, codigoFiscal)
        val dateHeader = fecha.ifBlank { "Sin fecha" }
        val time = extractTime(fechaCreacion) ?: extractTime(fechaDgi) ?: "--:--"

        return Transaction(
            id = id,
            invoiceNumber = codigo,
            time = time,
            amount = total,
            currency = moneda,
            status = transStatus,
            electronicStatus = elecStatus,
            codigoFiscal = codigoFiscal,
            numeroDocumentoFiscal = numeroDocumentoFiscal,
            fechaDgi = fechaDgi,
            dateHeader = dateHeader,
            clienteNombre = clienteNombre,
            clienteIdentificacion = clienteIdentificacion,
            formaPago = formaPago,
            totalRef = totalRef,
            abrMonedaSecundaria = abrMonedaSecundaria,
        )
    }

    private fun resolveTransactionStatus(estatus: String): TransactionStatus =
        when {
            estatus.equals("Anulada", ignoreCase = true) ||
                estatus.equals("Anulado", ignoreCase = true) -> TransactionStatus.CANCELLED
            estatus.equals("En Espera", ignoreCase = true) ||
                estatus.equals("Pendiente", ignoreCase = true) -> TransactionStatus.PENDING
            else -> TransactionStatus.PAID
        }

    private fun extractTime(dateTimeStr: String): String? {
        if (dateTimeStr.isBlank() || dateTimeStr.length <= DATE_PART_LENGTH) return null
        return dateTimeStr.substring(DATE_PART_LENGTH + 1).take(TIME_LENGTH)
    }

    private val networkErrorKeywords = listOf(
        "failed to connect",
        "unable to resolve host",
        "network is unreachable",
        "connection refused",
        "timeout",
    )

    private fun isNetworkError(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            val msg = cause.message?.lowercase().orEmpty()
            if (cause is IOException || networkErrorKeywords.any { msg.contains(it) }) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private companion object {
        const val DATE_PART_LENGTH = 10
        const val TIME_LENGTH = 5
    }
}
