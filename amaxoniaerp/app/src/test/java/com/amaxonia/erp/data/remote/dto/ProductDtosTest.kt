package com.amaxonia.erp.data.remote.dto

import com.amaxonia.erp.data.remote.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductDtosTest {

    @Test
    fun `deserializes product with photoUrl`() {
        val json = """{"id":"1","code":"P1","description":"Prod 1","photoUrl":"fotos/1.jpg"}"""
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("fotos/1.jpg", dto.photoUrl)
        assertEquals("fotos/1.jpg", dto.rawPhoto)
    }

    @Test
    fun `deserializes product with snake_case photo_url`() {
        val json = """{"id":"2","code":"P2","description":"Prod 2","photo_url":"fotos/2.jpg"}"""
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("fotos/2.jpg", dto.photoUrlSnake)
        assertEquals("fotos/2.jpg", dto.rawPhoto)
    }

    @Test
    fun `deserializes product with foto`() {
        val json = """{"id":"3","code":"P3","description":"Prod 3","foto":"fotos/3.jpg"}"""
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("fotos/3.jpg", dto.foto)
        assertEquals("fotos/3.jpg", dto.rawPhoto)
    }

    @Test
    fun `deserializes product with foto1`() {
        val json = """{"id":"4","code":"P4","description":"Prod 4","foto1":"fotos/4.jpg"}"""
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("fotos/4.jpg", dto.foto1)
        assertEquals("fotos/4.jpg", dto.rawPhoto)
    }

    @Test
    fun `rawPhoto precedence prioritizes photoUrl over foto`() {
        val json = """{"id":"5","code":"P5","description":"Prod 5","photoUrl":"fotos/p.jpg","foto":"fotos/f.jpg"}"""
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("fotos/p.jpg", dto.rawPhoto)
    }
}
