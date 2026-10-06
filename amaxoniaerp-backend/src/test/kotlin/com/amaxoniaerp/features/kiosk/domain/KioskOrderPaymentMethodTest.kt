package com.amaxoniaerp.features.kiosk.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** El método de pago se deriva de pago_marca (sin columna pago_metodo). */
class KioskOrderPaymentMethodTest {
    private fun order(
        pagoMarca: String?,
        pagoReferencia: String?,
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
            total = BigDecimal("5.8900"),
            quoteExpiraEn = LocalDateTime.now().plusMinutes(10),
            pagoReferencia = pagoReferencia,
            pagoAutorizacion = null,
            pagoUltimos4 = null,
            pagoMarca = pagoMarca,
            idFactura = null,
            motivoRechazo = null,
            creadoEn = LocalDateTime.now(),
            actualizadoEn = LocalDateTime.now(),
            items = emptyList(),
        )

    @Test
    fun `pago_marca YAPPY means method YAPPY with pago_referencia as transaction id`() {
        val order = order(pagoMarca = "YAPPY", pagoReferencia = "TX-1")

        assertEquals(KioskPaymentMethod.YAPPY, order.pagoMetodo)
        assertEquals("TX-1", order.yappyTransactionId)
    }

    @Test
    fun `pago_marca comparison ignores case and surrounding spaces`() {
        val order = order(pagoMarca = " yappy ", pagoReferencia = " TX-2 ")

        assertEquals(KioskPaymentMethod.YAPPY, order.pagoMetodo)
        assertEquals("TX-2", order.yappyTransactionId)
    }

    @Test
    fun `card brands or no brand mean method CARD and no Yappy transaction`() {
        val visa = order(pagoMarca = "VISA", pagoReferencia = "REF-CARD")
        assertEquals(KioskPaymentMethod.CARD, visa.pagoMetodo)
        assertNull(visa.yappyTransactionId)

        val quoted = order(pagoMarca = null, pagoReferencia = null)
        assertEquals(KioskPaymentMethod.CARD, quoted.pagoMetodo)
        assertNull(quoted.yappyTransactionId)
    }

    @Test
    fun `YAPPY brand without reference has no transaction id`() {
        assertNull(order(pagoMarca = "YAPPY", pagoReferencia = "  ").yappyTransactionId)
    }
}
