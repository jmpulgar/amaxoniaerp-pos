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

    @Test
    fun accountSnapshotKeepsBackendTotals() {
        val payment =
            TableAccountPayment(
                areaId = 1,
                mesaId = 2,
                sesionId = 3,
                cuenta =
                    CuentaMesaResponse(
                        id = 9,
                        sesionMesaId = 3,
                        subtotal = 9.38,
                        descuento = 0.11,
                        impuesto = 0.74,
                        total = 10.01,
                        detalle =
                            listOf(
                                CuentaDetalleResponse(
                                    productoId = 1,
                                    itemTotalSinIva = 4.635,
                                    itemTotalConIva = 5.005,
                                    itemPIva = 8.0,
                                ),
                                CuentaDetalleResponse(
                                    productoId = 2,
                                    itemTotalSinIva = 4.635,
                                    itemTotalConIva = 5.005,
                                    itemPIva = 8.0,
                                ),
                            ),
                    ),
            )

        assertEquals(9.38, payment.financialSnapshot.subtotalGross, 0.0)
        assertEquals(0.11, payment.financialSnapshot.itemDiscounts, 0.0)
        assertEquals(9.27, payment.financialSnapshot.subtotalNet, 0.0)
        assertEquals(0.74, payment.financialSnapshot.tax, 0.0)
        assertEquals(10.01, payment.financialSnapshot.total, 0.0)
        assertEquals(1, payment.financialSnapshot.taxLines.size)
    }
}
