package com.amaxoniaerp.features.items.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FlexibleIntColumnTypeTest {
    private val columnType = FlexibleIntColumnType()

    @Test
    fun `valueFromDB maneja booleano true como 1 y false como 0`() {
        assertEquals(1, columnType.valueFromDB(true))
        assertEquals(0, columnType.valueFromDB(false))
    }

    @Test
    fun `valueFromDB maneja numeros enteros longs y bytes`() {
        assertEquals(2, columnType.valueFromDB(2))
        assertEquals(1, columnType.valueFromDB(1L))
        assertEquals(0, columnType.valueFromDB(0.toByte()))
    }

    @Test
    fun `valueFromDB maneja cadenas numericas`() {
        assertEquals(1, columnType.valueFromDB("1"))
        assertEquals(2, columnType.valueFromDB("2"))
        assertEquals(0, columnType.valueFromDB("invalid"))
    }
}
