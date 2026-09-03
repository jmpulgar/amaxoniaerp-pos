package com.amaxoniaerp.features.electronicinvoice.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Q1 (opción b): en un reintento, la factura DEBE reutilizar el
 * numeroDocumentoFiscal ya persistido; el correlativo sólo se consume cuando
 * la factura nunca recibió número. Esto elimina el desfase que produce el
 * rechazo DGI 1513 (número duplicado).
 */
class NumeroDocumentoFiscalTest {
    @Test
    fun `reintento reutiliza el numero persistido sin leer correlativos`() {
        var lecturasCorrelativo = 0

        val numero =
            resolverNumeroDocumentoFiscal(
                persistido = "0000000012345",
                siguienteCorrelativo = {
                    lecturasCorrelativo++
                    "16"
                },
            )

        assertEquals("0000000012345", numero)
        assertEquals(0, lecturasCorrelativo)
    }

    @Test
    fun `primer envio consume el siguiente correlativo`() {
        for (persistido in listOf(null, "", "   ")) {
            val numero = resolverNumeroDocumentoFiscal(persistido) { "18" }
            assertEquals("18", numero)
        }
    }
}
