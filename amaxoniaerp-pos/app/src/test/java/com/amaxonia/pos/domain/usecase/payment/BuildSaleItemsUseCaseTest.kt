package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.LotAssignment
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.money.MinorUnitMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildSaleItemsUseCaseTest {
    private val useCase = BuildSaleItemsUseCase()

    @Test
    fun `exempt item preserves decimal quantity and clamps negative discount`() {
        val item =
            CartItem(
                product =
                    product("7").copy(
                        isExempt = true,
                        taxRate = 16.0,
                        bulkQuantity = 0.0,
                    ),
                quantityDecimal = 2.5,
                unitPriceWithTax = 10.0,
                discountPercent = -5.0,
            )

        val result = useCase(input(item)).single()

        assertEquals(0.0, result.itemPIva, 0.0)
        assertEquals(10.0, result.itemPrecioSinIva, 0.0)
        assertEquals(25.0, result.itemTotalSinIva, 0.0)
        assertEquals(25.0, result.itemTotalConIva, 0.0)
        assertEquals(2.5, result.itemCantidadTotal, 0.0)
        assertEquals(1, result.cantidadBulto)
        assertEquals(42, result.codVendedor)
        assertNull(result.idSegmento)
        assertNull(result.idFamilia)
        assertEquals("no", result.poseeConfiguracionLote)
        assertEquals(0.0, result.promocionCantidad, 0.0)
    }

    @Test
    fun `taxed promoted package preserves characterized totals and lot mappings`() {
        val item =
            CartItem(
                product =
                    product("8").copy(
                        isExempt = false,
                        taxRate = 16.0,
                        bulkQuantity = 12.0,
                        gobSegment = "4",
                        gobFamily = "5",
                    ),
                codVendedor = 9,
                quantityDecimal = 1.5,
                itemUnitPackage = "EMPAQUE",
                unitPriceWithTax = 116.0,
                discountPercent = 10.0,
                hasLotConfig = true,
                lotAssignments =
                    listOf(
                        LotAssignment("12", "LOT-A", cantidad = 2, almacen = 3),
                        LotAssignment("invalid", "LOT-B", cantidad = 1),
                    ),
                promocionId = "promo-1",
                promocionVeces = 2,
            )

        val result = useCase(input(item)).single()

        assertEquals(16.0, result.itemPIva, 0.0)
        assertEquals(100.0, result.itemPrecioSinIva, 0.0000001)
        assertEquals(15.0, result.itemMontoDescuento, 0.0000001)
        assertEquals(135.0, result.itemTotalSinIva, 0.0000001)
        assertEquals(156.6, result.itemTotalConIva, 0.0000001)
        assertEquals(18.0, result.itemCantidadTotal, 0.0)
        assertEquals(9, result.codVendedor)
        assertEquals(4, result.idSegmento)
        assertEquals(5, result.idFamilia)
        assertEquals(listOf(12, 0), result.codigosLote.map { it.idLoteItem })
        assertEquals("si", result.poseeConfiguracionLote)
        assertEquals(2.0, result.promocionCantidad, 0.0)
    }

    @Test
    fun `non exempt item falls back to configured tax and clamps excessive discount`() {
        val item =
            CartItem(
                product =
                    product("9").copy(
                        isExempt = false,
                        taxRate = 0.0,
                        bulkQuantity = 1.0,
                    ),
                unitPriceWithTax = 107.0,
                discountPercent = 150.0,
            )

        val result = useCase(input(item, defaultTaxRate = 7.0)).single()

        assertEquals(7.0, result.itemPIva, 0.0)
        assertEquals(100.0, result.itemPrecioSinIva, 0.0000001)
        assertEquals(100.0, result.itemMontoDescuento, 0.0000001)
        assertEquals(0.0, result.itemTotalSinIva, 0.0)
        assertEquals(0.0, result.itemTotalConIva, 0.0)
    }

    /**
     * Precision regression: at scale=2, the BigDecimal implementation must
     * produce values whose `toDouble()` representation is the exact value
     * the wire backend expects — never IEEE-754 residue like 6.89999...5.
     * These tests assert on the bit-exactness of the Double output, not on
     * a tolerance window.
     */
    @Test
    fun `line totals are exact at scale 2 - no IEEE-754 residue`() {
        // 10.00 unit price, 7% tax, qty 2 → subtotal 20, tax 1.40, total 21.40
        val item =
            CartItem(
                product = product("71").copy(isExempt = false, taxRate = 7.0, bulkQuantity = 1.0),
                quantityDecimal = 2.0,
                unitPriceWithTax = 10.70,
                discountPercent = 0.0,
            )
        val result = useCase(input(item, defaultTaxRate = 7.0)).single()

        // Each output must round-trip cleanly: toDouble → fromDoubleAsMinor
        // must not throw and must match the expected minor units.
        assertEquals(1000L, MinorUnitMoney.fromDoubleAsMinor(result.itemPrecioSinIva))
        assertEquals(2000L, MinorUnitMoney.fromDoubleAsMinor(result.itemTotalSinIva))
        assertEquals(2140L, MinorUnitMoney.fromDoubleAsMinor(result.itemTotalConIva))
    }

    /**
     * Quantity × line price combinations that traditionally accumulated
     * residue in Double. 0.10 × 3 = 0.30 exact, not 0.30000000000000004.
     */
    @Test
    fun `three tenths aggregate accurately`() {
        val item =
            CartItem(
                product = product("72").copy(isExempt = true, taxRate = 0.0, bulkQuantity = 1.0),
                quantityDecimal = 3.0,
                unitPriceWithTax = 0.10,
                discountPercent = 0.0,
            )
        val result = useCase(input(item)).single()
        assertEquals(0.10, result.itemPrecioSinIva, 0.0)
        assertEquals(0.30, result.itemTotalSinIva, 0.0)
        assertEquals(0.30, result.itemTotalConIva, 0.0)
        assertEquals(30L, MinorUnitMoney.fromDoubleAsMinor(result.itemTotalConIva))
    }

    /**
     * Discount math at non-trivial percentages. unitPrice 100, 16% tax, 33%
     * discount on quantity 1 → subtotal 100, discount 33, net 67, total
     * 67 × 1.16 = 77.72. All exact at scale 2.
     */
    @Test
    fun `discount and tax combination is exact`() {
        val item =
            CartItem(
                product = product("73").copy(isExempt = false, taxRate = 16.0, bulkQuantity = 1.0),
                quantityDecimal = 1.0,
                unitPriceWithTax = 116.0,
                discountPercent = 33.0,
            )
        val result = useCase(input(item)).single()
        assertEquals(100.0, result.itemPrecioSinIva, 0.0)
        assertEquals(33.0, result.itemMontoDescuento, 0.0)
        assertEquals(67.0, result.itemTotalSinIva, 0.0)
        // 67 * 1.16 = 77.72
        assertEquals(77.72, result.itemTotalConIva, 0.0)
        assertEquals(7772L, MinorUnitMoney.fromDoubleAsMinor(result.itemTotalConIva))
    }

    /**
     * Wholesale quantity multiplier. unitPrice 5.00 with tax, qty 4, no
     * discount, exempt → 20.00 exact.
     */
    @Test
    fun `wholesale quantity multiplication is exact`() {
        val item =
            CartItem(
                product = product("74").copy(isExempt = true, taxRate = 0.0, bulkQuantity = 1.0),
                quantityDecimal = 4.0,
                unitPriceWithTax = 5.0,
                discountPercent = 0.0,
            )
        val result = useCase(input(item)).single()
        assertEquals(20.0, result.itemTotalConIva, 0.0)
        assertEquals(2000L, MinorUnitMoney.fromDoubleAsMinor(result.itemTotalConIva))
    }

    private fun input(
        item: CartItem,
        defaultTaxRate: Double = 0.0,
    ) = BuildSaleItemsInput(listOf(item), warehouseId = 11, sellerId = 42, defaultTaxRate = defaultTaxRate)

    private fun product(id: String) =
        Product(
            id = id,
            code = "P-$id",
            reference = "R-$id",
            description = "Product $id",
            gobFamily = "invalid",
            unitPackage = "BOX",
        )
}
