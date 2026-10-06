package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.items.data.DepartamentoTable
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.ItemModifierGroupTable
import com.amaxoniaerp.features.kiosk.data.ItemModifierRelationTable
import com.amaxoniaerp.features.kiosk.data.ItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskCatalogRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteLineRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KioskQuoteRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    private val jwtConfig =
        JwtConfig(
            secret = "test-secret-kiosk-test-must-be-very-long-32-chars",
            domain = "http://localhost:8080",
            audience = "http://localhost:8080/kiosk",
            realm = "Amaxonia Kiosk Test",
        )

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    private val kioskDeviceRepository = KioskDeviceRepository()
    private val kioskCatalogRepository = KioskCatalogRepository()
    private val kioskOrderRepository = KioskOrderRepository()
    private val unlockRateLimiter = UnlockRateLimiter()
    private lateinit var kioskService: KioskService

    private val testCountry = "PA"
    private val testCompanyDb = "momi_pa"
    private val testDeviceId = UUID.randomUUID().toString()

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_quote_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        database = Database.connect(dataSource)

        kioskService =
            KioskService(
                kioskDeviceRepository = kioskDeviceRepository,
                unlockRateLimiter = unlockRateLimiter,
                jwtConfig = jwtConfig,
                databaseResolver = { _, _ -> database },
                kioskCatalogRepository = kioskCatalogRepository,
                kioskOrderRepository = kioskOrderRepository,
            )

        transaction(database) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS parametros_generales (
                    cod_empresa INT PRIMARY KEY,
                    default_cod_cliente_factura VARCHAR(80) NOT NULL DEFAULT 'CF',
                    default_id_formapago_factura INT NOT NULL DEFAULT 1,
                    porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL DEFAULT 7.00,
                    validar_stock VARCHAR(2) NOT NULL DEFAULT 'SI',
                    dias_vencimiento INT NOT NULL DEFAULT 30,
                    cod_almacen INT NOT NULL DEFAULT 1,
                    rif VARCHAR(50) NULL,
                    moneda VARCHAR(50) NOT NULL DEFAULT 'USD',
                    moneda_base INT NULL,
                    bloquear_itbms VARCHAR(2) NOT NULL DEFAULT 'NO',
                    facturar_cero TINYINT(1) NOT NULL DEFAULT 0,
                    impresion_directa TINYINT(1) NOT NULL DEFAULT 0,
                    tipo_facturacion INT NOT NULL DEFAULT 0,
                    kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'RETIRO_MOSTRADOR',
                    kiosco_impresora_cocina_ip VARCHAR(45) NULL,
                    kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI,PARA_LLEVAR',
                    kiosco_color_marca CHAR(7) NULL,
                    kiosco_config_version INT NOT NULL DEFAULT 1,
                    clave_kiosko VARCHAR(255) NULL
                );
                """.trimIndent(),
            )
            exec("INSERT INTO parametros_generales (cod_empresa, validar_stock, porcentaje_impuesto_principal) VALUES (1, 'SI', 7.00);")

            SchemaUtils.create(
                KioskDeviceTable,
                DepartamentoTable,
                ItemsTablePA,
                ItemModifierGroupTable,
                ItemModifierTable,
                ItemModifierRelationTable,
                KioskOrderTable,
                KioskOrderItemTable,
                KioskOrderItemModifierTable,
            )

            // Kiosk Device
            KioskDeviceTable.insert {
                it[id] = testDeviceId
                it[nombre] = "Kiosco Albrook"
                it[prefijoPedido] = "K1"
                it[idCaja] = "CAJA-01"
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 10
                it[idClienteGenerico] = "CF"
                it[activo] = true
                it[creadoEn] = LocalDateTime.now()
            }

            // Departamentos
            DepartamentoTable.insert {
                it[id] = 1
                it[codigo] = "HAM"
                it[descripcion] = "Hamburguesas"
                it[visible] = true
                it[visiblePos] = 1
            }

            // Items
            ItemsTablePA.insert {
                it[idItem] = 101
                it[codItem] = "HAM-01"
                it[descripcion1] = "Hamburguesa Clásica"
                it[descripcion2] = "Carne 100% res y queso cheddar"
                it[codDepartamento] = 1
                it[departamentoId] = 1
                it[precio1] = BigDecimal("5.50")
                it[iva] = BigDecimal("7.00")
                it[existenciaTotal] = 20
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }
            ItemsTablePA.insert {
                it[idItem] = 102
                it[codItem] = "HAM-02"
                it[descripcion1] = "Hamburguesa Doble"
                it[codDepartamento] = 1
                it[departamentoId] = 1
                it[precio1] = BigDecimal("7.50")
                it[iva] = BigDecimal("7.00")
                it[existenciaTotal] = 0 // Agotado
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }

            // Modificadores para 101
            // Grupo 1: Bebida (Mandatory, Min 1, Max 1)
            ItemModifierGroupTable.insert {
                it[id] = 1
                it[nombre] = "Bebida del Combo"
                it[minSeleccion] = 1
                it[maxSeleccion] = 1
                it[esObligatorio] = true
                it[esCombo] = true
                it[orden] = 1
                it[activo] = true
            }
            // Grupo 2: Extras (Opcional, Min 0, Max 2)
            ItemModifierGroupTable.insert {
                it[id] = 2
                it[nombre] = "Extras"
                it[minSeleccion] = 0
                it[maxSeleccion] = 2
                it[esObligatorio] = false
                it[esCombo] = false
                it[orden] = 2
                it[activo] = true
            }

            ItemModifierRelationTable.insert {
                it[idItem] = 101
                it[idGrupo] = 1
                it[orden] = 1
            }
            ItemModifierRelationTable.insert {
                it[idItem] = 101
                it[idGrupo] = 2
                it[orden] = 2
            }

            // Opciones Grupo 1
            ItemModifierTable.insert {
                it[id] = 1
                it[idGrupo] = 1
                it[nombre] = "Coca Cola Sin Azúcar"
                it[precioAdicional] = BigDecimal("0.0000")
                it[orden] = 1
                it[activo] = true
            }
            ItemModifierTable.insert {
                it[id] = 2
                it[idGrupo] = 1
                it[nombre] = "Sprite"
                it[precioAdicional] = BigDecimal("0.0000")
                it[orden] = 2
                it[activo] = true
            }

            // Opciones Grupo 2
            ItemModifierTable.insert {
                it[id] = 3
                it[idGrupo] = 2
                it[nombre] = "Tocineta Extra"
                it[precioAdicional] = BigDecimal("1.5000")
                it[orden] = 1
                it[activo] = true
            }
            ItemModifierTable.insert {
                it[id] = 4
                it[idGrupo] = 2
                it[nombre] = "Queso Cheddar Extra"
                it[precioAdicional] = BigDecimal("1.0000")
                it[orden] = 2
                it[activo] = true
            }
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun generateValidKioskJwt(): String =
        JWT
            .create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "kiosk")
            .withClaim("role", "KIOSK")
            .withClaim("device_id", testDeviceId)
            .withClaim("country_code", testCountry)
            .withClaim("company_db", testCompanyDb)
            .withClaim("admin_db", testCompanyDb)
            .withClaim("prefix", "K1")
            .withClaim("customer_id", "CF")
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    private fun ApplicationTestBuilder.installKtorSecurity() {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(json)
        }
        install(io.ktor.server.auth.Authentication) {
            jwt {
                realm = jwtConfig.realm ?: "test"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(jwtConfig.secret))
                        .withAudience(jwtConfig.audience)
                        .withIssuer(jwtConfig.domain)
                        .build(),
                )
                validate { credential ->
                    if (credential.payload.audience.contains(jwtConfig.audience)) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            }
        }
    }

    @Test
    fun `POST quote calculates exact totals with modifiers in Money and assigns K1-001`() =
        testApplication {
            installKtorSecurity()
            routing {
                kioskRoutes(kioskService)
            }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val token = generateValidKioskJwt()
            val idempotencyKey = UUID.randomUUID().toString()

            // Pedido: 2 x Hamburguesa Clásica (base 5.50 + tocineta 1.50 + bebida 0.00 = 7.00 unitario)
            // Subtotal = 14.00, Impuesto 7% = 0.98, Total = 14.98
            val quoteRequest =
                KioskQuoteRequest(
                    diningMode = "COMER_AQUI",
                    tableTent = "42",
                    customerId = "CF",
                    lines =
                        listOf(
                            KioskQuoteLineRequest(
                                itemId = 101,
                                qty = 2,
                                note = "Bien cocida",
                                modifiers = listOf(1, 3), // 1 = Coca Cola, 3 = Tocineta Extra (+1.50)
                            ),
                        ),
                )

            val response =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", idempotencyKey)
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val quote = json.decodeFromString<KioskQuoteResponse>(response.bodyAsText())

            assertEquals(idempotencyKey, quote.orderId)
            assertEquals("K1-001", quote.formattedOrderNumber)
            assertEquals("14.00", quote.subtotal)
            assertEquals("0.98", quote.tax)
            assertEquals("14.98", quote.total)
            assertEquals("COMER_AQUI", quote.diningMode)
            assertEquals("42", quote.tableTent)
            assertEquals(1, quote.lines.size)

            val line = quote.lines[0]
            assertEquals("7.00", line.unitPrice)
            assertEquals("14.00", line.subtotal)
            assertEquals("0.98", line.tax)
            assertEquals("14.98", line.total)
            assertEquals("Bien cocida", line.note)
            assertEquals(2, line.modifiers.size)

            // Verificar persistencia en base de datos
            transaction(database) {
                val dbOrder = KioskOrderTable.selectAll().where { KioskOrderTable.id eq idempotencyKey }.single()
                assertEquals("K1-001", dbOrder[KioskOrderTable.codigoPedido])
                assertEquals(1, dbOrder[KioskOrderTable.numeroPedidoDiario])
                assertEquals("COTIZADO", dbOrder[KioskOrderTable.estado])

                val dbItems = KioskOrderItemTable.selectAll().where { KioskOrderItemTable.idPedido eq idempotencyKey }.toList()
                assertEquals(1, dbItems.size)

                val dbMods =
                    KioskOrderItemModifierTable
                        .selectAll()
                        .where { KioskOrderItemModifierTable.idPedido eq idempotencyKey }
                        .toList()
                assertEquals(2, dbMods.size)
            }

            // Probar idempotencia: llamada repetida con el mismo Idempotency-Key devuelve lo mismo sin duplicar
            val repeatResponse =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", idempotencyKey)
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }
            assertEquals(HttpStatusCode.OK, repeatResponse.status)
            val repeatQuote = json.decodeFromString<KioskQuoteResponse>(repeatResponse.bodyAsText())
            assertEquals("K1-001", repeatQuote.formattedOrderNumber)
            assertEquals(quote.total, repeatQuote.total)

            // Probar segundo pedido con NUEVO Idempotency-Key -> debe ser K1-002
            val secondKey = UUID.randomUUID().toString()
            val secondResponse =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", secondKey)
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }
            assertEquals(HttpStatusCode.OK, secondResponse.status)
            val secondQuote = json.decodeFromString<KioskQuoteResponse>(secondResponse.bodyAsText())
            assertEquals("K1-002", secondQuote.formattedOrderNumber)
        }

    @Test
    fun `POST quote rejects when mandatory modifier is omitted`() =
        testApplication {
            installKtorSecurity()
            routing {
                kioskRoutes(kioskService)
            }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val token = generateValidKioskJwt()
            // No incluye bebida (Grupo 1 es obligatorio / min 1)
            val quoteRequest =
                KioskQuoteRequest(
                    diningMode = "COMER_AQUI",
                    lines =
                        listOf(
                            KioskQuoteLineRequest(
                                itemId = 101,
                                qty = 1,
                                modifiers = emptyList(),
                            ),
                        ),
                )

            val response =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Bebida del Combo"))
        }

    @Test
    fun `POST quote rejects when modifier exceeds maximum selection`() =
        testApplication {
            installKtorSecurity()
            routing {
                kioskRoutes(kioskService)
            }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val token = generateValidKioskJwt()
            // Selecciona 2 bebidas cuando max es 1
            val quoteRequest =
                KioskQuoteRequest(
                    diningMode = "COMER_AQUI",
                    lines =
                        listOf(
                            KioskQuoteLineRequest(
                                itemId = 101,
                                qty = 1,
                                modifiers = listOf(1, 2), // Bebidas 1 y 2
                            ),
                        ),
                )

            val response =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("máximo"))
        }

    @Test
    fun `POST quote rejects when item is sold out`() =
        testApplication {
            installKtorSecurity()
            routing {
                kioskRoutes(kioskService)
            }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val token = generateValidKioskJwt()
            // Item 102 tiene stock 0
            val quoteRequest =
                KioskQuoteRequest(
                    diningMode = "PARA_LLEVAR",
                    lines =
                        listOf(
                            KioskQuoteLineRequest(
                                itemId = 102,
                                qty = 1,
                            ),
                        ),
                )

            val response =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("agotado"))
        }

    @Test
    fun `POST quote rejects when Idempotency-Key header is missing`() =
        testApplication {
            installKtorSecurity()
            routing {
                kioskRoutes(kioskService)
            }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val token = generateValidKioskJwt()
            val quoteRequest =
                KioskQuoteRequest(
                    diningMode = "COMER_AQUI",
                    lines =
                        listOf(
                            KioskQuoteLineRequest(
                                itemId = 101,
                                qty = 1,
                                modifiers = listOf(1),
                            ),
                        ),
                )

            val response =
                client.post("/api/v1/kiosk/orders/quote") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    // Sin Idempotency-Key
                    contentType(ContentType.Application.Json)
                    setBody(quoteRequest)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Idempotency-Key"))
        }
}
