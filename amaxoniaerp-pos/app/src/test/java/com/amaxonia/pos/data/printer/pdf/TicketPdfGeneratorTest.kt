package com.amaxonia.pos.data.printer.pdf

import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TicketPdfGeneratorTest {
    @Test
    fun generatesValidPdfBytesFromTicketDocument() {
        val document =
            TicketDocument(
                elements =
                    listOf(
                        TicketElement.Text("TIENDA CENTRAL S.A.", TicketAlign.CENTER, bold = true),
                        TicketElement.Text("RUC: 12345-1-67890", TicketAlign.CENTER),
                        TicketElement.Divider,
                        TicketElement.Text("Producto 1 (7%)", TicketAlign.LEFT, bold = true),
                        TicketElement.Text("  PROMO: 2X1 VERANO", TicketAlign.LEFT),
                        TicketElement.Columns(
                            values = listOf("P01", "2", "UND", "5.00", "10.00"),
                            widths = listOf(8, 4, 5, 7, 8),
                            aligns =
                                listOf(
                                    TicketAlign.LEFT,
                                    TicketAlign.CENTER,
                                    TicketAlign.CENTER,
                                    TicketAlign.RIGHT,
                                    TicketAlign.RIGHT,
                                ),
                        ),
                        TicketElement.TotalsRow(
                            label = "Total:",
                            value = "10.70",
                            labelWidth = 18,
                            printerWidth = 32,
                            bold = true,
                        ),
                        TicketElement.Qr("https://fe.dgi.mef.gob.pa/test"),
                        TicketElement.Feed(2),
                    ),
            )

        val pdfBytes = TicketPdfGenerator.generatePdf(document)
        assertTrue("PDF bytes must not be empty", pdfBytes.isNotEmpty())
        val header = String(pdfBytes.take(5).toByteArray())
        assertTrue("File header must start with %PDF: $header", header.startsWith("%PDF"))
    }
}
