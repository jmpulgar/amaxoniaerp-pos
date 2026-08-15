package com.amaxonia.pos.domain.model.payment

import com.amaxonia.pos.data.local.AppJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class FormaPagoSerializationTest {
    @Test
    fun `deserializes without tipo moneda`() {
        val formaPago = AppJson.decodeFromString<FormaPago>(BASE_JSON)

        assertEquals("", formaPago.tipoMoneda)
    }

    @Test
    fun `deserializes empty tipo moneda`() {
        val formaPago = AppJson.decodeFromString<FormaPago>("""$BASE_JSON_WITHOUT_TIPO_MONEDA,"tipo_moneda":""}""")

        assertEquals("", formaPago.tipoMoneda)
    }

    private companion object {
        const val BASE_JSON_WITHOUT_TIPO_MONEDA =
            "{\"id_forma_pago\":1,\"activo\":1,\"pos\":1,\"grupo\":1,\"orden\":1"
        const val BASE_JSON = "$BASE_JSON_WITHOUT_TIPO_MONEDA}"
    }
}
