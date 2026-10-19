package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.features.clients.domain.CreateClientRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
            insertClient(id = "client-credit", code = "000000001", permiteCredito = true, dias = 30)
            insertClient(id = "client-cash", code = "000000002", permiteCredito = false, dias = 0)
            insertClient(id = "client-generic", code = "999999999", permiteCredito = false, dias = 0)
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

    @Test
    fun `createClient generates sequential code ignoring 999999999`() =
        runBlocking {
            val req =
                CreateClientRequest(
                    identification = "8-123-456",
                    name = "NUEVO CLIENTE",
                    lastName = "PANAMA",
                    phone = "6666-7777",
                )
            val created = repository.createClient(database, "PA", req)
            assertEquals("000000003", created.code)
            assertEquals("NUEVO CLIENTE", created.name)
            assertEquals("8-123-456", created.identification)
        }

    @Test
    fun `updateClient updates existing client by id or code`() =
        runBlocking {
            val req =
                CreateClientRequest(
                    identification = "8-888-888-MOD",
                    name = "EMPRESA MODIFICADA",
                    lastName = "ALFA",
                    permiteCredito = true,
                    diasCredito = 45,
                )
            val updatedById = repository.updateClient(database, "client-credit", req)
            assertNotNull(updatedById)
            assertEquals("EMPRESA MODIFICADA", updatedById.name)
            assertEquals(45, updatedById.diasCredito)

            val updatedByCode =
                repository.updateClient(
                    database,
                    "000000002",
                    req.copy(name = "CLIENTE CASH MODIFICADO"),
                )
            assertNotNull(updatedByCode)
            assertEquals("CLIENTE CASH MODIFICADO", updatedByCode.name)
        }

    private fun insertClient(
        id: String,
        code: String = id,
        permiteCredito: Boolean,
        dias: Int,
    ) {
        ClientsTable.insert {
            it[idCliente] = id
            it[codCliente] = code
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
