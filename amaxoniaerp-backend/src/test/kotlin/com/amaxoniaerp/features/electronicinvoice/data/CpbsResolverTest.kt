package com.amaxoniaerp.features.electronicinvoice.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Q8: el CPBS fiscal se resuelve desde `factura_detalle.id_familia` /
 * `id_segmento`; cuando la venta no los guardó (facturas creadas por el POS),
 * se hace fallback directo a `item.id_familia_gob` / `id_segmento_gob`.
 */
class CpbsResolverTest {
    @Test
    fun `usa el CPBS del detalle cuando existe`() {
        val (codigo, abrev) = resolverCpbs(5411, 54, 9999, 99)

        assertEquals("5411", codigo)
        assertEquals("54", abrev)
    }

    @Test
    fun `cae al CPBS de gobierno del item cuando el detalle viene NULL`() {
        val (codigo, abrev) = resolverCpbs(null, null, 31010101, 31)

        assertEquals("31010101", codigo)
        assertEquals("31", abrev)
    }

    @Test
    fun `mezcla detalle e item campo por campo`() {
        val (codigo, abrev) = resolverCpbs(5411, null, 31010101, 31)

        assertEquals("5411", codigo)
        assertEquals("31", abrev)
    }

    @Test
    fun `sin fuentes de CPBS retorna nulos`() {
        val (codigo, abrev) = resolverCpbs(null, null, null, null)

        assertNull(codigo)
        assertNull(abrev)
    }
}
