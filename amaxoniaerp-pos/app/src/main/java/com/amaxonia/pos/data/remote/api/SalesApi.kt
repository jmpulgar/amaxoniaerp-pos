package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasResumenDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter

interface SalesApi {
    suspend fun processSale(
        authHeader: String,
        payload: ProcessSaleRequestDto,
    ): Result<ProcessSaleResponseDto>

    suspend fun getFacturas(
        authHeader: String,
        limit: Int = 100,
        offset: Long = 0,
        filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
    ): Result<FacturasListResponseDto>

    suspend fun getFacturasResumen(
        authHeader: String,
        filter: InvoiceHistoryFilter = InvoiceHistoryFilter(),
    ): Result<FacturasResumenDto>

    suspend fun getFacturaDetalle(
        authHeader: String,
        facturaId: String,
    ): Result<FacturaDetalleResponseDto>

    /**
     * Resolves an existing invoice by its canonical `idFactura`. Returns the
     * invoice when found, `null` when the backend has no row for that id, or a
     * failure when the backend is unreachable (auditoría ítem 2).
     */
    suspend fun findByCorrelationId(
        authHeader: String,
        clientCorrelationId: String,
    ): Result<ReconciledInvoice?>

    suspend fun confirmFacturaFiscal(
        authHeader: String,
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto,
    ): Result<ConfirmFacturaFiscalResponseDto>

    suspend fun getPrintPayload(
        authHeader: String,
        facturaId: String,
    ): Result<FacturaPrintPayloadDto>

    suspend fun sendReceiptEmail(
        authHeader: String,
        facturaId: String,
    ): Result<EnviarCorreoFacturaResponseDto>
}
