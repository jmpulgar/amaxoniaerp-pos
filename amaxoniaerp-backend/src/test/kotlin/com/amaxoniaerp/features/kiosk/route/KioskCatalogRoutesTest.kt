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
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KioskCatalogRoutesTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    private val jwtConfig = JwtConfig(
        secret = "test-secret-kiosk-test-must-be-very-long-32-chars",
        domain = "http://localhost:8080",
        audience = "http://localhost:8080/kiosk",
        realm = "Amaxonia Kiosk Test",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val kioskDeviceRepository = KioskDeviceRepository()
    private val kioskCatalogRepository = KioskCatalogRepository()
    private val unlockRateLimiter = UnlockRateLimiter()
    private lateinit var kioskService: KioskService

    private val testCountry = "PA"
    private val testCompanyDb = "momi_pa"
    private val testDeviceId = UUID.randomUUID().toString()

    @BeforeTest
    fun setUp() {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:kiosk_cat_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 2
                isAutoCommit = false
            },
        )
        database = Database.connect(dataSource)

        kioskService = KioskService(
            kioskDeviceRepository = kioskDeviceRepository,
            unlockRateLimiter = unlockRateLimiter,
            jwtConfig = jwtConfig,
            databaseResolver = { _, _ -> database },
            kioskCatalogRepository = kioskCatalogRepository,
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

            // Departamentos:
            // 1: Visible en POS (visible_pos = 1, visible = true) -> Hamburguesas
            // 2: Visible en POS (visible_pos = 1, visible = true) -> Bebidas
            // 3: Oculto en POS (visible_pos = 0) -> Insumos Cocina
            // 4: Inactivo (visible = false) -> Descontinuados
            DepartamentoTable.insert {
                it[id] = 1
                it[codigo] = "HAM"
                it[descripcion] = "Hamburguesas"
                it[visible] = true
                it[visiblePos] = 1
            }
            DepartamentoTable.insert {
                it[id] = 2
                it[codigo] = "BEB"
                it[descripcion] = "Bebidas"
                it[visible] = true
                it[visiblePos] = 1
            }
            DepartamentoTable.insert {
                it[id] = 3
                it[codigo] = "INS"
                it[descripcion] = "Insumos Cocina"
                it[visible] = true
                it[visiblePos] = 0
            }
            DepartamentoTable.insert {
                it[id] = 4
                it[codigo] = "DESC"
                it[descripcion] = "Descontinuados"
                it[visible] = false
                it[visiblePos] = 1
            }

            // Items en Dept 1 (Hamburguesas)
            ItemsTablePA.insert {
                it[idItem] = 101
                it[codItem] = "HAM-01"
                it[descripcion1] = "Hamburguesa Clásica"
                it[descripcion2] = "Carne 100% res, queso cheddar, lechuga y tomate"
                it[codDepartamento] = 1
                it[departamentoId] = 1
                it[precio1] = BigDecimal("5.50")
                it[iva] = BigDecimal("7.00")
                it[foto] = "fotos/burger.jpg"
                it[existenciaTotal] = 20
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }
            ItemsTablePA.insert {
                it[idItem] = 102
                it[codItem] = "HAM-02"
                it[descripcion1] = "Hamburguesa Doble"
                it[descripcion2] = "Doble carne y queso"
                it[codDepartamento] = 1
                it[departamentoId] = 1
                it[precio1] = BigDecimal("7.50")
                it[iva] = BigDecimal("7.00")
                it[existenciaTotal] = 0 // AGOTADO
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }

            // Items en Dept 2 (Bebidas)
            ItemsTablePA.insert {
                it[idItem] = 201
                it[codItem] = "BEB-01"
                it[descripcion1] = "Coca Cola Original"
                it[codDepartamento] = 2
                it[departamentoId] = 2
                it[precio1] = BigDecimal("1.75")
                it[iva] = BigDecimal("7.00")
                it[existenciaTotal] = 50
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }

            // Item en Dept 3 (Insumos Cocina - visible_pos = 0, no debe aparecer)
            ItemsTablePA.insert {
                it[idItem] = 301
                it[codItem] = "INS-01"
                it[descripcion1] = "Pan Brioche x 50"
                it[codDepartamento] = 3
                it[departamentoId] = 3
                it[precio1] = BigDecimal("12.00")
                it[existenciaTotal] = 10
                it[estatus] = "A"
                it[visiblePos] = 'T'
            }

            // Modificadores para Hamburguesa Clásica (101)
            // Grupo 1: Bebida del Combo (Combo / Obligatorio)
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
            // Grupo 2: Extras (Opcional)
            ItemModifierGroupTable.insert {
                it[id] = 2
                it[nombre] = "Extras"
                it[minSeleccion] = 0
                it[maxSeleccion] = 3
                it[esObligatorio] = false
                it[esCombo] = false
                it[orden] = 2
                it[activo] = true
            }

            // Relaciones Item 101 -> Grupos 1 y 2
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
                it[idItemAsociado] = 201 // Asociado a Coca Cola (stock > 0)
                it[nombre] = "Coca Cola Sin Azúcar"
                it[precioAdicional] = BigDecimal("0.0000")
                it[orden] = 1
                it[activo] = true
            }
            ItemModifierTable.insert {
                it[id] = 2
                it[idGrupo] = 1
                it[idItemAsociado] = null
                it[nombre] = "Sprite"
                it[precioAdicional] = BigDecimal("0.0000")
                it[orden] = 2
                it[activo] = true
            }

            // Opciones Grupo 2
            ItemModifierTable.insert {
                it[id] = 3
                it[idGrupo] = 2
                it[idItemAsociado] = null
                it[nombre] = "Tocineta Extra"
                it[precioAdicional] = BigDecimal("1.5000")
                it[orden] = 1
                it[activo] = true
            }
            ItemModifierTable.insert {
                it[id] = 4
                it[idGrupo] = 2
                it[idItemAsociado] = null
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
        JWT.create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "kiosk")
            .withClaim("role", "KIOSK")
            .withClaim("device_id", testDeviceId)
            .withClaim("country_code", testCountry)
            .withClaim("company_db", testCompanyDb)
            .withClaim("admin_db", testCompanyDb)
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    private fun ApplicationTestBuilder.installKtorSecurity() {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(json)
        }
        install(io.ktor.server.auth.Authentication) {
            jwt {
                realm = jwtConfig.realm ?: "test"
                verifier(
                    JWT.require(Algorithm.HMAC256(jwtConfig.secret))
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
    fun `GET catalog returns only visible_pos = 1 departments, Price Level A and modifiers`() = testApplication {
        installKtorSecurity()
        routing {
            kioskRoutes(kioskService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val token = generateValidKioskJwt()
        val response = client.get("/api/v1/kiosk/catalog") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val etag = response.headers[HttpHeaders.ETag]
        assertNotNull(etag)

        val catalog = json.decodeFromString<KioskCatalogResponse>(response.bodyAsText())

        // 1. Verificar Categorías: solo id 1 y 2 (no 3 visible_pos=0, no 4 visible=false)
        assertEquals(2, catalog.categories.size)
        val catNames = catalog.categories.map { it.name }
        assertTrue(catNames.contains("Hamburguesas"))
        assertTrue(catNames.contains("Bebidas"))
        assertFalse(catNames.contains("Insumos Cocina"))
        assertFalse(catNames.contains("Descontinuados"))

        // 2. Verificar Items: solo de categorías 1 y 2
        assertEquals(3, catalog.items.size)
        val item101 = catalog.items.first { it.id == 101 }
        assertEquals("Hamburguesa Clásica", item101.name)
        assertEquals("5.50", item101.price) // Precio 1 (Nivel A)
        assertEquals("7.00", item101.taxRate)
        assertEquals("/api/data/PA/momi_pa/item/burger.jpg", item101.imageUrl)
        assertFalse(item101.soldOut) // Existencia 20 > 0

        // Item 102 agotado
        val item102 = catalog.items.first { it.id == 102 }
        assertTrue(item102.soldOut) // Existencia 0

        // Insumos cocina (item 301) no debe estar
        assertTrue(catalog.items.none { it.id == 301 })

        // 3. Modificadores de Hamburguesa Clásica (101)
        assertEquals(2, item101.modifierGroups.size)
        val comboGroup = item101.modifierGroups.first { it.id == 1 }
        assertEquals("Bebida del Combo", comboGroup.name)
        assertTrue(comboGroup.isMandatory)
        assertTrue(comboGroup.isCombo)
        assertEquals(1, comboGroup.min)
        assertEquals(1, comboGroup.max)
        assertEquals(2, comboGroup.options.size)
        assertEquals("Coca Cola Sin Azúcar", comboGroup.options[0].name)
        assertEquals("0.00", comboGroup.options[0].extraPrice)
        assertFalse(comboGroup.options[0].soldOut)

        val extrasGroup = item101.modifierGroups.first { it.id == 2 }
        assertEquals("Extras", extrasGroup.name)
        assertFalse(extrasGroup.isMandatory)
        assertFalse(extrasGroup.isCombo)
        assertEquals(0, extrasGroup.min)
        assertEquals(3, extrasGroup.max)
        assertEquals(2, extrasGroup.options.size)
        assertEquals("Tocineta Extra", extrasGroup.options[0].name)
        assertEquals("1.50", extrasGroup.options[0].extraPrice)
    }

    @Test
    fun `GET catalog returns 304 Not Modified when If-None-Match matches ETag`() = testApplication {
        installKtorSecurity()
        routing {
            kioskRoutes(kioskService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val token = generateValidKioskJwt()
        val firstResponse = client.get("/api/v1/kiosk/catalog") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, firstResponse.status)
        val etag = firstResponse.headers[HttpHeaders.ETag]
        assertNotNull(etag)

        val secondResponse = client.get("/api/v1/kiosk/catalog") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, secondResponse.status)
        assertEquals("", secondResponse.bodyAsText())
    }

    @Test
    fun `verify catalog-response fixture deserializes cleanly`() {
        val fixtureFile = File("../contracts/kiosk/catalog-response.json")
        val altFixtureFile = File("contracts/kiosk/catalog-response.json")
        val content = if (fixtureFile.exists()) {
            fixtureFile.readText()
        } else if (altFixtureFile.exists()) {
            altFixtureFile.readText()
        } else {
            File("D:/PROGRAMMING/Kotlin/Amaxonia/contracts/kiosk/catalog-response.json").readText()
        }

        val response = json.decodeFromString<KioskCatalogResponse>(content)
        assertEquals(2, response.categories.size)
        assertEquals(2, response.items.size)
        assertEquals("Hamburguesa Clásica", response.items[0].name)
        assertEquals(2, response.items[0].modifierGroups.size)
    }
}
