package com.amaxonia.pos.domain.usecase.creditnote

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult
import com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.CreditNoteFiscalConfirmationRepository
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProcessCreditNoteFiscalUseCaseTest {
    @Test
    fun `non Venezuela configuration leaves detail untouched`() =
        runTest {
            val original = detail()
            val useCase = ProcessCreditNoteFiscalUseCase(unusedConfirmation(), EmptyPrinterProvider, context("PA"))

            val result = useCase(original)

            assertSame(original, result.detail)
            assertEquals(null, result.errorMessage)
        }

    @Test
    fun `successful HKA print confirms fiscal identifiers`() =
        runTest {
            var confirmedId = ""
            val confirmation =
                CreditNoteFiscalConfirmationRepository { id, _ ->
                    confirmedId = id
                    Result.success(
                        ConfirmCreditNoteFiscalResponseDto(
                            true,
                            id,
                            "NC-1",
                            CreditNoteFiscalStatusDto.CONFIRMADA,
                            "FISCAL-9",
                            "FISCAL-9",
                            "SERIAL-7",
                        ),
                    )
                }
            val useCase =
                ProcessCreditNoteFiscalUseCase(
                    confirmation,
                    FixedPrinterProvider(Result.success(CreditNotePrintResult("FISCAL-9", "SERIAL-7"))),
                    context("VE"),
                )

            val result = useCase(detail())

            assertEquals("credit-1", confirmedId)
            assertEquals(CreditNoteFiscalStatusDto.CONFIRMADA, result.detail.fiscalStatus)
            assertEquals("FISCAL-9", result.detail.fiscalNumber)
            assertEquals("SERIAL-7", result.detail.printerSerial)
            assertEquals(null, result.errorMessage)
        }

    @Test
    fun `confirmation failure preserves printed detail and legacy fallback message`() =
        runTest {
            val useCase =
                ProcessCreditNoteFiscalUseCase(
                    CreditNoteFiscalConfirmationRepository { _, _ -> Result.failure(IllegalStateException()) },
                    FixedPrinterProvider(Result.success(CreditNotePrintResult("FISCAL-9", "SERIAL-7"))),
                    context("VE"),
                )

            val result = useCase(detail())

            assertEquals(CreditNoteFiscalStatusDto.PENDIENTE, result.detail.fiscalStatus)
            assertEquals(
                "La impresión fiscal salió bien, pero no se pudo confirmar en el backend",
                result.errorMessage,
            )
        }

    private fun detail() =
        CreditNoteDetailDto(
            id = "credit-1",
            codigo = "NC-1",
            facturaId = "invoice-1",
            facturaCodigo = "F-1",
            fecha = "2026-07-13",
            periodo = "2026-07",
            observacion = "",
            clienteNombre = "Cliente",
            clienteIdentificacion = "ID",
            subtotal = 10.0,
            impuesto = 0.0,
            total = 10.0,
            fiscalStatus = CreditNoteFiscalStatusDto.PENDIENTE,
            anulaFacturaCompleta = true,
            lines = emptyList(),
            fiscalDocument =
                CreditNoteFiscalDocumentDto(
                    "credit-1",
                    "NC-1",
                    "2026-07-13",
                    "Cliente",
                    "ID",
                    "Address",
                    "Phone",
                    "F-1",
                    "ORIGINAL",
                    "2026-07-12",
                    "",
                    "",
                    emptyList(),
                ),
        )

    private fun context(country: String): CreditNoteContextReader =
        object : CreditNoteContextReader {
            override suspend fun currentCountryCode(): String = country

            override suspend fun selectedPrinterType(): PrinterType = PrinterType.THE_FACTORY_HKA
        }

    private fun unusedConfirmation() = CreditNoteFiscalConfirmationRepository { _, _ -> Result.failure(AssertionError("must not confirm")) }

    private object EmptyPrinterProvider : PrinterProvider {
        override fun getActivePrinter(): PrinterRepository? = null

        override fun getActiveTicketPrinter(): TicketPrinter? = null
    }

    private class FixedPrinterProvider(
        private val result: Result<CreditNotePrintResult>,
    ) : PrinterProvider {
        override fun getActivePrinter(): PrinterRepository =
            object : PrinterRepository {
                override suspend fun printReceipt(transaction: Transaction): Result<ReceiptPrintResult> =
                    Result.failure(AssertionError("not used"))

                override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult> = result

                override suspend fun printReportX(): Result<Unit> = Result.failure(AssertionError("not used"))

                override suspend fun printReportZ(): Result<Unit> = Result.failure(AssertionError("not used"))
            }

        override fun getActiveTicketPrinter(): TicketPrinter? = null
    }
}
