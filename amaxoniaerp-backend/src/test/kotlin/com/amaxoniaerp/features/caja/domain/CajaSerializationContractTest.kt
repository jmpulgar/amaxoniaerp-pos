package com.amaxoniaerp.features.caja.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CajaSerializationContractTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `secuencia mantiene claves snake case`() {
        val encoded = json.encodeToString(CajaSecuenciaData(id = "seq-1", idCaja = "caja-1"))

        assertTrue("\"id_caja\":\"caja-1\"" in encoded)
        assertTrue("\"monto_efectivo_apertura\":0.0" in encoded)
        assertTrue("\"forma_pago\":[]" in encoded)
        assertFalse("\"idCaja\"" in encoded)
    }

    @Test
    fun `detalle de cierre mantiene claves snake case`() {
        val encoded =
            json.encodeToString(
                CajaCierreFormaPagoRequest(
                    idFormaPago = 1,
                    monto = 0.0,
                    montoCierre = 0.0,
                    montoDiferencia = 0.0,
                ),
            )

        assertEquals(
            "{\"id_forma_pago\":1,\"monto\":0.0,\"monto_cierre\":0.0,\"monto_diferencia\":0.0}",
            encoded,
        )
    }
}
