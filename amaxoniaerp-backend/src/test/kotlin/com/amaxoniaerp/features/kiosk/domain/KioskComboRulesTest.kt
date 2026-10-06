package com.amaxoniaerp.features.kiosk.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas de precio y selección de combos replicadas del POS PHP. */
class KioskComboRulesTest {
    private fun option(
        id: Int,
        group: Int,
        extra: String = "0.00",
        soldOut: Boolean = false,
        name: String = "OPCION $id",
    ) = KioskComboOption(
        id = id,
        groupId = group,
        idItem = null,
        name = name,
        extraConIva = BigDecimal(extra),
        isDefault = false,
        orden = id,
        soldOut = soldOut,
    )

    private val groups =
        listOf(
            KioskComboGroup(1, "INCLUYE", KioskComboRules.TIPO_INCLUIDO, 1, 1, 0, listOf(option(10, 1, name = "PAPAS"))),
            KioskComboGroup(2, "BEBIDA", KioskComboRules.TIPO_COMBO, 1, 1, 1, listOf(option(20, 2), option(21, 2, "0.50", soldOut = true))),
            KioskComboGroup(3, "EXTRAS", KioskComboRules.TIPO_MODIFICADOR, 0, 0, 2, listOf(option(30, 3, "1.07"), option(31, 3, "0.54"))),
        )

    @Test
    fun `extra resolves precio, then coniva1 when the group adds, else zero`() {
        assertEquals(BigDecimal("1.25"), KioskComboRules.resolveExtraConIva(BigDecimal("1.25"), true, BigDecimal("9.99")))
        assertEquals(BigDecimal("1.87"), KioskComboRules.resolveExtraConIva(null, true, BigDecimal("1.87")))
        assertEquals(BigDecimal("0.00"), KioskComboRules.resolveExtraConIva(null, false, BigDecimal("1.87")))
        assertEquals(BigDecimal("0.00"), KioskComboRules.resolveExtraConIva(null, true, null))
    }

    @Test
    fun `extra without tax divides the tax-inclusive sum like amaxonia_composicion`() {
        assertEquals(BigDecimal("1.0000"), KioskComboRules.extraSinIva(BigDecimal("1.07"), BigDecimal("7.00")))
        assertEquals(BigDecimal("1.7477"), KioskComboRules.extraSinIva(BigDecimal("1.87"), BigDecimal("7")))
        // Exento o sin impuesto: el mismo valor.
        assertEquals(BigDecimal("1.8700"), KioskComboRules.extraSinIva(BigDecimal("1.87"), BigDecimal.ZERO))
    }

    @Test
    fun `selection counts repeated ids, sums extras and keeps group order`() {
        val selected = KioskComboRules.validateSelection("Burger", groups, listOf(31, 20, 31))

        assertEquals(listOf(20, 31), selected.map { it.option.id })
        assertEquals(2, selected.last().veces)
        assertEquals("2 x OPCION 31", selected.last().displayName)
        assertEquals(BigDecimal("1.08"), selected.last().extraConIva)
        // 0.00 + 1.08 con impuesto → 1.0093 sin impuesto (7%).
        assertEquals(BigDecimal("1.0093"), KioskComboRules.lineExtraSinIva(selected, BigDecimal("7.00")))
    }

    @Test
    fun `maximo_veces zero means as many as options`() {
        val extras = groups[2]
        assertEquals(2, extras.effectiveMax)
        val error =
            assertFailsWith<IllegalArgumentException> {
                KioskComboRules.validateSelection("Burger", groups, listOf(20, 30, 31, 31))
            }
        assertTrue(error.message!!.contains("máximo de 2"))
    }

    @Test
    fun `mandatory groups, included options, unknown and sold out ids are rejected`() {
        assertTrue(
            assertFailsWith<IllegalArgumentException> { KioskComboRules.validateSelection("Burger", groups, listOf(30)) }
                .message!!
                .contains("'BEBIDA' requiere al menos 1"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { KioskComboRules.validateSelection("Burger", groups, listOf(20, 10)) }
                .message!!
                .contains("Modificador 10 no válido"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { KioskComboRules.validateSelection("Burger", groups, listOf(21)) }
                .message!!
                .contains("agotado"),
        )
        assertTrue(KioskComboRules.validateSelection("Agua", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `included groups are summarized in the description and not selectable`() {
        assertEquals(listOf(2, 3), KioskComboRules.selectableGroups(groups).map { it.id })
        assertEquals("Carne. Incluye: PAPAS", KioskComboRules.describeItem("Carne", groups))
        assertEquals("Incluye: PAPAS", KioskComboRules.describeItem(null, groups))
        assertNull(KioskComboRules.describeItem(null, groups.drop(1)))
    }
}
