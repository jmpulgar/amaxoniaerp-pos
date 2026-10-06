package com.amaxoniaerp.features.kiosk.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class KioskOrderChargeTest {
    private fun order(
        total: String,
        vararg lines: Pair<String, String>,
    ): KioskOrderRecord =
        KioskOrderRecord(
            id = "order-1",
            idDispositivo = "device-1",
            numeroPedidoDiario = 1,
            codigoPedido = "K1-001",
            fecha = LocalDate.now(),
            estado = "COTIZADO",
            modalidad = "PARA_LLEVAR",
            portamesa = null,
            idCliente = "CF",
            total = BigDecimal(total),
            quoteExpiraEn = LocalDateTime.now().plusMinutes(10),
            pagoReferencia = null,
            pagoAutorizacion = null,
            pagoUltimos4 = null,
            pagoMarca = null,
            idFactura = null,
            motivoRechazo = null,
            creadoEn = LocalDateTime.now(),
            actualizadoEn = LocalDateTime.now(),
            items =
                lines.mapIndexed { index, (price, qty) ->
                    KioskOrderItemRecord(
                        idPedido = "order-1",
                        linea = index + 1,
                        idItem = 100 + index,
                        cantidad = BigDecimal(qty),
                        precioUnitario = BigDecimal(price),
                        nota = null,
                        modifiers = emptyList(),
                    )
                },
        )

    @Test
    fun `subtotal comes from lines and tax closes the gap to the quoted total`() {
        // 5.50 x1 (7% = 0.385 -> 0.39) + 3.25 x2 (7% = 0.455 -> 0.46): total cotizado 12.85
        val charge = KioskOrderCharge.from(order("12.8500", "5.5000" to "1.0000", "3.2500" to "2.0000"))

        assertEquals(BigDecimal("12.00"), charge.subTotal)
        assertEquals(BigDecimal("0.85"), charge.tax)
        assertEquals(BigDecimal("12.85"), charge.total)
        assertEquals(charge.total, charge.subTotal + charge.tax)
    }

    @Test
    fun `exempt order has zero tax`() {
        val charge = KioskOrderCharge.from(order("4.0000", "2.0000" to "2.0000"))

        assertEquals(BigDecimal("4.00"), charge.subTotal)
        assertEquals(BigDecimal("0.00"), charge.tax)
        assertEquals(BigDecimal("4.00"), charge.total)
    }

    @Test
    fun `subtotal never exceeds total so the breakdown always sums exactly`() {
        val charge = KioskOrderCharge.from(order("3.0000", "3.3333" to "1.0000"))

        assertEquals(BigDecimal("3.00"), charge.subTotal)
        assertEquals(BigDecimal("0.00"), charge.tax)
        assertEquals(charge.total, charge.subTotal + charge.tax)
    }
}
