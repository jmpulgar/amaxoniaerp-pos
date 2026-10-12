package com.amaxonia.kiosk.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {
    @Test
    fun `category name drops the ERP sort prefix and calms all caps`() {
        assertEquals("Comida rapida", displayName("1. COMIDA RAPIDA"))
        assertEquals("Bebidas", displayName("2) BEBIDAS"))
        assertEquals("Postres", displayName("3 - POSTRES"))
    }

    @Test
    fun `names that already mix cases are kept as typed`() {
        assertEquals("Combos McFlow", displayName("Combos McFlow"))
        assertEquals("Elige tu bebida", sentenceCase("Elige tu bebida"))
    }

    @Test
    fun `all caps group names become sentence case`() {
        assertEquals("Acompañante", sentenceCase("ACOMPAÑANTE"))
        assertEquals("Término de la carne", sentenceCase("TÉRMINO DE LA CARNE"))
    }

    @Test
    fun `numbers inside the name are not treated as a prefix`() {
        assertEquals("Combo 2x1", displayName("COMBO 2X1"))
    }
}
