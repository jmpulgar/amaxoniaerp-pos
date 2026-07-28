package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.CuentaDetalleResponse
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class TableAccountPaymentTest {
    @Test
    fun `maps reserved account lines and stable correlation into standard sale contract`() {
        val payment =
            TableAccountPayment(
                areaId = 4,
                mesaId = 12,
                sesionId = 30,
                cuenta =
                    CuentaMesaResponse(
                        id = 8,
                        sesionMesaId = 30,
                        total = 15.0,
                        detalle =
                            listOf(
                                CuentaDetalleResponse(
                                    productoId = 501,
                                    itemAlmacen = 7,
                                    itemCodigo = "P501",
                                    itemDescripcion = "Producto",
                                    cantidad = 1.5,
                                    itemPrecioSinIva = 10.0,
                                    itemTotalSinIva = 15.0,
                                    itemTotalConIva = 15.0,
                                ),
                            ),
                    ),
            )

        assertEquals("mesa-30-cuenta-8", payment.correlationId)
        assertEquals(4, payment.saleContext.areaId)
        assertEquals(12, payment.saleContext.mesaId)
        assertEquals(30, payment.saleContext.sesionMesaId)
        assertEquals(8, payment.saleContext.cuentaMesaId)
        assertEquals(1.5, payment.saleItems.single().itemCantidadTotal, 0.0)
        assertEquals(7, payment.saleItems.single().itemAlmacen)
    }
}
