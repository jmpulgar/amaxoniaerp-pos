package com.amaxonia.pos.data.printer.senraise

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import recieptservice.com.recieptservice.PrinterInterface

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SenraiseH10PrinterTest {
    private lateinit var context: Context
    private val printedLines = mutableListOf<String>()
    private val alignments = mutableListOf<Int>()
    private val boldStates = mutableListOf<Boolean>()
    private var qrPayload: String? = null

    private val fakeService =
        object : PrinterInterface.Stub() {
            override fun printEpson(data: ByteArray?) = Unit

            override fun getServiceVersion(): String = "1.0.0"

            override fun printText(text: String?) {
                text?.let { printedLines.add(it) }
            }

            override fun printBitmap(pic: Bitmap?) = Unit

            override fun printBarCode(data: String?, symbology: Int, height: Int, width: Int) = Unit

            override fun printQRCode(data: String?, modulesize: Int, errorlevel: Int) {
                qrPayload = data
            }

            override fun setAlignment(alignment: Int) {
                alignments.add(alignment)
            }

            override fun setTextSize(textSize: Float) = Unit

            override fun nextLine(line: Int) = Unit

            override fun printTableText(text: Array<out String>?, weight: IntArray?, alignment: IntArray?) {
                text?.let { printedLines.add(it.joinToString(" | ")) }
            }

            override fun setTextBold(bold: Boolean) {
                boldStates.add(bold)
            }
        }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        printedLines.clear()
        alignments.clear()
        boldStates.clear()
        qrPayload = null
    }

    @Test
    fun printTextSendsTextToService() =
        runTest {
            val fakeManager = FakeSenraisePrinterManager(context, fakeService, isConnected = true)
            val printer = SenraiseH10Printer(context, fakeManager)

            val result = printer.printText("Hola Senraise")

            assertTrue(result is PrintResult.Success)
            assertTrue(printedLines.any { it.contains("Hola Senraise") })
        }

    @Test
    fun printTicketPrintsAllElements() =
        runTest {
            val fakeManager = FakeSenraisePrinterManager(context, fakeService, isConnected = true)
            val printer = SenraiseH10Printer(context, fakeManager)

            val ticket =
                TicketDocument(
                    elements =
                        listOf(
                            TicketElement.Text("TITULO", TicketAlign.CENTER, bold = true),
                            TicketElement.Divider,
                            TicketElement.Columns(
                                values = listOf("Item 1", "x1", "$10.00"),
                                widths = listOf(2, 1, 1),
                                aligns = listOf(TicketAlign.LEFT, TicketAlign.CENTER, TicketAlign.RIGHT),
                            ),
                            TicketElement.TotalsRow(label = "Total:", value = "$10.00", labelWidth = 16, printerWidth = 32, bold = true),
                            TicketElement.Qr("https://example.com/invoice/123", size = 4),
                            TicketElement.Feed(2),
                        ),
                )

            val result = printer.printTicket(ticket)

            assertTrue(result is PrintResult.Success)
            assertTrue(printedLines.any { it.contains("TITULO") })
            assertTrue(printedLines.any { it.contains("Item 1 | x1 | $10.00") })
            assertTrue(printedLines.any { it.contains("Total:") })
            assertEquals("https://example.com/invoice/123", qrPayload)
        }

    @Test
    fun returnsErrorWhenServiceNotAvailable() =
        runTest {
            val fakeManager = FakeSenraisePrinterManager(context, null, isConnected = false)
            val printer = SenraiseH10Printer(context, fakeManager)

            val result = printer.printText("Test")

            assertTrue(result is PrintResult.Error)
            assertEquals("Servicio de impresión Senraise no disponible", (result as PrintResult.Error).message)
        }
}

private class FakeSenraisePrinterManager(
    context: Context,
    private val stub: PrinterInterface?,
    private val isConnected: Boolean,
) : SenraisePrinterManager(context) {
    override suspend fun bind(): Boolean = isConnected
    override fun getInterface(): PrinterInterface? = stub
}
