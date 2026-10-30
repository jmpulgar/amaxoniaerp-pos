package com.amaxonia.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEncodingTest {
    @Test
    fun cleanMojibakeRestoresAccentedCharacters() {
        assertEquals("Café", cleanMojibake("Caf\u00C3\u00A9"))
        assertEquals("Porción", cleanMojibake("Porci\u00C3\u00B3n"))
        assertEquals("TORREFACTORA VALLE CHIRIQUÍ, S.A.", cleanMojibake("TORREFACTORA VALLE CHIRIQU\u00C3\u008D, S.A."))
        assertEquals("Niño", cleanMojibake("Ni\u00C3\u00B1o"))
        assertEquals("Árbol", cleanMojibake("\u00C3\u0081rbol"))
        assertEquals("Último", cleanMojibake("\u00C3\u009Altimo"))
    }

    @Test
    fun cleanMojibakeLeavesCleanTextUnchanged() {
        assertEquals("Café Molido Gourmet", cleanMojibake("Café Molido Gourmet"))
        assertEquals("Coca Cola 2L", cleanMojibake("Coca Cola 2L"))
        assertEquals("Sin tildes ni acentos", cleanMojibake("Sin tildes ni acentos"))
        assertEquals("", cleanMojibake(""))
    }
}
