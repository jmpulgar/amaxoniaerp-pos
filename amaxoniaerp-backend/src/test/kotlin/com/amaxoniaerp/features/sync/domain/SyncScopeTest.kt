package com.amaxoniaerp.features.sync.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncScopeTest {
    @Test
    fun `parametros ausentes o vacios significan TODOS`() {
        assertTrue(SyncScope.fromParams(null, null).allProducts)
        assertTrue(SyncScope.fromParams(null, null).allClients)
        assertTrue(SyncScope.fromParams("", "").allProducts)
        assertTrue(SyncScope.fromParams("   ", ",").allClients)
    }

    @Test
    fun `parsea csv con blancos deduplica y descarta no numericos`() {
        val expectedIds = setOf(3, 5, 9)
        val scope = SyncScope.fromParams(" 3, 5 ,3, x, 0, -2, 9", null)
        assertEquals(expectedIds, scope.departmentIds)
        assertFalse(scope.allProducts)
        assertTrue(scope.allClients)
    }

    @Test
    fun `parsea zoneCodes y restringe alcance de clientes`() {
        val expectedZones = setOf("Z-1", "Z-2")
        val scope = SyncScope.fromParams(null, null, " Z-1, Z-2 , Z-1 ")
        assertEquals(expectedZones, scope.zoneCodes)
        assertTrue(scope.allProducts)
        assertFalse(scope.allClients)
    }

    @Test
    fun `el alcance restringido limita la cantidad de ids por filtro`() {
        val extra = 50
        val many = (1..(SyncScope.MAX_IDS_PER_FILTER + extra)).joinToString(",") { it.toString() }
        val scope = SyncScope.fromParams(many, many)
        assertEquals(SyncScope.MAX_IDS_PER_FILTER, scope.departmentIds.size)
        assertEquals(SyncScope.MAX_IDS_PER_FILTER, scope.branchIds.size)
    }

    @Test
    fun `ALL es el scope por defecto y sin restriccion`() {
        assertTrue(SyncScope.ALL.allProducts)
        assertTrue(SyncScope.ALL.allClients)
    }
}
