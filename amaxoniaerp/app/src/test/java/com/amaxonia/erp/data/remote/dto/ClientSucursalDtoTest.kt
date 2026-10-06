package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientSucursalDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserializesClientSucursalDtoCorrectly() {
        val rawJson = """
            {
                "sucursalId": 12,
                "clienteCodigo": "CLI-500",
                "nombreSucursal": "Sucursal Costa del Este",
                "nombreContacto": "Carlos Rivera",
                "telefonoContacto": "507-6999-8888",
                "correoContacto": "carlos@empresa.com",
                "direccion": "Avenida Balboa, Torre Las Americas",
                "observaciones": "Entrega en planta baja"
            }
        """.trimIndent()

        val dto = json.decodeFromString<ClientSucursalDto>(rawJson)

        assertEquals(12, dto.sucursalId)
        assertEquals("CLI-500", dto.clienteCodigo)
        assertEquals("Sucursal Costa del Este", dto.nombreSucursal)
        assertEquals("Carlos Rivera", dto.nombreContacto)
        assertEquals("507-6999-8888", dto.telefonoContacto)
        assertEquals("carlos@empresa.com", dto.correoContacto)
        assertEquals("Avenida Balboa, Torre Las Americas", dto.direccion)
        assertEquals("Entrega en planta baja", dto.observaciones)
    }

    @Test
    fun deserializesClientSucursalDtoWithNulls() {
        val rawJson = """
            {
                "sucursalId": 1,
                "clienteCodigo": "CF",
                "nombreSucursal": "Principal"
            }
        """.trimIndent()

        val dto = json.decodeFromString<ClientSucursalDto>(rawJson)

        assertEquals(1, dto.sucursalId)
        assertEquals("CF", dto.clienteCodigo)
        assertEquals("Principal", dto.nombreSucursal)
        assertNull(dto.nombreContacto)
        assertNull(dto.telefonoContacto)
        assertNull(dto.correoContacto)
        assertNull(dto.direccion)
        assertNull(dto.observaciones)
    }
}
