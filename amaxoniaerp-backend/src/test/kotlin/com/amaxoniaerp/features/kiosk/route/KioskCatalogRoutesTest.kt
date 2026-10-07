package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.items.data.DepartamentoTable
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.ComboGroupItemTable
import com.amaxoniaerp.features.kiosk.data.ComboGroupTable
import com.amaxoniaerp.features.kiosk.data.ComboItemTable
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskClient
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Catálogo del kiosco con combos/modificadores del modelo del ERP (item_combos → grupos → grupo_items). */
class KioskCatalogRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private val json = KioskTestSupport.json

    @BeforeTest
    fun setUp() {
        dataSource = KioskTestSupport.newDataSource("kiosk_cat")
        database = Database.connect(dataSource)
        KioskTestSupport.createParametrosGenerales(database)
        KioskTestSupport.createCajaSchema(database)
        KioskTestSupport.createComboTables(database)
        seedCatalog()
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun newService() = KioskService(unlockRateLimiter = UnlockRateLimiter(), databaseResolver = { _, _ -> database })

    private fun seedCatalog() {
        transaction(database) {
            SchemaUtils.create(DepartamentoTable, ItemsTablePA)
            department(1, "HAM", "Hamburguesas", visible = true, visiblePos = 1, foto = "fotos/1_foto.png")
            department(2, "BEB", "Bebidas", visible = true, visiblePos = 1)
            department(3, "INS", "Insumos Cocina", visible = true, visiblePos = 0)
            department(4, "DESC", "Descontinuados", visible = false, visiblePos = 1)

            item(101, 1, "Hamburguesa Clásica", "5.50", stock = 20, description = "Carne 100% res", foto = "fotos/burger.jpg")
            item(102, 1, "Hamburguesa Doble", "7.50", stock = 0)
            item(201, 2, "Coca Cola Original", "1.75", coniva = "1.87", stock = 50)
            item(202, 2, "Sprite", "1.75", coniva = "1.87", stock = 0)
            item(203, 2, "Malta Descontinuada", "1.00", stock = 10, estatus = "I")
            item(301, 3, "Pan Brioche x 50", "12.00", stock = 10)
            item(401, 3, "Papas Fritas", "1.50", stock = 40)

            ComboItemTable.insert {
                it[idItemCombo] = 1
                it[idItem] = 101
                it[idCombo] = 1
            }
            group(id = 10, name = "INCLUYE", tipo = "INCLUIDO", minimo = 1, maximo = 1, orden = 0)
            group(id = 12, name = "EXTRAS", tipo = "MODIFICADOR", minimo = 0, maximo = 0, orden = 2, adiciona = 1)
            group(id = 11, name = "BEBIDA", tipo = "COMBO", minimo = 1, maximo = 1, orden = 1, adiciona = 1)

            option(id = 101, group = 10, idItem = 401, precio = "0.00", orden = 0, porDefecto = 1)
            // precio NULL + grupo que adiciona → coniva1 del producto (1.87, con impuesto).
            option(id = 111, group = 11, idItem = 201, precio = null, orden = 0, porDefecto = 1)
            option(id = 112, group = 11, idItem = 202, precio = "0.00", orden = 1)
            option(id = 113, group = 11, idItem = 203, precio = "0.00", orden = 2)
            option(id = 122, group = 12, idItem = null, nombre = "SIN CEBOLLA", precio = null, orden = 1)
            option(id = 121, group = 12, idItem = null, nombre = "TOCINETA", precio = "1.07", orden = 0)
        }
    }

    private fun department(
        id: Int,
        code: String,
        name: String,
        visible: Boolean,
        visiblePos: Int,
        foto: String? = null,
    ) {
        DepartamentoTable.insert {
            it[DepartamentoTable.id] = id
            it[codigo] = code
            it[descripcion] = name
            it[DepartamentoTable.visible] = visible
            it[DepartamentoTable.visiblePos] = visiblePos
            it[DepartamentoTable.foto] = foto
        }
    }

    private fun item(
        id: Int,
        department: Int,
        name: String,
        price: String,
        stock: Int,
        coniva: String = "0.00",
        description: String? = null,
        foto: String? = null,
        estatus: String = "A",
    ) {
        ItemsTablePA.insert {
            it[idItem] = id
            it[codItem] = "IT-$id"
            it[descripcion1] = name
            it[descripcion2] = description
            it[codDepartamento] = department
            it[departamentoId] = department
            it[precio1] = BigDecimal(price)
            it[coniva1] = BigDecimal(coniva)
            it[iva] = BigDecimal("7.00")
            it[ItemsTablePA.foto] = foto
            it[existenciaTotal] = stock
            it[ItemsTablePA.estatus] = estatus
            it[visiblePos] = 'T'
        }
    }

    private fun group(
        id: Int,
        name: String,
        tipo: String,
        minimo: Int,
        maximo: Int,
        orden: Int,
        adiciona: Int = 0,
    ) {
        ComboGroupTable.insert {
            it[idGrupo] = id
            it[idCombo] = 1
            it[nombreGrupo] = name
            it[ComboGroupTable.adiciona] = adiciona
            it[maximoVeces] = maximo
            it[ComboGroupTable.tipo] = tipo
            it[ComboGroupTable.minimo] = minimo
            it[ComboGroupTable.orden] = orden
        }
    }

    private fun option(
        id: Int,
        group: Int,
        idItem: Int?,
        precio: String?,
        orden: Int,
        nombre: String? = null,
        porDefecto: Int = 0,
    ) {
        ComboGroupItemTable.insert {
            it[idGrupoItem] = id
            it[idGrupo] = group
            it[ComboGroupItemTable.idItem] = idItem
            it[ComboGroupItemTable.nombre] = nombre
            it[ComboGroupItemTable.precio] = precio?.let(::BigDecimal)
            it[ComboGroupItemTable.porDefecto] = porDefecto
            it[ComboGroupItemTable.orden] = orden
        }
    }

    private suspend fun HttpClient.catalog(): KioskCatalogResponse {
        val response = get("/api/v1/kiosk/catalog") { kioskHeaders() }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<KioskCatalogResponse>(response.bodyAsText())
    }

    @Test
    fun `GET catalog returns visible departments, price level A and stock`() =
        testApplication {
            val catalog = kioskClient(newService()).catalog()

            assertEquals(listOf("Bebidas", "Hamburguesas"), catalog.categories.map { it.name })
            // Imagen propia del departamento (departamento.foto), no la del primer producto.
            assertEquals(
                listOf(null, "/api/data/PA/momi_pa/departamento/1_foto.png"),
                catalog.categories.map { it.iconUrl },
            )
            assertEquals(setOf(101, 102, 201, 202), catalog.items.map { it.id }.toSet())

            val burger = catalog.items.first { it.id == 101 }
            assertEquals("5.50", burger.price)
            assertEquals("7.00", burger.taxRate)
            assertEquals("/api/data/PA/momi_pa/item/burger.jpg", burger.imageUrl)
            assertFalse(burger.soldOut)
            assertTrue(catalog.items.first { it.id == 102 }.soldOut)
            assertTrue(
                catalog.items
                    .first { it.id == 102 }
                    .modifierGroups
                    .isEmpty(),
            )
        }

    @Test
    fun `GET catalog maps grupos and grupo_items to modifier groups`() =
        testApplication {
            val burger = kioskClient(newService()).catalog().items.first { it.id == 101 }

            // INCLUIDO no se elige: se resume en la descripción.
            assertEquals("Carne 100% res. Incluye: Papas Fritas", burger.description)
            assertEquals(listOf(11, 12), burger.modifierGroups.map { it.id })

            val bebida = burger.modifierGroups[0]
            assertEquals("BEBIDA", bebida.name)
            assertEquals(1, bebida.min)
            assertEquals(1, bebida.max)
            assertTrue(bebida.isMandatory)
            assertTrue(bebida.isCombo)
            // La opción con producto inactivo (113) se omite.
            assertEquals(listOf(111, 112), bebida.options.map { it.id })
            val coca = bebida.options[0]
            assertEquals("Coca Cola Original", coca.name)
            // coniva1 1.87 con impuesto → 1.75 sin impuesto (misma base que price).
            assertEquals("1.75", coca.extraPrice)
            assertTrue(coca.isDefault)
            assertFalse(coca.soldOut)
            val sprite = bebida.options[1]
            assertEquals("0.00", sprite.extraPrice)
            assertTrue(sprite.soldOut)
            assertFalse(sprite.isDefault)

            val extras = burger.modifierGroups[1]
            assertEquals("EXTRAS", extras.name)
            assertEquals(0, extras.min)
            // maximo_veces 0 = sin límite = cantidad de opciones.
            assertEquals(2, extras.max)
            assertFalse(extras.isMandatory)
            assertFalse(extras.isCombo)
            assertEquals(listOf("TOCINETA", "SIN CEBOLLA"), extras.options.map { it.name })
            assertEquals(listOf("1.00", "0.00"), extras.options.map { it.extraPrice })
        }

    @Test
    fun `GET catalog without the migrated combo model sells products without options`() =
        testApplication {
            transaction(database) { exec("ALTER TABLE grupos DROP COLUMN tipo") }
            val burger = kioskClient(newService()).catalog().items.first { it.id == 101 }

            assertEquals("Carne 100% res", burger.description)
            assertTrue(burger.modifierGroups.isEmpty())
        }

    @Test
    fun `GET catalog works when grupos has no orden column`() =
        testApplication {
            transaction(database) { exec("ALTER TABLE grupos DROP COLUMN orden") }
            val burger = kioskClient(newService()).catalog().items.first { it.id == 101 }

            // Sin orden: por id_grupo.
            assertEquals(listOf(11, 12), burger.modifierGroups.map { it.id })
        }

    @Test
    fun `GET catalog returns 304 Not Modified when If-None-Match matches ETag`() =
        testApplication {
            val client = kioskClient(newService())
            val first = client.get("/api/v1/kiosk/catalog") { kioskHeaders() }
            assertEquals(HttpStatusCode.OK, first.status)
            val etag = first.headers[HttpHeaders.ETag]
            assertNotNull(etag)

            val second =
                client.get("/api/v1/kiosk/catalog") {
                    kioskHeaders()
                    header(HttpHeaders.IfNoneMatch, etag)
                }
            assertEquals(HttpStatusCode.NotModified, second.status)
            assertEquals("", second.bodyAsText())
        }

    @Test
    fun `verify catalog-response fixture deserializes cleanly`() {
        val content =
            listOf("../contracts/kiosk/catalog-response.json", "contracts/kiosk/catalog-response.json")
                .map(::File)
                .first { it.exists() }
                .readText()

        val response = json.decodeFromString<KioskCatalogResponse>(content)
        assertEquals(2, response.categories.size)
        assertEquals(2, response.items.size)
        val burger = response.items[0]
        assertEquals("Hamburguesa Clásica", burger.name)
        assertEquals(2, burger.modifierGroups.size)
        assertTrue(burger.modifierGroups[0].options[0].isDefault)
        assertFalse(burger.modifierGroups[1].options[0].isDefault)
    }
}
