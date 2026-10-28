package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.clients.domain.CreateClientRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            SchemaUtils.create(ClientsTable, VendedorTable, SucursalTable)
            exec("CREATE TABLE IF NOT EXISTS parametros_generales (cod_empresa INT, default_cod_cliente_factura VARCHAR(80))")
            insertClient(id = "client-credit", code = "000000001", permiteCredito = true, dias = 30)
            insertClient(id = "client-cash", code = "000000002", permiteCredito = false, dias = 0)
            insertClient(id = "client-generic", code = "999999999", permiteCredito = false, dias = 0)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            exec("DROP TABLE IF EXISTS parametros_generales")
            SchemaUtils.drop(SucursalTable, VendedorTable, ClientsTable)
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

    @Test
    fun `listClients filtra por vendedor cuando id_clientes no esta vacio`() =
        runBlocking {
            transaction(database) {
                VendedorTable.insert {
                    it[idVendedor] = 10
                    it[codVendedor] = 10
                    it[nombre] = "Vendedor Con Clientes"
                    it[codUsuarios] = "1"
                    it[idTiendas] = "1"
                    it[idCajas] = "1"
                    it[activo] = 1
                    it[idClientes] = "client-credit"
                    it[crearClientes] = 1
                }
            }

            val filtered =
                repository.listClients(
                    database,
                    limit = 10,
                    offset = 0,
                    search = null,
                    includeTotal = true,
                    sellerId = 10,
                )
            assertEquals(1, filtered.first.size)
            assertEquals("client-credit", filtered.first.first().id)
        }

    @Test
    fun `listClients devuelve todos cuando id_clientes del vendedor esta vacio`() =
        runBlocking {
            transaction(database) {
                VendedorTable.insert {
                    it[idVendedor] = 20
                    it[codVendedor] = 20
                    it[nombre] = "Vendedor Sin Restriccion"
                    it[codUsuarios] = "1"
                    it[idTiendas] = "1"
                    it[idCajas] = "1"
                    it[activo] = 1
                    it[idClientes] = ""
                    it[crearClientes] = 1
                }
            }

            val all =
                repository.listClients(
                    database,
                    limit = 10,
                    offset = 0,
                    search = null,
                    includeTotal = true,
                    sellerId = 20,
                )
            assertEquals(3, all.first.size)
        }

    @Test
    fun `createClient autoasocia cliente al vendedor si sellerId viene informado`() =
        runBlocking {
            transaction(database) {
                VendedorTable.insert {
                    it[idVendedor] = 30
                    it[codVendedor] = 30
                    it[nombre] = "Vendedor Para Autoasociar"
                    it[codUsuarios] = "1"
                    it[idTiendas] = "1"
                    it[idCajas] = "1"
                    it[activo] = 1
                    it[idClientes] = "client-cash"
                    it[crearClientes] = 1
                }
            }

            val req =
                CreateClientRequest(
                    name = "CLIENTE AUTOASOCIADO",
                    sellerId = 30,
                )
            val created = repository.createClient(database, "PA", req)
            assertNotNull(created.id)
            assertEquals("00000", created.identification)

            val sellerRow =
                transaction(database) {
                    VendedorTable
                        .select(VendedorTable.idClientes)
                        .where { VendedorTable.idVendedor eq 30 }
                        .single()
                }
            val assigned = sellerRow[VendedorTable.idClientes]
            assertNotNull(assigned)
            assertEquals("client-cash,${created.id}", assigned)
        }

    @Test
    fun `canSellerCreateClients respeta permiso crear_clientes`() =
        runBlocking {
            transaction(database) {
                VendedorTable.insert {
                    it[idVendedor] = 40
                    it[codVendedor] = 40
                    it[nombre] = "Vendedor Bloqueado"
                    it[codUsuarios] = "1"
                    it[idTiendas] = "1"
                    it[idCajas] = "1"
                    it[activo] = 1
                    it[idClientes] = ""
                    it[crearClientes] = 0
                }
            }

            val canCreate = repository.canSellerCreateClients(database, 40)
            assertEquals(false, canCreate)
        }

    @Test
    fun `getDefaultClient retorna cliente de sucursal cuando la sucursal tiene id_cliente_default`() =
        runBlocking {
            transaction(database) {
                SucursalTable.insert {
                    it[idSucursal] = 1
                    it[sucursal] = "Sucursal con cliente default"
                    it[idClienteDefault] = "client-credit"
                }
                exec("INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura) VALUES (1, '000000002')")
            }

            val defaultClient = repository.getDefaultClient(database, "PA", sucursalId = 1)
            assertNotNull(defaultClient)
            assertEquals("client-credit", defaultClient.id)
        }

    @Test
    fun `getDefaultClient cae a parametros_generales cuando sucursal no tiene id_cliente_default`() =
        runBlocking {
            transaction(database) {
                SucursalTable.insert {
                    it[idSucursal] = 2
                    it[sucursal] = "Sucursal sin cliente default"
                    it[idClienteDefault] = null
                }
                exec("INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura) VALUES (1, '000000002')")
            }

            val defaultClient = repository.getDefaultClient(database, "PA", sucursalId = 2)
            assertNotNull(defaultClient)
            assertEquals("client-cash", defaultClient.id)
        }

    @Test
    fun `getDefaultClient cae a parametros_generales cuando sucursalId es null`() =
        runBlocking {
            transaction(database) {
                exec("INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura) VALUES (1, '000000001')")
            }

            val defaultClient = repository.getDefaultClient(database, "PA", sucursalId = null)
            assertNotNull(defaultClient)
            assertEquals("client-credit", defaultClient.id)
        }

    @Test
    fun `getDefaultClient retorna null cuando ni sucursal ni parametros_generales tienen default`() =
        runBlocking {
            transaction(database) {
                SucursalTable.insert {
                    it[idSucursal] = 3
                    it[sucursal] = "Sucursal vacia"
                    it[idClienteDefault] = null
                }
            }

            val defaultClient = repository.getDefaultClient(database, "PA", sucursalId = 3)
            assertNull(defaultClient)

            val defaultClientSinSucursal = repository.getDefaultClient(database, "PA", sucursalId = null)
            assertNull(defaultClientSinSucursal)
        }

    @Test
    fun `getDefaultClient cae a parametros_generales si cliente de sucursal no existe en clientes`() =
        runBlocking {
            transaction(database) {
                SucursalTable.insert {
                    it[idSucursal] = 4
                    it[sucursal] = "Sucursal con cliente inexistente"
                    it[idClienteDefault] = "cliente-inexistente"
                }
                exec("INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura) VALUES (1, '000000002')")
            }

            val defaultClient = repository.getDefaultClient(database, "PA", sucursalId = 4)
            assertNotNull(defaultClient)
            assertEquals("client-cash", defaultClient.id)
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
