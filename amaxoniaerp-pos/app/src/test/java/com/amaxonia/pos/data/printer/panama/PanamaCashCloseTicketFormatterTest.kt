package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.caja.CashCloseInventoryLine
import com.amaxonia.pos.domain.model.caja.CashClosePromotionLine
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.printer.TicketElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PanamaCashCloseTicketFormatterTest {
    @Test
    fun `cash close ticket matches characterized golden`() {
        val actual = PanamaCashCloseTicketFormatter().format(payload()).elements.joinToString("\n", transform = ::serialize)
        val uri = checkNotNull(javaClass.classLoader?.getResource("golden/panama-cash-close-ticket.txt")).toURI()
        val expected = File(uri).readText().trimEnd()

        assertEquals(expected, actual)
    }

    @Test
    fun `Venezuela cash close uses RIF label`() {
        val text =
            PanamaCashCloseTicketFormatter()
                .format(payload(), "VE")
                .elements
                .filterIsInstance<TicketElement.Text>()
                .joinToString("\n") { it.value }

        assertTrue(text.contains("RIF: RUC-1"))
    }

    private fun payload(): CashCloseTicketPayload =
        CashCloseTicketPayload(
            companyName = "Acme",
            companyRuc = "RUC-1",
            companyAddress = "Panama",
            companyPhone = "555",
            sellerName = "Seller",
            cashRegisterName = "Caja 1",
            branchName = "Sucursal 1",
            summary =
                CierreCajaSummary(
                    totalSales = 15.0,
                    expectedClose = 16.0,
                    montoEfectivoEntrada = 2.0,
                    montoEfectivoSalida = 1.0,
                    montoCierre = 16.0,
                    montoDiferencia = 0.0,
                ),
            paymentAmounts = mapOf("EFECTIVO" to 10.0),
            generalPayments = 0.5,
            promotionLines = listOf(CashClosePromotionLine("PROMO", "Promocion 1", 2.0)),
            inventoryLines = listOf(CashCloseInventoryLine("P1", "Product", 5.0, 2.0, 3.0)),
            qrPayload = "close-qr",
        )

    private fun serialize(element: TicketElement): String =
        when (element) {
            is TicketElement.Text -> "TEXT|${element.align}|${element.bold}|${element.value}"
            is TicketElement.Columns ->
                "COLUMNS|${element.values.joinToString("~")}|${element.widths.joinToString(",")}|" +
                    element.aligns.joinToString(",")
            is TicketElement.TotalsRow ->
                "TOTALS|${element.bold}|${element.labelWidth}|${element.printerWidth}|${element.label}|${element.value}"
            is TicketElement.Qr -> "QR|${element.size}|${element.value}"
            is TicketElement.Feed -> "FEED|${element.lines}"
            TicketElement.Divider -> "DIVIDER"
        }
}
