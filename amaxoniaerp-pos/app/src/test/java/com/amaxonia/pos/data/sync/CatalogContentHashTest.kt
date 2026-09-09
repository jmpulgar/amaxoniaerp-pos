package com.amaxonia.pos.data.sync

import com.amaxonia.pos.domain.model.PriceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CatalogContentHashTest {
    private fun product(
        id: String = "ITM-000123",
        code: String = "A123",
        description: String = "AGUA MINERAL 600ML",
        price1: Double = 0.55,
        barcode1: String = "7450000123456",
    ) = CatalogContentHash.ProductDigest(
        id = id,
        code = code,
        description = description,
        reference = "REF-A123",
        barcode1 = barcode1,
        barcode2 = "",
        barcode3 = "",
        department = 3,
        isExempt = false,
        taxRate = 7.0,
        costActual = 0.35,
        unitPackage = "EMP 24",
        bulkQuantity = 24.0,
        portionUnit = null,
        unitOrPackage = "UNIDAD",
        estatus = "A",
        prices = levels(price1 = price1),
    )

    private fun levels(price1: Double): List<CatalogContentHash.PriceLevelDigest> =
        listOf("A", "B", "C", "D", "E").mapIndexed { index, label ->
            val price = if (index == 0) price1 else price1 + index
            CatalogContentHash.PriceLevelDigest(
                label = label,
                price = price,
                utilityPercent = 20.0,
                pricePlusUtility = price,
                pricePlusTax = price * 1.07,
                unitPrice = price / 24.0,
                unitPricePlusTax = price / 24.0 * 1.07,
                discountPercent = 0.0,
            )
        }

    @Test
    fun `xxh64 produce los vectores oficiales del algoritmo`() {
        assertEquals(0xEF46DB3751D8E999uL.toLong(), XxHash64.hash(ByteArray(0)))
        assertEquals(0xD24EC4F1A98C6E5BuL.toLong(), XxHash64.hash("a".toByteArray()))
        assertEquals(0x44BC2CF5AD770999L, XxHash64.hash("abc".toByteArray()))
        assertEquals(0x066ED728FCEEB3BEL, XxHash64.hash("message digest".toByteArray()))
        assertEquals(
            0xCFE1F278FA89835CuL.toLong(),
            XxHash64.hash("abcdefghijklmnopqrstuvwxyz".toByteArray()),
        )
        assertEquals(
            0xAAA46907D3047814uL.toLong(),
            XxHash64.hash("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toByteArray()),
        )
        assertEquals(0xBBF7DC20B828A78BuL.toLong(), XxHash64.hash(ByteArray(96) { 'x'.code.toByte() }))
    }

    @Test
    fun `mismo id con campo distinto cambia el hash de fila y el agregado`() {
        val original = product()
        val modified = product(price1 = 0.60)

        assertNotEquals(CatalogContentHash.productRowHash(original), CatalogContentHash.productRowHash(modified))

        val datasetA = listOf(product("ITM-1"), product("ITM-2"), original)
        val datasetB = listOf(product("ITM-1"), product("ITM-2"), modified)

        val hashA = CatalogContentHash.aggregate(datasetA.map { CatalogContentHash.productRowHash(it) })
        val hashB = CatalogContentHash.aggregate(datasetB.map { CatalogContentHash.productRowHash(it) })
        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `mismo id con codigo de barras distinto cambia el agregado sin cambiar el conteo`() {
        val base = (1..100).map { product(id = "ITM-$it") }
        val divergente = base.map { if (it.id == "ITM-50") it.copy(barcode1 = "7459999999999") else it }

        assertEquals(base.size, divergente.size)
        assertNotEquals(
            CatalogContentHash.aggregate(base.map { CatalogContentHash.productRowHash(it) }),
            CatalogContentHash.aggregate(divergente.map { CatalogContentHash.productRowHash(it) }),
        )
    }

    @Test
    fun `el agregado es conmutativo e independiente del orden de iteracion`() {
        val rows = (1..50).map { product(id = "ITM-$it", price1 = it.toDouble()) }
        val hashes = rows.map { CatalogContentHash.productRowHash(it) }

        assertEquals(
            CatalogContentHash.aggregate(hashes),
            CatalogContentHash.aggregate(hashes.reversed()),
        )
        assertEquals(
            CatalogContentHash.aggregate(hashes),
            CatalogContentHash.aggregate(hashes.shuffled(Random(42))),
        )
    }

    @Test
    fun `null vacio y campo distinto de vacio producen hashes distintos`() {
        val conNull = CatalogContentHash.rowHash("X") { str(null) }
        val conVacio = CatalogContentHash.rowHash("X") { str("") }
        val conValor = CatalogContentHash.rowHash("X") { str("A") }

        assertNotEquals(conNull, conVacio)
        assertNotEquals(conVacio, conValor)
        assertNotEquals(conNull, conValor)
    }

    @Test
    fun `NaN se canonicaliza igual y cero negativo se distingue de cero`() {
        val nan1 = CatalogContentHash.rowHash("X") { dbl(Double.NaN) }
        val nan2 = CatalogContentHash.rowHash("X") { dbl(Double.NaN) }
        assertEquals(nan1, nan2)

        assertNotEquals(
            CatalogContentHash.rowHash("X") { dbl(0.0) },
            CatalogContentHash.rowHash("X") { dbl(-0.0) },
        )
    }

    @Test
    fun `fixture identico entre backend y cliente produce el mismo digest`() {
        val fixture = product(id = "ITM-FIXTURE-001", price1 = 12.34, barcode1 = "7450000987654")
        val computed = CatalogContentHash.productRowHash(fixture)
        assertTrue(computed != 0L)
        assertEquals(FIXTURE_DIGEST_V1, computed)
    }

    @Test
    fun `PriceLevel del dominio mapea a digest sin alterar el hash`() {
        val domainLevels = levels(12.34)
            .map {
                PriceLevel(
                    label = it.label,
                    price = it.price,
                    utilityPercent = it.utilityPercent,
                    pricePlusUtility = it.pricePlusUtility,
                    pricePlusTax = it.pricePlusTax,
                    unitPrice = it.unitPrice,
                    unitPricePlusTax = it.unitPricePlusTax,
                    discountPercent = it.discountPercent,
                )
            }

        val fixture = product(id = "ITM-FIXTURE-001", price1 = 12.34, barcode1 = "7450000987654")
        val digestsFromDomain = domainLevels.map { level ->
            CatalogContentHash.PriceLevelDigest(
                label = level.label,
                price = level.price,
                utilityPercent = level.utilityPercent,
                pricePlusUtility = level.pricePlusUtility,
                pricePlusTax = level.pricePlusTax,
                unitPrice = level.unitPrice,
                unitPricePlusTax = level.unitPricePlusTax,
                discountPercent = level.discountPercent,
            )
        }
        val fromDomain = fixture.copy(prices = digestsFromDomain)

        assertEquals(CatalogContentHash.productRowHash(fixture), CatalogContentHash.productRowHash(fromDomain))
    }

    companion object {
        // Digest del fixture canónico (ITM-FIXTURE-001). Fijado contra la
        // implementación de referencia (librería xxhash oficial); DEBE ser
        // idéntico al del test del backend (contrato cruzado de hash).
        const val FIXTURE_DIGEST_V1 = 3132355712640916076L
    }
}
