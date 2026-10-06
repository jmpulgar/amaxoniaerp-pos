package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.ComboGroupItemTable
import com.amaxoniaerp.features.kiosk.data.ComboGroupTable
import com.amaxoniaerp.features.kiosk.data.ComboItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteLineRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteResponse
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskClient
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cotización con opciones de combo (id_grupo_item), precio PHP y numeración diaria por caja. */
class KioskQuoteRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private val json = KioskTestSupport.json

    @BeforeTest
    fun setUp() {
        dataSource = KioskTestSupport.newDataSource("kiosk_quote")
        database = Database.connect(dataSource)
        KioskTestSupport.createParametrosGenerales(database)
        KioskTestSupport.createCajaSchema(database)
        KioskTestSupport.createComboTables(database)
        KioskTestSupport.createKioskOrderTables(database)
        seedItems()
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun newService() = KioskService(unlockRateLimiter = UnlockRateLimiter(), databaseResolver = { _, _ -> database })

    private fun seedItems() {
        transaction(database) {
            SchemaUtils.create(ItemsTablePA)
            item(101, "Hamburguesa Clásica", "5.50", stock = 20)
            item(102, "Hamburguesa Doble", "7.50", stock = 0)
            item(201, "Coca Cola Original", "1.75", stock = 50, coniva = "1.87")
            item(202, "Sprite", "1.75", stock = 0, coniva = "1.87")
            item(401, "Papas Fritas", "1.50", stock = 40)

            ComboItemTable.insert {
                it[idItemCombo] = 1
                it[idItem] = 101
                it[idCombo] = 1
            }
            group(10, "INCLUYE", "INCLUIDO", minimo = 1, maximo = 1, orden = 0)
            group(11, "BEBIDA", "COMBO", minimo = 1, maximo = 1, orden = 1, adiciona = 1)
            group(12, "EXTRAS", "MODIFICADOR", minimo = 0, maximo = 2, orden = 2, adiciona = 1)
            option(101, 10, idItem = 401, precio = "0.00", orden = 0)
            option(111, 11, idItem = 201, precio = null, orden = 0)
            option(112, 11, idItem = 202, precio = "0.00", orden = 1)
            option(121, 12, idItem = null, precio = "1.07", orden = 0, nombre = "TOCINETA")
            option(122, 12, idItem = null, precio = null, orden = 1, nombre = "SIN CEBOLLA")
        }
    }

    private fun item(
        id: Int,
        name: String,
        price: String,
        stock: Int,
        coniva: String = "0.00",
    ) {
        ItemsTablePA.insert {
            it[idItem] = id
            it[codItem] = "IT-$id"
            it[descripcion1] = name
            it[codDepartamento] = 1
            it[departamentoId] = 1
            it[precio1] = BigDecimal(price)
            it[coniva1] = BigDecimal(coniva)
            it[iva] = BigDecimal("7.00")
            it[existenciaTotal] = stock
            it[estatus] = "A"
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
    ) {
        ComboGroupItemTable.insert {
            it[idGrupoItem] = id
            it[idGrupo] = group
            it[ComboGroupItemTable.idItem] = idItem
            it[ComboGroupItemTable.nombre] = nombre
            it[ComboGroupItemTable.precio] = precio?.let(::BigDecimal)
            it[ComboGroupItemTable.orden] = orden
        }
    }

    private fun burger(
        modifiers: List<Int>,
        qty: Int = 1,
    ) = KioskQuoteRequest(
        diningMode = "COMER_AQUI",
        tableTent = "42",
        customerId = "CF",
        lines = listOf(KioskQuoteLineRequest(itemId = 101, qty = qty, note = "Bien cocida", modifiers = modifiers)),
    )

    private suspend fun HttpClient.quote(
        request: KioskQuoteRequest,
        idempotencyKey: String? = UUID.randomUUID().toString(),
        prefix: String = KioskTestSupport.PREFIX,
    ): HttpResponse =
        post("/api/v1/kiosk/orders/quote") {
            kioskHeaders(prefix = prefix)
            if (idempotencyKey != null) header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    @Test
    fun `POST quote prices combo options like the PHP POS and numbers orders per caja`() =
        testApplication {
            val client = kioskClient(newService())
            val idempotencyKey = UUID.randomUUID().toString()

            // 2 x Hamburguesa (5.50) + Coca (precio NULL → coniva1 1.87) + 2 x TOCINETA (1.07 c/u):
            // extra con ITBMS 4.01 → sin ITBMS 4.01 / 1.07 = 3.7477 → unitario 9.2477.
            // Subtotal 18.4954 → 18.50; ITBMS 7% = 1.295 → 1.30; total 19.80.
            val request = burger(modifiers = listOf(121, 111, 121), qty = 2)
            val response = client.quote(request, idempotencyKey)

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val quote = json.decodeFromString<KioskQuoteResponse>(response.bodyAsText())
            assertEquals(idempotencyKey, quote.orderId)
            assertEquals("K1-001", quote.formattedOrderNumber)
            assertEquals("18.50", quote.subtotal)
            assertEquals("1.30", quote.tax)
            assertEquals("19.80", quote.total)

            val line = quote.lines.single()
            assertEquals("9.25", line.unitPrice)
            assertEquals(listOf(111, 121), line.modifiers.map { it.id })
            assertEquals(listOf("Coca Cola Original", "2 x TOCINETA"), line.modifiers.map { it.name })
            assertEquals(listOf("1.75", "2.00"), line.modifiers.map { it.extraPrice })

            transaction(database) {
                val order = KioskOrderTable.selectAll().where { KioskOrderTable.id eq idempotencyKey }.single()
                assertEquals(KioskTestSupport.CAJA_ID, order[KioskOrderTable.idDispositivo].trim())
                assertEquals("COTIZADO", order[KioskOrderTable.estado])
                assertEquals(BigDecimal("19.8000"), order[KioskOrderTable.total])
                val item = KioskOrderItemTable.selectAll().where { KioskOrderItemTable.idPedido eq idempotencyKey }.single()
                assertEquals(BigDecimal("9.2477"), item[KioskOrderItemTable.precioUnitario])
                val mods =
                    KioskOrderItemModifierTable
                        .selectAll()
                        .where { KioskOrderItemModifierTable.idPedido eq idempotencyKey }
                        .orderBy(KioskOrderItemModifierTable.idModificador to SortOrder.ASC)
                        .map { it[KioskOrderItemModifierTable.idModificador] to it[KioskOrderItemModifierTable.precioAdicional] }
                assertEquals(listOf(111 to BigDecimal("1.7477"), 121 to BigDecimal("2.0000")), mods)
            }

            // Mismo Idempotency-Key: misma cotización.
            val repeat = json.decodeFromString<KioskQuoteResponse>(client.quote(request, idempotencyKey).bodyAsText())
            assertEquals("K1-001", repeat.formattedOrderNumber)
            assertEquals(quote.total, repeat.total)

            // La numeración es por caja; el prefijo viene del header (se pasa a mayúsculas).
            assertEquals("K1-002", json.decodeFromString<KioskQuoteResponse>(client.quote(request).bodyAsText()).formattedOrderNumber)
            val other = client.quote(request, prefix = "k7")
            assertEquals("K7-003", json.decodeFromString<KioskQuoteResponse>(other.bodyAsText()).formattedOrderNumber)
        }

    @Test
    fun `POST quote rejects when a mandatory group is omitted`() =
        testApplication {
            val response = kioskClient(newService()).quote(burger(modifiers = listOf(121)))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("El grupo 'BEBIDA' requiere al menos 1 selección(es)"))
        }

    @Test
    fun `POST quote rejects when a group exceeds maximo_veces`() =
        testApplication {
            val response = kioskClient(newService()).quote(burger(modifiers = listOf(111, 121, 121, 122)))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("El grupo 'EXTRAS' permite un máximo de 2 selección(es)"))
        }

    @Test
    fun `POST quote rejects options that are not selectable for the item`() =
        testApplication {
            val client = kioskClient(newService())
            // 101 es la opción INCLUIDO del combo: no se elige.
            for (invalid in listOf(101, 999)) {
                val response = client.quote(burger(modifiers = listOf(111, invalid)))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("Modificador $invalid no válido"))
            }
        }

    @Test
    fun `POST quote rejects sold out options and items`() =
        testApplication {
            val client = kioskClient(newService())

            val soldOutOption = client.quote(burger(modifiers = listOf(112)))
            assertEquals(HttpStatusCode.BadRequest, soldOutOption.status)
            assertTrue(soldOutOption.bodyAsText().contains("Modificador 'Sprite' se encuentra agotado"))

            val soldOutItem =
                client.quote(KioskQuoteRequest(diningMode = "PARA_LLEVAR", lines = listOf(KioskQuoteLineRequest(itemId = 102, qty = 1))))
            assertEquals(HttpStatusCode.BadRequest, soldOutItem.status)
            assertTrue(soldOutItem.bodyAsText().contains("se encuentra agotado"))
        }

    @Test
    fun `POST quote rejects when Idempotency-Key header is missing`() =
        testApplication {
            val response = kioskClient(newService()).quote(burger(modifiers = listOf(111)), idempotencyKey = null)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Idempotency-Key"))
        }

    @Test
    fun `POST quote answers 503 when the tenant has no kiosk order tables`() =
        testApplication {
            transaction(database) {
                SchemaUtils.drop(KioskOrderItemModifierTable, KioskOrderItemTable, KioskOrderTable)
            }
            val response = kioskClient(newService()).quote(burger(modifiers = listOf(111)))

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("El kiosco no está habilitado en esta empresa (falta migración)"))
        }
}
