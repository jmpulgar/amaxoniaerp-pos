package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.SaleItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateSaleTotalsUseCaseTest {
    private val useCase = CalculateSaleTotalsUseCase()

    @Test
    fun `preserves legacy aggregation for taxed exempt and discounted lines`() {
        val result =
            useCase(
                listOf(
                    item(LineValues(10.0, 2.0, 1.0, 16.0, 19.0, 22.04)),
                    item(LineValues(3.0, 0.5, 0.0, 0.0, 1.5, 1.5)),
                    item(LineValues(5.0, 1.0, 0.5, 16.0, 4.5, 5.22)),
                ),
            )

        assertEquals(26.5, result.subtotalGross, TOLERANCE)
        assertEquals(1.5, result.itemDiscounts, TOLERANCE)
        assertEquals(25.0, result.subtotalNet, TOLERANCE)
        assertEquals(28.76, result.total, TOLERANCE)
        assertEquals(3.76, result.tax, TOLERANCE)
        assertEquals(1, result.taxLines.size)
        assertEquals(23.5, result.taxLines.single().totalizarBaseRetencion, TOLERANCE)
        assertEquals(3.76, result.taxLines.single().totalizarMontoIva2, TOLERANCE)
    }

    @Test
    fun `empty sale has zero totals and no tax lines`() {
        val result = useCase(emptyList())

        assertEquals(0.0, result.total, 0.0)
        assertEquals(0.0, result.tax, 0.0)
        assertEquals(emptyList<Any>(), result.taxLines)
    }

    private fun item(values: LineValues) =
        SaleItemDto(
            idItem = 1,
            itemAlmacen = 1,
            itemDescripcion = "item",
            itemCantidad = values.quantity,
            itemPrecioSinIva = values.priceWithoutTax,
            itemMontoDescuento = values.discount,
            itemPIva = values.taxRate,
            itemTotalSinIva = values.net,
            itemTotalConIva = values.total,
            itemCantidadTotal = values.quantity,
        )

    private data class LineValues(
        val priceWithoutTax: Double,
        val quantity: Double,
        val discount: Double,
        val taxRate: Double,
        val net: Double,
        val total: Double,
    )

    private companion object {
        const val TOLERANCE = 0.000000000001
    }
}
