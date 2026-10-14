package com.amaxoniaerp.features.items.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemsTaxonomyQueriesTest {
    private val repository = ItemsRepository()
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:items_tax_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(DepartamentoTable)
            DepartamentoTable.insert {
                it[id] = 1
                it[codigo] = "DEP1"
                it[descripcion] = "Bebidas"
                it[visible] = true
                it[visiblePos] = 1
            }
            DepartamentoTable.insert {
                it[id] = 2
                it[codigo] = "DEP2"
                it[descripcion] = "Comida"
                it[visible] = true
                it[visiblePos] = 1
            }
            DepartamentoTable.insert {
                it[id] = 3
                it[codigo] = "DEP3"
                it[descripcion] = "Insumos"
                it[visible] = true
                it[visiblePos] = 0
            }
            DepartamentoTable.insert {
                it[id] = 4
                it[codigo] = "DEP4"
                it[descripcion] = "Ocultos"
                it[visible] = false
                it[visiblePos] = 1
            }
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(DepartamentoTable)
        }
    }

    @Test
    fun `listDepartments without visible_pos returns all departments`() =
        runBlocking {
            val all = repository.listDepartments(database, onlyVisiblePos = false)
            assertEquals(4, all.size)
        }

    @Test
    fun `listDepartments with visible_pos returns only visible POS departments`() =
        runBlocking {
            val visible = repository.listDepartments(database, onlyVisiblePos = true)
            assertEquals(2, visible.size)
            val ids = visible.map { it.first }.toSet()
            assertEquals(setOf(1, 2), ids)
        }
}
