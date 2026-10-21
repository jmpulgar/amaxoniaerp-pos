package com.amaxonia.pos.data.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.repository.FakeSecureKeyValueStore
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.repository.PrinterRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultCreditNoteTicketPrinterTest {
    private lateinit var context: Context
    private lateinit var localStore: LocalStore
    private val printedTickets = mutableListOf<TicketDocument>()

    private val fakeTicketPrinter =
        object : TicketPrinter {
            override suspend fun connect(): PrintResult = PrintResult.Success

            override suspend fun disconnect() = Unit

            override suspend fun isAvailable(): Boolean = true

            override suspend fun printText(text: String): PrintResult = PrintResult.Success

            override suspend fun printTicket(ticket: TicketDocument): PrintResult {
                printedTickets.add(ticket)
                return PrintResult.Success
            }
        }

    private val fakePrinterProvider =
        object : PrinterProvider {
            var currentTicketPrinter: TicketPrinter? = fakeTicketPrinter

            override fun getActivePrinter(): PrinterRepository? = null

            override fun getActiveTicketPrinter(): TicketPrinter? = currentTicketPrinter
        }

    @Before
    fun setUp() =
        runTest {
            context = ApplicationProvider.getApplicationContext()
            localStore = LocalStore(context, secureStore = FakeSecureKeyValueStore())
            localStore.saveCompanySession(
                CompanySessionSnapshot(
                    token = "tok",
                    company =
                        CompanyDetailsSnapshot(
                            id = 1,
                            name = "TORREFACTORA VALLE CHIRIQUI",
                            adminDb = "torrefactora_administrativo",
                            accountingDb = "",
                            payrollDb = "",
                            rif = "155673336-2-2018",
                        ),
                ),
            )
        }

    @Test
    fun printsWithPhysicalPrinterWhenAvailable() =
        runTest {
            fakePrinterProvider.currentTicketPrinter = fakeTicketPrinter
            val printer =
                DefaultCreditNoteTicketPrinter(
                    printerProvider = fakePrinterProvider,
                    localStore = localStore,
                    contextProvider = { context },
                )
            val detail = createDetail()

            val result = printer(detail)

            assertTrue(result.isSuccess)
            assertEquals(1, printedTickets.size)
        }

    @Test
    fun generatesAndSavesPdfWhenNoPrinterAvailable() =
        runTest {
            fakePrinterProvider.currentTicketPrinter = null
            val printer =
                DefaultCreditNoteTicketPrinter(
                    printerProvider = fakePrinterProvider,
                    localStore = localStore,
                    contextProvider = { context },
                )
            val detail = createDetail()

            val result = printer(detail)

            assertTrue(result.isSuccess)
            val pdfFile = File(context.cacheDir, "ticket_nc_NC-001-00001.pdf")
            assertTrue(pdfFile.exists())
            assertTrue(pdfFile.length() > 0)
        }

    private fun createDetail() =
        CreditNoteDetailDto(
            id = "nc-1",
            codigo = "NC-001-00001",
            facturaId = "fact-1",
            facturaCodigo = "001-00001",
            fecha = "2026-10-21",
            periodo = "2026-10",
            observacion = "Devolucion",
            clienteNombre = "CONSUMIDOR FINAL",
            clienteIdentificacion = "910837884",
            subtotal = 100.0,
            impuesto = 0.0,
            total = 100.0,
            fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
            fiscalNumber = "3475",
            anulaFacturaCompleta = true,
            cufe = "FE0420000123456789",
            qr = "https://dgi-fep.mef.gob.pa/Consultas/FacturasPorQR?chFE=FE04",
            lines =
                listOf(
                    CreditNoteDetailLineDto(
                        id = "line-1",
                        idDetalleFactura = "det-1",
                        idItem = 1,
                        descripcion = "CAFE VALLE",
                        codigo = "745110",
                        referencia = "",
                        cantidad = 1.0,
                        precioSinIva = 100.0,
                        descuentoPorcentaje = 0.0,
                        descuentoMonto = 0.0,
                        pIva = 0.0,
                        totalSinIva = 100.0,
                        totalConIva = 100.0,
                    ),
                ),
            fiscalDocument =
                CreditNoteFiscalDocumentDto(
                    creditNoteId = "nc-1",
                    creditNoteCode = "NC-001-00001",
                    date = "2026-10-21",
                    customerName = "CONSUMIDOR FINAL",
                    customerIdentifier = "910837884",
                    customerAddress = "PANAMA",
                    customerPhone = "9999-9999",
                    originalInvoiceCode = "001-00001",
                    originalFiscalNumber = "3438",
                    originalInvoiceDate = "2026-10-20",
                    printerSerial = "",
                    comment = "Devolucion",
                    lines = emptyList(),
                ),
        )
}
