package com.amaxoniaerp.features.sales.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Q5: la columna física `factura.fechaFactura` es VARCHAR(20) con el formato
 * datetime del PHP legado ('yyyy-MM-dd HH:mm:ss') y el listado FEL del web
 * filtra con `BETWEEN` de strings datetime. Escribir sólo 'yyyy-MM-dd'
 * deja la factura FUERA del rango del día y el web deja de listarla.
 */
class FechaFacturaLegadoTest {
    @Test
    fun `fecha ISO de la app se guarda en formato datetime del legado`() {
        val guardado = formatFechaFacturaLegado("2026-09-03", LocalDate.of(2026, 9, 10))

        assertEquals("2026-09-03 00:00:00", guardado)
    }

    @Test
    fun `datetime completo del cliente se trunca al dia y se guarda en formato legado`() {
        val guardado = formatFechaFacturaLegado("2026-09-03 14:22:33", LocalDate.of(2026, 9, 10))

        assertEquals("2026-09-03 00:00:00", guardado)
    }

    @Test
    fun `sin fecha se usa el dia de la venta en formato legado`() {
        val guardado = formatFechaFacturaLegado(null, LocalDate.of(2026, 9, 10))

        assertEquals("2026-09-10 00:00:00", guardado)
    }

    @Test
    fun `fecha ilegible cae al dia de la venta`() {
        val guardado = formatFechaFacturaLegado("no-es-fecha", LocalDate.of(2026, 9, 10))

        assertEquals("2026-09-10 00:00:00", guardado)
    }
}
