package com.amaxonia.pos.data.local.db

import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.repository.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientMappingTest {
    @Test
    fun `online and cached client mapping preserve credit configuration`() {
        val dto = ClientDto(permiteCredito = true, diasCredito = 30)

        val online = dto.toDomain()
        val cached = dto.toEntity().toDomain()

        assertEquals(true, online.permiteCredito)
        assertEquals(30, online.diasCredito)
        assertEquals(online.permiteCredito, cached.permiteCredito)
        assertEquals(online.diasCredito, cached.diasCredito)
    }

    @Test
    fun `legacy client defaults credit configuration`() {
        val client = ClientDto().toEntity().toDomain()

        assertEquals(false, client.permiteCredito)
        assertEquals(0, client.diasCredito)
    }
}
