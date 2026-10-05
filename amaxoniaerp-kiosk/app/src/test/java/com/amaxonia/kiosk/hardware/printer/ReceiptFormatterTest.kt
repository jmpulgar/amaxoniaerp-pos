package com.amaxonia.kiosk.hardware.printer

import com.amaxonia.kiosk.core.network.KioskInvoiceInfo
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskReceipt
import com.amaxonia.kiosk.core.network.KioskReceiptLine
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {
    private val samplePayment =
        KioskPaymentResponse(
            orderNumber = "K1-042",
            status = "FISCAL_SUCCESS",
            dispatch = "RETIRO_MOSTRADOR",
            invoice =
                KioskInvoiceInfo(
                    codFactura = "FAC-2026-0001",
                    cufe = "CUFE-PANAMA-998877665544332211",
                    qr = "https://dgi.mef.gob.pa/fe/998877",
                    fechaRecepcionDGI = "2026-10-05 16:30:00",
                ),
            receipt =
                KioskReceipt(
                    companyName = "Amaxonia Burgers",
                    ruc = "155688941-2-2018",
                    dv = "42",
                    address = "Via España, Ciudad de Panama",
                    orderNumber = "K1-042",
                    diningMode = "COMER_AQUI",
                    tableTent = "17",
                    customerName = "Juan Perez",
                    customerId = "8-123-456",
                    date = "2026-10-05 16:30:00",
                    lines =
                        listOf(
                            KioskReceiptLine(
                                qty = 2,
                                description = "Hamburguesa Doble",
                                price = "5.00",
                                total = "10.00",
                                modifiers = listOf("Pan Artesanal", "Extra Queso"),
                            ),
                            KioskReceiptLine(
                                qty = 1,
                                description = "Papas Grandes",
                                price = "2.50",
                                total = "2.50",
                                modifiers = emptyList(),
                            ),
                        ),
                    subtotal = "12.50",
                    tax = "0.88",
                    total = "13.38",
                    paymentBrand = "MASTERCARD",
                    paymentLast4 = "1234",
                    paymentAuthCode = "AUT987654",
                    paymentReference = "REF883311",
                ),
        )

    @Test
    fun `formatCustomerReceipt formats full fiscal ticket with CUFE and breakdown`() {
        val formatted = ReceiptFormatter.formatCustomerReceipt(samplePayment)

        assertTrue(formatted.contains("Amaxonia Burgers"))
        assertTrue(formatted.contains("RUC: 155688941-2-2018-42"))
        assertTrue(formatted.contains("Via España, Ciudad de Panama"))
        assertTrue(formatted.contains("*** ORDEN: K1-042 ***"))
        assertTrue(formatted.contains("MODALIDAD: COMER_AQUI"))
        assertTrue(formatted.contains("PORTAMESA / MESA: #17"))
        assertTrue(formatted.contains("CLIENTE: Juan Perez"))
        assertTrue(formatted.contains("2x Hamburguesa Doble"))
        assertTrue(formatted.contains("+ Pan Artesanal"))
        assertTrue(formatted.contains("+ Extra Queso"))
        assertTrue(formatted.contains("1x Papas Grandes"))
        assertTrue(formatted.contains("SUBTOTAL:"))
        assertTrue(formatted.contains("12.50"))
        assertTrue(formatted.contains("TOTAL A PAGAR:"))
        assertTrue(formatted.contains("13.38"))
        assertTrue(formatted.contains("MASTERCARD ****1234"))
        assertTrue(formatted.contains("AUT987654"))
        assertTrue(formatted.contains("FACTURA FISCAL: FAC-2026-0001"))
        assertTrue(formatted.contains("CUFE: CUFE-PANAMA-998877665544332211"))
    }

    @Test
    fun `formatCustomerReceipt handles fallback paid pending invoice status`() {
        val pendingPayment =
            samplePayment.copy(
                status = "PAID_PENDING_INVOICE",
                invoice = null,
            )

        val formatted = ReceiptFormatter.formatCustomerReceipt(pendingPayment)

        assertTrue(formatted.contains("*** PAGO PENDIENTE DE FACTURAR ***"))
        assertTrue(formatted.contains("Conserve este comprobante para su factura."))
    }

    @Test
    fun `formatKitchenTicket formats kitchen specific ticket with item notes and mesa`() {
        val formatted = ReceiptFormatter.formatKitchenTicket(samplePayment)

        assertTrue(formatted.contains("*** TICKET DE COCINA ***"))
        assertTrue(formatted.contains("ORDEN: K1-042"))
        assertTrue(formatted.contains("MODALIDAD: COMER_AQUI"))
        assertTrue(formatted.contains("MESA: #17"))
        assertTrue(formatted.contains("2x  Hamburguesa Doble"))
        assertTrue(formatted.contains("* Pan Artesanal"))
        assertTrue(formatted.contains("* Extra Queso"))
        assertTrue(formatted.contains("1x  Papas Grandes"))
    }
}
