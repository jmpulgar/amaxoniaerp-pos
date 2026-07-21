package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice

interface SalesRepository {
    suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto>

    /**
     * Looks up a previously-processed sale by its canonical [clientCorrelationId]
     * (a.k.a. `idFactura`). Used by the reconciliation step after an HTTP 409
     * Conflict on a retried submission (auditoría ítem 2 / INT-BE-001).
     *
     * Contract:
     * - Returns [Result.success] wrapping [ReconciledInvoice] when the backend
     *   exposes the prior invoice under that id.
     * - Returns [Result.success]` of `null` when the backend does not know the
     *   id (caller falls back to [PaymentFlowResult.DuplicateInvoice]).
     * - Returns [Result.failure] when the backend is unreachable; caller must
     *   NOT silently promote the conflict to an automatic approval.
     */
    suspend fun findByCorrelationId(clientCorrelationId: String): Result<ReconciledInvoice?>

    suspend fun confirmFacturaFiscal(
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto,
    ): Result<ConfirmFacturaFiscalResponseDto>

    suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto>

    suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto>
}
