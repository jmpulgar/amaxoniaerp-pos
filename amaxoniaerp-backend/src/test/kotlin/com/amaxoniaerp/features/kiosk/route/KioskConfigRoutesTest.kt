package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.KioskConfigRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceTable
import com.amaxoniaerp.features.kiosk.data.KioskMediaTable
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KioskConfigRoutesTest {

    private lateinit var dataSourcePA: HikariDataSource
    private lateinit var databasePA: Database
    private lateinit var dataSourceVE: HikariDataSource
    private lateinit var databaseVE: Database

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
    private val kioskConfigRepository = KioskConfigRepository()
    private val unlockRateLimiter = UnlockRateLimiter()
    private lateinit var kioskService: KioskService

    private val testDeviceIdPA = UUID.randomUUID().toString()
    private val testDeviceIdVE = UUID.randomUUID().toString()

    @BeforeTest
    fun setUp() {
        dataSourcePA = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:kiosk_cfg_pa_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 2
                isAutoCommit = false
            },
        )
        databasePA = Database.connect(dataSourcePA)

        dataSourceVE = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:kiosk_cfg_ve_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 2
                isAutoCommit = false
            },
        )
        databaseVE = Database.connect(dataSourceVE)

        kioskService = KioskService(
            kioskDeviceRepository = kioskDeviceRepository,
            unlockRateLimiter = unlockRateLimiter,
            jwtConfig = jwtConfig,
            databaseResolver = { countryCode, _ ->
                if (countryCode.equals("VE", ignoreCase = true)) databaseVE else databasePA
            },
            kioskConfigRepository = kioskConfigRepository,
        )

        // Setup PA schema
        transaction(databasePA) {
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
                    kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'RETIRO_MOSTRADOR',
                    kiosco_impresora_cocina_ip VARCHAR(45) NULL,
                    kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI,PARA_LLEVAR',
                    kiosco_color_marca CHAR(7) NULL DEFAULT '#E65100',
                    kiosco_config_version INT NOT NULL DEFAULT 2,
                    clave_kiosko VARCHAR(255) NULL
                );
                """.trimIndent(),
            )
            exec("INSERT INTO parametros_generales (cod_empresa, kiosco_color_marca, kiosco_config_version) VALUES (1, '#E65100', 2);")

            SchemaUtils.create(KioskDeviceTable, KioskMediaTable)
            KioskDeviceTable.insert {
                it[id] = testDeviceIdPA
                it[nombre] = "Kiosco Central PA"
                it[prefijoPedido] = "K1"
                it[idCaja] = "CAJA-01"
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 10
                it[idClienteGenerico] = "CF"
                it[activo] = true
                it[creadoEn] = LocalDateTime.now()
            }

            KioskMediaTable.insert {
                it[tipo] = "IMAGE"
                it[archivo] = "promo1.jpg"
                it[orden] = 1
                it[duracionSeg] = 5
                it[activo] = true
                it[updatedAt] = LocalDateTime.now()
            }
            KioskMediaTable.insert {
                it[tipo] = "VIDEO"
                it[archivo] = "video1.mp4"
                it[orden] = 2
                it[duracionSeg] = 15
                it[activo] = true
                it[updatedAt] = LocalDateTime.now()
            }
        }

        // Setup VE schema
        transaction(databaseVE) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS parametros_generales (
                    cod_empresa INT PRIMARY KEY,
                    default_cod_cliente_factura VARCHAR(80) NOT NULL DEFAULT 'CF',
                    default_id_formapago_factura INT NOT NULL DEFAULT 1,
                    porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL DEFAULT 16.00,
                    validar_stock VARCHAR(2) NOT NULL DEFAULT 'SI',
                    dias_vencimiento INT NOT NULL DEFAULT 30,
                    cod_almacen INT NOT NULL DEFAULT 1,
                    rif VARCHAR(50) NULL,
                    moneda VARCHAR(50) NOT NULL DEFAULT 'USD',
                    moneda_base INT NOT NULL DEFAULT 1,
                    multi_moneda VARCHAR(2) NOT NULL DEFAULT 'SI',
                    moneda_secundaria INT NOT NULL DEFAULT 2,
                    moneda_secundaria_abr VARCHAR(50) NOT NULL DEFAULT 'VES',
                    igtf DECIMAL(10,6) NULL,
                    impresion_directa VARCHAR(2) NOT NULL DEFAULT 'No',
                    kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'IMPRESORA_COCINA',
                    kiosco_impresora_cocina_ip VARCHAR(45) NULL,
                    kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI',
                    kiosco_color_marca CHAR(7) NULL DEFAULT '#1976D2',
                    kiosco_config_version INT NOT NULL DEFAULT 1,
                    clave_kiosko VARCHAR(255) NULL
                );
                """.trimIndent(),
            )
            exec("INSERT INTO parametros_generales (cod_empresa) VALUES (1);")

            exec(
                """
                CREATE TABLE IF NOT EXISTS tasas_cambio (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    divisa INT NOT NULL,
                    monedabase INT NOT NULL,
                    tasa_inversa DECIMAL(20,8) NOT NULL,
                    facturado VARCHAR(1) NOT NULL DEFAULT '0'
                );
                """.trimIndent(),
            )
            exec("INSERT INTO tasas_cambio (divisa, monedabase, tasa_inversa, facturado) VALUES (2, 1, 40.50000000, '0');")

            SchemaUtils.create(KioskDeviceTable, KioskMediaTable)
            KioskDeviceTable.insert {
                it[id] = testDeviceIdVE
                it[nombre] = "Kiosco Caracas VE"
                it[prefijoPedido] = "K2"
                it[idCaja] = "CAJA-02"
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 20
                it[idClienteGenerico] = "CF"
                it[activo] = true
                it[creadoEn] = LocalDateTime.now()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        dataSourcePA.close()
        dataSourceVE.close()
    }

    private fun generateValidKioskJwt(countryCode: String, companyDb: String, deviceId: String): String =
        JWT.create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "kiosk")
            .withClaim("role", "KIOSK")
            .withClaim("device_id", deviceId)
            .withClaim("country_code", countryCode)
            .withClaim("company_db", companyDb)
            .withClaim("admin_db", companyDb)
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
    fun `GET config returns 200 with ETag and full configuration for PA`() = testApplication {
        installKtorSecurity()
        routing {
            kioskRoutes(kioskService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val token = generateValidKioskJwt("PA", "momi_pa", testDeviceIdPA)
        val response = client.get("/api/v1/kiosk/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val etag = response.headers[HttpHeaders.ETag]
        assertNotNull(etag)
        assertTrue(etag.isNotBlank())

        val config = json.decodeFromString<KioskConfigResponse>(response.bodyAsText())
        assertEquals(2, config.version)
        assertEquals("#E65100", config.brandColor)
        assertEquals("RETIRO_MOSTRADOR", config.dispatch)
        assertEquals(listOf("COMER_AQUI", "PARA_LLEVAR"), config.diningModes)
        assertEquals(2, config.media.size)
        assertEquals("IMAGE", config.media[0].type)
        assertEquals("/api/data/PA/momi_pa/banners/promo1.jpg", config.media[0].url)
        assertEquals(5, config.media[0].durationSec)
        assertEquals("USD", config.currency.base)
        assertEquals(null, config.currency.secondary)
        assertEquals("1.0000", config.currency.rate)
        assertEquals("PA", config.country)
    }

    @Test
    fun `GET config returns 304 Not Modified when If-None-Match matches ETag`() = testApplication {
        installKtorSecurity()
        routing {
            kioskRoutes(kioskService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val token = generateValidKioskJwt("PA", "momi_pa", testDeviceIdPA)
        val firstResponse = client.get("/api/v1/kiosk/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, firstResponse.status)
        val etag = firstResponse.headers[HttpHeaders.ETag]
        assertNotNull(etag)

        val secondResponse = client.get("/api/v1/kiosk/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, secondResponse.status)
        assertEquals("", secondResponse.bodyAsText())
        assertEquals(etag, secondResponse.headers[HttpHeaders.ETag])
    }

    @Test
    fun `GET config returns multicurrency and exchange rate for VE`() = testApplication {
        installKtorSecurity()
        routing {
            kioskRoutes(kioskService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val token = generateValidKioskJwt("VE", "momi_ve", testDeviceIdVE)
        val response = client.get("/api/v1/kiosk/config") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val config = json.decodeFromString<KioskConfigResponse>(response.bodyAsText())
        assertEquals("USD", config.currency.base)
        assertEquals("VES", config.currency.secondary)
        assertEquals("40.5", config.currency.rate)
        assertEquals("VE", config.country)
        assertEquals("IMPRESORA_COCINA", config.dispatch)
    }
}
