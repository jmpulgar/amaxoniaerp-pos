package com.amaxoniaerp.features.sync.domain

import com.amaxoniaerp.features.sync.domain.CatalogContentHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SyncDigestsTest {
    private fun productDto(
        id: String = "ITM-000123",
        price1: Double = 0.55,
        estatus: String? = "A",
    ) = ProductSyncDto(
        id = id,
        code = "A123",
        description = "AGUA MINERAL 600ML",
        reference = "REF-A123",
        barcode1 = "7450000123456",
        department = 3,
        isExempt = false,
        taxRate = 7.0,
        costActual = 0.35,
        unitPackage = "EMP 24",
        bulkQuantity = 24.0,
        unitOrPackage = "UNIDAD",
        estatus = estatus,
        prices =
            listOf("A", "B", "C", "D", "E").mapIndexed { index, label ->
                val price = if (index == 0) price1 else price1 + index
                PriceLevelSyncDto(
                    label = label,
                    price = price,
                    utilityPercent = 20.0,
                    pricePlusUtility = price,
                    pricePlusTax = price * 1.07,
                    unitPrice = price / 24.0,
                    unitPricePlusTax = price / 24.0 * 1.07,
                    discountPercent = 0.0,
                )
            },
    )

    private fun clientDto(
        id: String = "CLI-000001",
        permiteCredito: Boolean = true,
        idSucursal: Int? = 12,
    ) = ClientSyncDto(
        id = id,
        code = "CLI-000001",
        rif = "J-12345678-9",
        dv = "0",
        nombre = "COMERCIAL LA ESQUINA",
        direccion = "AV PRINCIPAL",
        telefonos = "555-1234",
        activo = true,
        codTipoCliente = 2,
        permiteCredito = permiteCredito,
        limite = 5000.0,
        dias = 15,
        idSucursal = idSucursal,
    )

    @Test
    fun `el dto slim produce el mismo digest que el ProductDigest canonico`() {
        val dto = productDto()
        val handBuilt =
            CatalogContentHash.ProductDigest(
                id = dto.id,
                code = dto.code,
                description = dto.description,
                reference = dto.reference,
                barcode1 = dto.barcode1,
                barcode2 = dto.barcode2,
                barcode3 = dto.barcode3,
                department = dto.department,
                isExempt = dto.isExempt,
                taxRate = dto.taxRate,
                costActual = dto.costActual,
                unitPackage = dto.unitPackage,
                bulkQuantity = dto.bulkQuantity,
                portionUnit = dto.portionUnit,
                unitOrPackage = dto.unitOrPackage,
                estatus = dto.estatus,
                prices =
                    dto.prices.map { level ->
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
                    },
            )

        assertEquals(
            CatalogContentHash.productRowHash(handBuilt),
            productContentHash(dto),
        )
    }

    @Test
    fun `cambiar estatus cambia el hash de contenido`() {
        assertNotEquals(
            productContentHash(productDto(estatus = "A")),
            productContentHash(productDto(estatus = "I")),
        )
        assertNotEquals(
            productContentHash(productDto(estatus = "A")),
            productContentHash(productDto(estatus = null)),
        )
    }

    @Test
    fun `client hash es determinista y sensible a contenido`() {
        assertEquals(
            clientContentHash(clientDto()),
            clientContentHash(clientDto()),
        )
        assertNotEquals(
            clientContentHash(clientDto(permiteCredito = true)),
            clientContentHash(clientDto(permiteCredito = false)),
        )
        assertNotEquals(
            clientContentHash(clientDto(idSucursal = 12)),
            clientContentHash(clientDto(idSucursal = 30)),
        )
        assertNotEquals(
            clientContentHash(clientDto(idSucursal = 12)),
            clientContentHash(clientDto(idSucursal = null)),
        )
    }
}
