package com.amaxoniaerp.features.electronicinvoice.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de la taxonomía de errores FE (Q4): separación en memoria del
 * catálogo de transporte TFHKA (100-300) del catálogo fiscal DGI (4 dígitos),
 * con clasificación `reintentable` y `requiereConciliacion`.
 *
 * Caso real que motiva el parser (factura 001-01182):
 * "203 1513-Número del documento fiscal duplicado. | 1508-Tiempo excesivo en
 * operación en contingencia. | 2007-Item 1: No informado ningún código de
 * producto en la Codificación Panameña de Bienes y Servicios."
 */
class FeErrorTaxonomyTest {
    @Test
    fun `mensaje combinado del incidente real se separa en tres incidencias fiscales`() {
        val analisis =
            analizarFalloPac(
                codigo = "203",
                mensaje =
                    "1513-Número del documento fiscal duplicado. | " +
                        "1508-Tiempo excesivo en operación en contingencia. | " +
                        "2007-Item 1: No informado ningún código de producto en la Codificación Panameña de Bienes y Servicios.",
            )

        assertEquals("203", analisis.codigoTransporte)
        assertEquals(
            listOf("1513", "1508", "2007"),
            analisis.incidenciasFiscales.map { it.codigo },
        )
        assertTrue(analisis.incidenciasFiscales[0].mensaje.contains("duplicado"))
        assertTrue(analisis.incidenciasFiscales[2].mensaje.contains("Item 1"))
        assertFalse(analisis.reintentable, "rechazos fiscales no son reintentables sin corrección")
        assertTrue(analisis.requiereConciliacion, "1513 exige consultar EstadoDocumento antes de reintentar")
    }

    @Test
    fun `un solo codigo fiscal sin separadores se parsea`() {
        val analisis = analizarFalloPac(codigo = "203", mensaje = "2007-Item 1: No informado ningún código de producto")

        assertEquals(listOf("2007"), analisis.incidenciasFiscales.map { it.codigo })
        assertFalse(analisis.reintentable)
        assertFalse(analisis.requiereConciliacion, "2007 no requiere conciliación, requiere corrección de datos")
    }

    @Test
    fun `codigo de transporte 300 es reintentable sin incidencias fiscales`() {
        val analisis = analizarFalloPac(codigo = "300", mensaje = "Reenviar documento")

        assertTrue(analisis.incidenciasFiscales.isEmpty())
        assertTrue(analisis.reintentable)
        assertFalse(analisis.requiereConciliacion)
    }

    @Test
    fun `codigo 102 duplicado requiere conciliacion y no reintento ciego`() {
        val analisis = analizarFalloPac(codigo = "102", mensaje = "El documento está duplicado.")

        assertFalse(analisis.reintentable)
        assertTrue(analisis.requiereConciliacion)
    }

    @Test
    fun `codigo 109 de validacion de campos no es reintentable sin cambios`() {
        val analisis = analizarFalloPac(codigo = "109", mensaje = "El campo descripcion es requerido.")

        assertFalse(analisis.reintentable)
        assertFalse(analisis.requiereConciliacion)
    }

    @Test
    fun `codigos de procesamiento del PAC son reintentables`() {
        for (codigo in listOf("100", "101", "201")) {
            val analisis = analizarFalloPac(codigo = codigo, mensaje = "Error al procesar solicitud")
            assertTrue(analisis.reintentable, "codigo $codigo debe ser reintentable")
        }
    }

    @Test
    fun `fallo de transporte sin codigo PAC es reintentable y requiere conciliacion`() {
        val analisis = analizarFalloPac(codigo = null, mensaje = "Timeout esperando DGI", falloDeTransporte = true)

        assertTrue(analisis.reintentable)
        assertTrue(analisis.requiereConciliacion, "un timeout exige consultar EstadoDocumento")
    }

    @Test
    fun `codigo 202 de respuesta DGI requiere conciliacion`() {
        val analisis = analizarFalloPac(codigo = "202", mensaje = "Error al recibir la respuesta de la DGI.")

        assertTrue(analisis.requiereConciliacion)
        assertTrue(analisis.reintentable, "fallo de integración DGI admite reintento tras conciliar")
    }

    @Test
    fun `mensaje vacio con codigo de transporte no produce incidencias`() {
        val analisis = analizarFalloPac(codigo = "201", mensaje = "")

        assertTrue(analisis.incidenciasFiscales.isEmpty())
        assertTrue(analisis.reintentable)
    }
}
