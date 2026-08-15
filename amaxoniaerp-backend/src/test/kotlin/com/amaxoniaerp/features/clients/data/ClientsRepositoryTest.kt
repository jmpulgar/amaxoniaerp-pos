package com.amaxoniaerp.features.clients.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientsRepositoryTest {
    private lateinit var database: Database
    private val repository = ClientsRepository()

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:clients_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(ClientsTable)
            insertClient(id = "client-credit", permiteCredito = true, dias = 30)
            insertClient(id = "client-cash", permiteCredito = false, dias = 0)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(ClientsTable)
        }
    }

    @Test
    fun `client mapping preserves credit permission and days`() =
        runBlocking {
            val clients = repository.listClients(database, limit = 10, offset = 0, search = null, includeTotal = true)

            val byId = clients.first.associateBy { it.id }
            assertEquals(true, byId.getValue("client-credit").permiteCredito)
            assertEquals(30, byId.getValue("client-credit").diasCredito)
            assertEquals(false, byId.getValue("client-cash").permiteCredito)
            assertEquals(0, byId.getValue("client-cash").diasCredito)
        }

    private fun insertClient(
        id: String,
        permiteCredito: Boolean,
        dias: Int,
    ) {
        ClientsTable.insert {
            it[idCliente] = id
            it[codCliente] = id
            it[rif] = "ID-$id"
            it[dv] = ""
            it[nombre] = "CLIENTE"
            it[apellido] = "PRUEBA"
            it[direccion] = "DIRECCION"
            it[telefonos] = "0000"
            it[email] = "$id@example.invalid"
            it[estado] = "A"
            it[pais] = 170
            it[codTipoCliente] = 1
            it[tipoContribuyente] = 1
            it[ClientsTable.permiteCredito] = permiteCredito
            it[limite] = 0.0
            it[ClientsTable.dias] = dias
        }
    }
}
