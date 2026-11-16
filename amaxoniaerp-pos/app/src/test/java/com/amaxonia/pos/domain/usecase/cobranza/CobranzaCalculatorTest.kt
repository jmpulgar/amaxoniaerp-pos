package com.amaxonia.pos.domain.usecase.cobranza

import com.amaxonia.pos.domain.model.cobranza.CobranzaPendingInvoiceDto
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CobranzaCalculatorTest {
    private val efectivo = FormaPago(idFormaPago = 1, siglas = "CASH", descripcion = "Efectivo", activo = 1, pos = 1, grupo = 1, orden = 1)
    private val tarjeta = FormaPago(idFormaPago = 2, siglas = "TDC", descripcion = "Tarjeta", activo = 1, pos = 1, grupo = 1, orden = 2)
    private val credito = FormaPago(idFormaPago = 3, siglas = "CXC", descripcion = "Crédito", activo = 1, pos = 1, grupo = 1, orden = 3)

    private fun invoice(
        id: String,
        saldo: Double,
    ) = CobranzaPendingInvoiceDto(idFactura = id, codFactura = id, montoOriginal = saldo, saldo = saldo)

    private fun selection(
        id: String,
        saldo: Double,
        amount: String = saldo.toString(),
    ) = CobranzaInvoiceSelection(invoice(id, saldo), Money.parse(amount))

    private fun line(
        forma: FormaPago,
        amount: String,
    ) = CobranzaPaymentLine(id = "${forma.idFormaPago}-$amount", formaPago = forma, amount = Money.parse(amount))

    @Test
    fun `varias formas de pago cubren varias facturas y el excedente en efectivo es vuelto`() {
        val selections = listOf(selection("F1", 10.44), selection("F2", 5.00))
        val payments = listOf(line(tarjeta, "10.00"), line(efectivo, "10.00"))

        val totals = CobranzaCalculator.totals(selections, payments)

        assertNull(totals.error)
        assertEquals(Money.parse("15.44"), totals.totalACobrar)
        assertEquals(Money.parse("4.56"), totals.cambio)

        val applied = CobranzaCalculator.appliedPayments(payments, totals.cambio)
        assertEquals(10.0, applied[0].monto, 0.0)
        assertEquals(5.44, applied[1].monto, 0.0)
        assertEquals(10.0, applied[1].montoRecibido, 0.0)
        assertEquals(4.56, applied[1].efectivoCambio, 0.0)
        // Lo aplicado cuadra exacto con lo cobrado.
        assertEquals(15.44, applied.sumOf { it.monto }, 1e-9)
    }

    @Test
    fun `un excedente sin efectivo no se acepta`() {
        val totals = CobranzaCalculator.totals(listOf(selection("F1", 10.0)), listOf(line(tarjeta, "12.00")))
        assertEquals("El excedente solo puede devolverse en efectivo", totals.error)
    }

    @Test
    fun `abono parcial valido y monto mayor al saldo rechazado`() {
        val parcial = CobranzaCalculator.totals(listOf(selection("F1", 10.0, "4.00")), listOf(line(tarjeta, "4.00")))
        assertTrue(parcial.isReady)

        val excedido = CobranzaCalculator.totals(listOf(selection("F1", 10.0, "10.01")), listOf(line(tarjeta, "10.01")))
        assertEquals("Un monto supera el saldo de su factura", excedido.error)
    }

    @Test
    fun `informa lo que falta cubrir`() {
        val totals = CobranzaCalculator.totals(listOf(selection("F1", 10.0)), listOf(line(efectivo, "7.50")))
        assertEquals(Money.parse("2.50"), totals.faltante)
        assertEquals("Falta cubrir 2.50", totals.error)
    }

    @Test
    fun `la forma de pago credito no se ofrece para cobrar`() {
        assertEquals(listOf(efectivo, tarjeta), cobranzaFormasPago(listOf(efectivo, tarjeta, credito)))
    }
}
