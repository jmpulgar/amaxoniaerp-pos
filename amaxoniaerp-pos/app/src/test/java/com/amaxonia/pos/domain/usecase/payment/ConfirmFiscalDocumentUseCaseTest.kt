package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.repository.SalesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmFiscalDocumentUseCaseTest {
    @Test
    fun `forwards the exact fiscal identity and maps success to unit`() =
        runTest {
            val repository = CapturingSalesRepository(Result.success(confirmation()))

            val result = ConfirmFiscalDocumentUseCase(repository)("invoice-7", "fiscal-9", "printer-2")

            assertTrue(result.isSuccess)
            assertEquals("invoice-7", repository.invoiceId)
            assertEquals("fiscal-9", repository.payload?.numeroDocumentoFiscal)
            assertEquals("printer-2", repository.payload?.impresoraSerial)
        }

    @Test
    fun `preserves repository failure without hiding it`() =
        runTest {
            val failure = IllegalStateException("confirmation unavailable")
            val repository = CapturingSalesRepository(Result.failure(failure))

            val result = ConfirmFiscalDocumentUseCase(repository)("invoice", "fiscal", "printer")

            assertSame(failure, result.exceptionOrNull())
        }

    private class CapturingSalesRepository(
        private val confirmationResult: Result<ConfirmFacturaFiscalResponseDto>,
    ) : SalesRepository {
        var invoiceId: String? = null
        var payload: ConfirmFacturaFiscalRequestDto? = null

        override suspend fun confirmFacturaFiscal(
            facturaId: String,
            payload: ConfirmFacturaFiscalRequestDto,
        ): Result<ConfirmFacturaFiscalResponseDto> {
            invoiceId = facturaId
            this.payload = payload
            return confirmationResult
        }

        override suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> = unused()

        override suspend fun findByCorrelationId(clientCorrelationId: String): Result<ReconciledInvoice?> = unused()

        override suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto> = unused()

        override suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto> = unused()

        private fun <T> unused(): Result<T> = Result.failure(UnsupportedOperationException("Not used"))
    }

    private fun confirmation() =
        ConfirmFacturaFiscalResponseDto(
            success = true,
            id = "confirmation",
            codigo = "OK",
            numeroDocumentoFiscal = "fiscal-9",
            codFacturaFiscal = "",
            impresoraSerial = "printer-2",
        )
}
