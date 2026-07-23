package com.amaxonia.pos.domain.usecase.caja

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotePrintResult
import com.amaxonia.pos.domain.model.creditnote.ReceiptPrintResult
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashClosePrintingServiceTest {
    @Test
    fun `report messages preserve X and Z behavior`() =
        runTest {
            val service = service(FixedPrinterRepository(Result.success(Unit), Result.failure(IllegalStateException("paper"))))

            assertEquals(CashClosePrintOutcome.Message("Reporte X impreso correctamente"), service.printReport(FiscalReportType.X))
            assertEquals(CashClosePrintOutcome.Message("Error Reporte Z: paper"), service.printReport(FiscalReportType.Z))
        }

    @Test
    fun `close ticket offer supports Panama and Venezuela with Sunmi`() =
        runTest {
            val ticketPrinter = RecordingTicketPrinter()

            assertTrue(service(ticketPrinter = ticketPrinter).shouldOfferCloseTicket())
            assertTrue(service(country = "VE", ticketPrinter = ticketPrinter).shouldOfferCloseTicket())
            assertFalse(service(ticketPrinter = null).shouldOfferCloseTicket())
        }

    @Test
    fun `successful close ticket keeps exact user message`() =
        runTest {
            val ticketPrinter = RecordingTicketPrinter()

            val result = service(ticketPrinter = ticketPrinter).printCloseTicket(payload())

            assertEquals(CashClosePrintOutcome.Message("Ticket de cierre impreso correctamente"), result)
            assertEquals(TicketDocument(emptyList()), ticketPrinter.lastDocument)
        }

    private fun service(
        printer: PrinterRepository? = FixedPrinterRepository(Result.success(Unit), Result.success(Unit)),
        country: String = "PA",
        ticketPrinter: TicketPrinter? = RecordingTicketPrinter(),
    ) = CashClosePrintingService(
        object : PrinterProvider {
            override fun getActivePrinter(): PrinterRepository? = printer

            override fun getActiveTicketPrinter(): TicketPrinter? = ticketPrinter
        },
        object : CashCloseContextReader {
            override suspend fun currentCountryCode(): String = country

            override suspend fun selectedPrinterType(): PrinterType = PrinterType.SUNMI_V2

            override suspend fun currentCompany(): CompanyIdentity? = null
        },
        object : CashCloseTicketFormatter {
            override val paymentLabels: List<String> = emptyList()

            override fun format(payload: CashCloseTicketPayload): TicketDocument = TicketDocument(emptyList())
        },
    )

    private class FixedPrinterRepository(
        private val reportX: Result<Unit>,
        private val reportZ: Result<Unit>,
    ) : PrinterRepository {
        override suspend fun printReceipt(transaction: Transaction): Result<ReceiptPrintResult> = error("not used")

        override suspend fun printCreditNote(document: CreditNoteFiscalDocumentDto): Result<CreditNotePrintResult> = error("not used")

        override suspend fun printReportX(): Result<Unit> = reportX

        override suspend fun printReportZ(): Result<Unit> = reportZ
    }

    private class RecordingTicketPrinter : TicketPrinter {
        var lastDocument: TicketDocument? = null

        override suspend fun connect(): PrintResult = PrintResult.Success

        override suspend fun disconnect() = Unit

        override suspend fun isAvailable(): Boolean = true

        override suspend fun printText(text: String): PrintResult = PrintResult.Success

        override suspend fun printTicket(ticket: TicketDocument): PrintResult {
            lastDocument = ticket
            return PrintResult.Success
        }
    }

    private fun payload() =
        CashCloseTicketPayload(
            companyName = "Company",
            companyRuc = "RUC",
            companyAddress = "Branch",
            companyPhone = "",
            sellerName = "Seller",
            cashRegisterName = "Caja",
            branchName = "Branch",
            summary = CierreCajaSummary(),
            paymentAmounts = emptyMap(),
            generalPayments = 0.0,
            promotionLines = emptyList(),
            inventoryLines = emptyList(),
            qrPayload = "qr",
        )
}
