package com.amaxonia.pos.data.remote.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Q3/Q4: el backend PA responde los fallos FE como
 * `{success:false, codigo, mensaje, reintentable, incidenciasFiscales}`.
 * Antes el POS sólo leía las claves "error"/"message" y el detalle DGI
 * (1513, 1508, 2007...) se perdía mostrando un error genérico.
 */
class FeErrorMessageTest {
    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `extrae codigo y mensaje del contrato del backend PA`() {
        val raw = json("""{"success":false,"codigo":"203","mensaje":"1513-Número del documento fiscal duplicado.","reintentable":false}""")

        assertEquals("[203] 1513-Número del documento fiscal duplicado.", extraerMensajeErrorFe(raw))
    }

    @Test
    fun `conserva el detalle combinado de incidencias DGI`() {
        val raw =
            json(
                """{"success":false,"codigo":"203","mensaje":"1513-Número duplicado. |""" +
                    """ 1508-Tiempo excesivo en contingencia. | 2007-Item 1: sin CPBS"}""",
            )

        assertEquals(
            "[203] 1513-Número duplicado. | 1508-Tiempo excesivo en contingencia. | 2007-Item 1: sin CPBS",
            extraerMensajeErrorFe(raw),
        )
    }

    @Test
    fun `mantiene compatibilidad con las claves error y message del legado`() {
        assertEquals("Falta invoiceId", extraerMensajeErrorFe(json("""{"error":"Falta invoiceId"}""")))
        assertEquals("No aplicable", extraerMensajeErrorFe(json("""{"message":"No aplicable"}""")))
    }

    @Test
    fun `mensaje sin codigo se devuelve tal cual`() {
        assertEquals("Error de PAC", extraerMensajeErrorFe(json("""{"mensaje":"Error de PAC"}""")))
    }

    @Test
    fun `body ilegible o vacio retorna nulo`() {
        assertNull(extraerMensajeErrorFe(null))
        assertNull(extraerMensajeErrorFe(json("""{}""")))
        assertNull(extraerMensajeErrorFe(json("""{"mensaje":""}""")))
    }
}
