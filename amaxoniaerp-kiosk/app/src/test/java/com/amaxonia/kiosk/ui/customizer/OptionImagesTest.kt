package com.amaxonia.kiosk.ui.customizer

import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionImagesTest {
    private fun item(
        id: Int,
        name: String,
        imageUrl: String?,
    ) = KioskItemDto(
        id = id,
        categoryId = 1,
        name = name,
        description = null,
        price = "1.00",
        taxRate = "7",
        imageUrl = imageUrl,
        soldOut = false,
        modifierGroups = emptyList(),
    )

    private fun option(name: String) = KioskModifierOptionDto(id = 1, name = name, extraPrice = "0.00")

    @Test
    fun `option borrows the photo of the catalog product with the same name`() {
        val resolve = optionImageResolver(listOf(item(1, "COCA COLA LATA", "/img/coca.png")))

        assertEquals("/img/coca.png", resolve(option("Coca Cola  lata")))
    }

    @Test
    fun `names match without accents or punctuation`() {
        val resolve = optionImageResolver(listOf(item(1, "CERVEZA PANAMÁ LIGHT", "/img/cerveza.png")))

        assertEquals("/img/cerveza.png", resolve(option("cerveza panama light.")))
    }

    @Test
    fun `no photo when the product has none or does not exist`() {
        val resolve = optionImageResolver(listOf(item(1, "PAPAS FRITAS", null)))

        assertNull(resolve(option("PAPAS FRITAS")))
        assertNull(resolve(option("ROJO")))
    }
}
