package com.amaxonia.pos.domain.model

import com.amaxonia.pos.data.remote.dto.PriceDto
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.remote.dto.ProductOptionGroupDto
import com.amaxonia.pos.data.remote.dto.ProductOptionItemDto
import com.amaxonia.pos.data.repository.toDomain
import com.amaxonia.pos.domain.model.options.ProductComposition
import com.amaxonia.pos.domain.model.options.SelectedOption
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsInput
import com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOptionsTest {

    @Test
    fun productDetectsOptionsForComboOrKit() {
        val standard = Product(id = "1", description = "Refresco")
        assertFalse(standard.hasOptions)

        val combo = Product(id = "2", description = "Combo Hamburguesa", poseeProductoCombo = true)
        assertTrue(combo.hasOptions)

        val kit = Product(id = "3", description = "Kit Mantenimiento", tipoArticulo = "K")
        assertTrue(kit.hasOptions)
    }

    @Test
    fun productDtoToDomainMapsOptionsCorrectly() {
        val dto = ProductDto(
            id = "100",
            description = "Combo Lunch",
            poseeProductoCombo = "si",
            idCombo = "45",
            tipoArticulo = "C",
            prices = listOf(PriceDto(label = "A", price = 10.0, pricePlusTax = 10.70)),
        )

        val domain = dto.toDomain()
        assertTrue(domain.poseeProductoCombo)
        assertEquals("45", domain.idCombo)
        assertTrue(domain.hasOptions)
    }

    @Test
    fun optionGroupDtoMapsToDomain() {
        val dto = ProductOptionGroupDto(
            idGrupo = "grp-1",
            tipo = "COMBO",
            nombre = "Bebida",
            minimo = 1,
            maximo = 1,
            opciones = listOf(
                ProductOptionItemDto(
                    idGrupoItem = "opt-1",
                    nombre = "Agua",
                    precio = 0.0,
                    porDefecto = 1,
                    agotado = 0,
                ),
                ProductOptionItemDto(
                    idGrupoItem = "opt-2",
                    nombre = "Cerveza",
                    precioExtra = 1.50,
                    porDefecto = 0,
                    agotado = 0,
                ),
            ),
        )

        val domain = dto.toDomain()
        assertEquals("grp-1", domain.idGrupo)
        assertTrue(domain.isMandatory)
        assertTrue(domain.isSingleChoice)
        assertEquals(2, domain.opciones.size)
        assertTrue(domain.opciones[0].isDefault)
        assertEquals(1.50, domain.opciones[1].precioExtra, 0.001)
    }

    @Test
    fun optionGroupDtoResolvesImageUrlsWithResolver() {
        val fakeResolver = object : com.amaxonia.pos.domain.repository.ImageUrlResolver {
            override fun product(companyDatabase: String, photoPath: String): String =
                "http://cdn.test/$companyDatabase/$photoPath"

            override fun client(companyDatabase: String, clientId: String, filename: String): String = ""
        }

        val dto = ProductOptionGroupDto(
            idGrupo = "grp-1",
            nombre = "Bebida",
            opciones = listOf(
                ProductOptionItemDto(
                    idGrupoItem = "opt-coca",
                    nombre = "Coca Cola",
                    foto = "fotos/coca.png",
                ),
            ),
        )

        val domain = dto.toDomain(adminDb = "empresa1", imageUrlResolver = fakeResolver)
        assertEquals("http://cdn.test/empresa1/fotos/coca.png", domain.opciones[0].photoUrl)
    }

    @Test
    fun cartItemCalculatesTotalWithCompositionExtras() {
        val product = Product(
            id = "10",
            description = "Combo",
            isExempt = true,
            taxRate = 0.0,
            prices = listOf(PriceLevel(label = "A", price = 10.0, pricePlusTax = 10.0)),
        )

        val composition = ProductComposition(
            idCombo = "c1",
            opciones = listOf(
                SelectedOption(
                    idGrupo = "g1",
                    grupo = "Guarnición",
                    tipo = "COMBO",
                    idGrupoItem = "opt-papas",
                    nombre = "Papas Rústicas",
                    precio = 1.50,
                    veces = 1,
                ),
            ),
            extraConIva = 1.50,
            extraSinIva = 1.50,
        )

        val item = CartItem(
            product = product,
            quantity = 2,
            unitPriceWithTax = 11.50, // 10.00 base + 1.50 extra
            composition = composition,
        )

        assertTrue(item.hasComposition)
        assertEquals(23.00, item.total, 0.001)
        assertTrue(item.composition!!.summaryText.contains("Papas Rústicas"))
    }

    @Test
    fun cartKeepsItemsWithDifferentCompositionsSeparated() {
        val cartRepository = CartRepository()
        val product = Product(
            id = "p-1",
            description = "Hamburguesa Combo",
            prices = listOf(PriceLevel(label = "A", price = 8.0, pricePlusTax = 8.0)),
        )

        val compFries = ProductComposition(
            idCombo = "c1",
            opciones = listOf(
                SelectedOption(
                    idGrupo = "g1",
                    grupo = "Lado",
                    tipo = "COMBO",
                    idGrupoItem = "fries",
                    nombre = "Papas",
                    precio = 0.0,
                ),
            ),
            extraConIva = 0.0,
        )

        val compSalad = ProductComposition(
            idCombo = "c1",
            opciones = listOf(
                SelectedOption(
                    idGrupo = "g1",
                    grupo = "Lado",
                    tipo = "COMBO",
                    idGrupoItem = "salad",
                    nombre = "Ensalada",
                    precio = 0.50,
                ),
            ),
            extraConIva = 0.50,
        )

        cartRepository.addToCart(product = product, quantity = 1, composition = compFries)
        cartRepository.addToCart(product = product, quantity = 1, composition = compSalad)

        val items = cartRepository.cartItems.value
        assertEquals(2, items.size)
        assertNotEquals(items[0].composition, items[1].composition)

        // Adding identical composition merges and increases quantity
        cartRepository.addToCart(product = product, quantity = 2, composition = compFries)
        val updatedItems = cartRepository.cartItems.value
        assertEquals(2, updatedItems.size)
        assertEquals(3, updatedItems[0].quantity) // 1 + 2 = 3
    }

    @Test
    fun buildSaleItemsSerializesCompositionJson() {
        val product = Product(
            id = "42",
            description = "Desayuno Combo",
            isExempt = true,
            taxRate = 0.0,
            prices = listOf(PriceLevel(label = "A", price = 6.0, pricePlusTax = 6.0)),
        )

        val composition = ProductComposition(
            idCombo = "combo-42",
            opciones = listOf(
                SelectedOption(
                    idGrupo = "g-cafe",
                    grupo = "Café",
                    tipo = "COMBO",
                    idGrupoItem = "cappuccino",
                    nombre = "Capuccino",
                    precio = 0.75,
                ),
            ),
            extraConIva = 0.75,
            extraSinIva = 0.75,
        )

        val cartItem = CartItem(
            product = product,
            quantity = 1,
            unitPriceWithTax = 6.75,
            composition = composition,
        )

        val buildSaleItems = BuildSaleItemsUseCase()
        val saleItems = buildSaleItems(
            BuildSaleItemsInput(
                cartItems = listOf(cartItem),
                warehouseId = 1,
                sellerId = 2,
                defaultTaxRate = 0.0,
            ),
        )

        assertEquals(1, saleItems.size)
        val line = saleItems[0]
        assertTrue(line.composicion.isNotBlank())
        assertTrue(line.composicion.contains("Capuccino"))
        assertTrue(line.composicion.contains("cappuccino"))
    }
}
