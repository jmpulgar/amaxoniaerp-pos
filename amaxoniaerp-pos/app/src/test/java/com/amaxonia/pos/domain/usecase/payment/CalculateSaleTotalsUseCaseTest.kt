package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.money.MinorUnitMoney
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * The defining regression: a per-line total that previously accumulated
     * as 6.8999999999999995 because of IEEE-754 residue in Double sums. With
     * BigDecimal arithmetic in [CalculateSaleTotalsUseCase], the rolled-up
     * total is exactly 6.90 and the downstream [MinorUnitMoney] conversion
     * succeeds with minor-units 690.
     */
    @Test
    fun `rollup total has no IEEE-754 residue and converts to minor cleanly`() {
        // Construct a single line whose Double value is 6.90 and let the use
        // case aggregate it. The previous Double-sum implementation produced
        // 6.8999999999999995; the BigDecimal version must produce exactly
        // 6.90 so MinorUnitMoney.fromDoubleAsMinor returns 690.
        val single = useCase(listOf(item(LineValues(10.0, 1.0, 0.0, 0.0, 6.90, 6.90))))
        assertEquals(6.90, single.total, 0.0)
        assertEquals(690L, MinorUnitMoney.fromDoubleAsMinor(single.total))

        // Two-line aggregate where the legacy Double sum drifted.
        // 3.45 + 3.45 in IEEE-754 = 6.9 exactly; sum three copies of 2.30 to
        // force a non-trivial sum (0.1+0.2-style residue appears for many
        // combinations, but we don't need to reproduce the exact noise — we
        // need to assert the result is exact to scale 2).
        val triple =
            useCase(
                List(3) { item(LineValues(10.0, 1.0, 0.0, 0.0, 2.30, 2.30)) },
            )
        assertEquals(6.90, triple.total, 0.0)
        assertEquals(690L, MinorUnitMoney.fromDoubleAsMinor(triple.total))
    }

    /**
     * The classic 0.1 + 0.2 = 0.30000000000000004 trap. With BigDecimal, the
     * sum of three 0.10 lines is exactly 0.30 (minor 30), not 0.30000000004.
     */
    @Test
    fun `sum of three 0_10 lines totals exactly 0_30 in minor units`() {
        val result = useCase(List(3) { item(LineValues(0.10, 1.0, 0.0, 0.0, 0.10, 0.10)) })
        assertEquals(0.30, result.total, 0.0)
        assertEquals(30L, MinorUnitMoney.fromDoubleAsMinor(result.total))
    }

    /**
     * Price × quantity rollup. Three lines of 2.50 × 4 = 10.00 each, total
     * 30.00. Verifies cross-field multiplication happens in BigDecimal.
     */
    @Test
    fun `price times quantity rollup is exact`() {
        val result =
            useCase(
                List(
                    3,
                ) { item(LineValues(priceWithoutTax = 2.50, quantity = 4.0, discount = 0.0, taxRate = 0.0, net = 10.00, total = 10.00)) },
            )
        // subtotalGross = sum of precio * cantidad = 10.00 * 3 = 30.00
        assertEquals(30.00, result.subtotalGross, 0.0)
        assertEquals(30.00, result.total, 0.0)
        assertEquals(3000L, MinorUnitMoney.fromDoubleAsMinor(result.total))
    }

    /**
     * Many small lines (1000 × 0.01) stress the accumulator. A Double sum
     * would accumulate visible residue around 1e-15 per add; the BigDecimal
     * accumulator must hit exactly 10.00.
     */
    @Test
    fun `thousand cent lines aggregate to exact 10_00`() {
        val items = List(1000) { item(LineValues(0.01, 1.0, 0.0, 0.0, 0.01, 0.01)) }
        val result = useCase(items)
        assertEquals(10.00, result.total, 0.0)
        assertEquals(1000L, MinorUnitMoney.fromDoubleAsMinor(result.total))
        // Identical assertion via toPlainString to guard against a 10.0000001 leak.
        assertTrue("total=$result", result.total.toString().let { it == "10.0" || it == "10.00" })
    }

    /**
     * Large quantity × small unit price with tax. 7% IVA on 100 × 1.00 line.
     * 100 × 1.00 = 100 subtotal, 7 tax, 107 total. All exact in scale=2.
     */
    @Test
    fun `taxed bulk rollup stays scale-2 exact`() {
        val result =
            useCase(
                listOf(
                    item(LineValues(priceWithoutTax = 1.00, quantity = 100.0, discount = 0.0, taxRate = 7.0, net = 100.00, total = 107.00)),
                ),
            )
        assertEquals(100.00, result.subtotalNet, 0.0)
        assertEquals(7.00, result.tax, 0.0)
        assertEquals(107.00, result.total, 0.0)
        assertEquals(1, result.taxLines.size)
        assertEquals(100.00, result.taxLines.single().totalizarBaseRetencion, 0.0)
        assertEquals(7.00, result.taxLines.single().totalizarMontoIva2, 0.0)
        assertEquals(10700L, MinorUnitMoney.fromDoubleAsMinor(result.total))
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
