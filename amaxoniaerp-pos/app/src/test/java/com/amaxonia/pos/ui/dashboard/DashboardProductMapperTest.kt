package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DashboardProductMapperTest {
    @Test
    fun `maps domain product without changing displayed values`() {
        val source =
            Product(
                id = "P-1",
                description = "Café",
                prices = listOf(PriceLevel(label = "A", pricePlusTax = 12.34)),
                taxRate = 7.0,
                isExempt = false,
                photoUrl = "coffee.jpg",
                department = "Bebidas",
                code = "CAFE",
                barcode1 = "123",
            )

        val mapped = DashboardProductMapper(FixedImageResolver).fromProduct(source, "tenant")

        assertEquals("P-1", mapped.id)
        assertEquals("Café", mapped.name)
        assertEquals(12.34, mapped.price, 0.0)
        assertEquals("image://tenant/coffee.jpg", mapped.imageUrl)
        assertEquals("Bebidas", mapped.category)
        assertSame(source, mapped.sourceProduct)
    }

    private object FixedImageResolver : ImageUrlResolver {
        override fun product(
            companyDatabase: String,
            photoPath: String,
        ): String = "image://$companyDatabase/$photoPath"

        override fun client(
            companyDatabase: String,
            clientId: String,
            filename: String,
        ): String = error("Not used by product mapping")
    }
}
