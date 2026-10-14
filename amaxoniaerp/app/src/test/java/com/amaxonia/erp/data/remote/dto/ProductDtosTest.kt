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

    @Test
    fun `deserializes product with combo and options attributes`() {
        val json = """
            {
                "id": "100",
                "code": "BURGER-COMBO",
                "description": "Hamburguesa Doble",
                "posee_producto_combo": "si",
                "id_combo": "12",
                "tipo_articulo": "C",
                "posee_variantes": "no",
                "variantes_total": 0
            }
        """.trimIndent()
        val dto = AppJson.decodeFromString<ProductDto>(json)
        assertEquals("si", dto.poseeProductoCombo)
        assertEquals("12", dto.idCombo)
        assertEquals("C", dto.tipoArticulo)
        assertEquals("no", dto.poseeVariantes)
        assertEquals(0, dto.variantesTotal)
    }

    @Test
    fun `deserializes product options response correctly`() {
        val json = """
            {
                "success": true,
                "id_combo": "12",
                "grupos": [
                    {
                        "id_grupo": "1",
                        "tipo": "INCLUIDO",
                        "nombre": "INCLUYE",
                        "minimo": 1,
                        "maximo": 1,
                        "opciones": [
                            {
                                "id_grupo_item": "10",
                                "id_item": "50",
                                "cod_item": "PAPAS",
                                "nombre": "Papas Fritas Medianas",
                                "precio": 0.0,
                                "cantidad": 1.0,
                                "por_defecto": 1,
                                "agotado": 0
                            }
                        ]
                    },
                    {
                        "id_grupo": "2",
                        "tipo": "COMBO",
                        "nombre": "BEBIDA",
                        "minimo": 1,
                        "maximo": 1,
                        "opciones": [
                            {
                                "id_grupo_item": "20",
                                "id_item": "55",
                                "cod_item": "COCA",
                                "nombre": "Coca Cola 355ml",
                                "precio": 0.0,
                                "cantidad": 1.0,
                                "por_defecto": 1,
                                "agotado": 0
                            },
                            {
                                "id_grupo_item": "21",
                                "id_item": "56",
                                "cod_item": "JUGO",
                                "nombre": "Jugo Natural",
                                "precio": 0.75,
                                "cantidad": 1.0,
                                "por_defecto": 0,
                                "agotado": 0
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val dto = AppJson.decodeFromString<ProductOptionsResponseDto>(json)
        assertEquals(true, dto.success)
        assertEquals("12", dto.idCombo)
        assertEquals(2, dto.grupos.size)
        assertEquals("INCLUIDO", dto.grupos[0].tipo)
        assertEquals("BEBIDA", dto.grupos[1].nombre)
        assertEquals(2, dto.grupos[1].opciones.size)
        assertEquals(0.75, dto.grupos[1].opciones[1].precio, 0.001)
    }

    @Test
    fun `deserializes product options with numeric IDs and data wrapper correctly`() {
        val json = """
            {
                "success": true,
                "id_combo": 1,
                "data": [
                    {
                        "id_grupo": 1,
                        "tipo": "COMBO",
                        "nombre": "ACOMPAÑAMIENTOS",
                        "minimo": 1,
                        "maximo": 1,
                        "opciones": [
                            {
                                "id_grupo_item": 1,
                                "id_item": 3762,
                                "cod_item": "P00074",
                                "nombre": "PATACONES",
                                "precio": 1.60,
                                "cantidad": 1,
                                "por_defecto": 0,
                                "agotado": 0
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val dto = AppJson.decodeFromString<ProductOptionsResponseDto>(json)
        assertEquals("1", dto.idCombo)
        assertEquals(1, dto.resolvedGroups.size)
        assertEquals("1", dto.resolvedGroups[0].idGrupo)
        assertEquals("ACOMPAÑAMIENTOS", dto.resolvedGroups[0].nombre)
        assertEquals(1, dto.resolvedGroups[0].opciones.size)
        assertEquals("1", dto.resolvedGroups[0].opciones[0].idGrupoItem)
        assertEquals("3762", dto.resolvedGroups[0].opciones[0].idItem)
        assertEquals("PATACONES", dto.resolvedGroups[0].opciones[0].nombre)
        assertEquals(1.60, dto.resolvedGroups[0].opciones[0].precio, 0.001)
    }
}
