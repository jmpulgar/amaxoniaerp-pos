package com.amaxonia.pos.data.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.saveActiveCaja
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.local.saveSelectedPrinterType
import com.amaxonia.pos.data.repository.FakeSecureKeyValueStore
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionFiscalItem
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.domain.repository.SalesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultInvoicePrintGatewayTest {

    private lateinit var localStore: LocalStore
    private val printedTickets = mutableListOf<TicketDocument>()

    private val fakeTicketPrinter = object : TicketPrinter {
        override suspend fun connect(): PrintResult = PrintResult.Success
        override suspend fun disconnect() {}
        override suspend fun isAvailable(): Boolean = true
        override suspend fun printText(text: String): PrintResult = PrintResult.Success
        override suspend fun printTicket(ticket: TicketDocument): PrintResult {
            printedTickets.add(ticket)
            return PrintResult.Success
        }
    }

    private val fakePrinterProvider = object : PrinterProvider {
        override fun getActivePrinter(): PrinterRepository? = null
        override fun getActiveTicketPrinter(): TicketPrinter = fakeTicketPrinter
    }

    private val fakeSalesRepository = object : SalesRepository {
        override suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> =
            error("Not used")

        override suspend fun findByCorrelationId(clientCorrelationId: String): Result<ReconciledInvoice?> =
            error("Not used")

        override suspend fun confirmFacturaFiscal(
            facturaId: String,
            payload: ConfirmFacturaFiscalRequestDto,
        ): Result<ConfirmFacturaFiscalResponseDto> = error("Not used")

        override suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto> =
            Result.failure(IllegalStateException("Network unavailable"))

        override suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto> =
            error("Not used")
    }

    private val testCompany = CompanySessionSnapshot(
        token = "test-token",
        company = CompanyDetailsSnapshot(
            id = 1,
            name = "TEST RESTAURANT CORP",
            adminDb = "admin",
            accountingDb = "acc",
            payrollDb = "pay",
            rif = "J-12345678-0",
        ),
    )

    private val testCaja = Caja(
        idCaja = "1",
        codCaja = "01",
        descripcion = "Caja 1",
        estatus = 1,
        idSucursal = 1,
        serieCaja = "P01",
    )

    private val testTransaction = Transaction(
        id = "OFF-1700000000",
        invoiceNumber = "OFF-1700000000",
        time = "12:00 PM",
        amount = 25.00,
        currency = "USD",
        status = TransactionStatus.PENDING,
        dateHeader = "Miércoles, 26 Agosto 2026",
        clienteNombre = "Carlos Gonzalez",
        clienteIdentificacion = "8-765-4321",
        formaPago = "EFECTIVO",
        paymentMethods = listOf(
            TransactionPaymentMethod(description = "Efectivo", sigla = "CASH", amount = 25.00),
        ),
        fiscalItems = listOf(
            TransactionFiscalItem(description = "Menu Ejecutivo", quantity = 2.0, unitPriceWithoutTax = 12.50, iva = 0.0),
        ),
    )

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localStore = LocalStore(context, FakeSecureKeyValueStore())
        localStore.saveSelectedCountry(ServerCountries.PANAMA)
        localStore.saveSelectedPrinterType(PrinterType.SUNMI_V2)
        localStore.saveCompanySession(testCompany)
        localStore.saveActiveCaja(testCaja)
        printedTickets.clear()
    }

    @Test
    fun printsOfflineSunmiTicketUsingLocalFallbackForPanama() = runTest {
        val gateway = DefaultInvoicePrintGateway(fakePrinterProvider, localStore, fakeSalesRepository)

        val feedback = gateway.print(
            countryCode = "PA",
            transaction = testTransaction,
            remoteInvoiceId = "OFF-1700000000",
        )

        assertNotNull(feedback)
        assertEquals("Ticket SUNMI enviado correctamente", feedback?.displayMessage)
        assertEquals(1, printedTickets.size)

        val ticket = printedTickets.first()
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        assertTrue(texts.contains("TEST RESTAURANT CORP"))
        assertTrue(texts.any { it.contains("Menu Ejecutivo") })
    }

    @Test
    fun printsOfflineSunmiTicketUsingLocalFallbackForVenezuela() = runTest {
        localStore.saveSelectedCountry(ServerCountries.VENEZUELA)
        localStore.saveSelectedPrinterType(PrinterType.SUNMI_V2)
        val gateway = DefaultInvoicePrintGateway(fakePrinterProvider, localStore, fakeSalesRepository)

        val feedback = gateway.print(
            countryCode = "VE",
            transaction = testTransaction,
            remoteInvoiceId = "OFF-VE-100",
        )

        assertNotNull(feedback)
        assertEquals("Ticket SUNMI enviado correctamente", feedback?.displayMessage)
        assertEquals(1, printedTickets.size)

        val ticket = printedTickets.first()
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        assertTrue(texts.contains("FACTURA"))
        assertTrue(texts.contains("TEST RESTAURANT CORP"))
    }
}
