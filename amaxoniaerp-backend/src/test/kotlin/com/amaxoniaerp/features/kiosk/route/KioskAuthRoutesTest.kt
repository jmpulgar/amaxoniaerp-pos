package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceTable
import com.amaxoniaerp.features.kiosk.domain.KioskPairingRequest
import com.amaxoniaerp.features.kiosk.domain.KioskPairingResponse
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KioskAuthRoutesTest {
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
    private val unlockRateLimiter = UnlockRateLimiter(maxAttempts = 5, windowSeconds = 60)
    private lateinit var kioskService: KioskService

    private val testCountry = "PA"
    private val testCompanyDb = "momi_test"
    private val testDeviceId = UUID.randomUUID().toString()
    private val rawPairingCode = "12345678"
    private val rawAdminPassword = "secretKioskPassword"

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_auth_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
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
            )

        transaction(database) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS parametros_generales (
                    cod_empresa INT PRIMARY KEY,
                    default_cod_cliente_factura VARCHAR(80) NOT NULL,
                    clave_kiosko VARCHAR(255)
                )
                """.trimIndent(),
            )
            SchemaUtils.create(KioskDeviceTable)

            // Seed parametros_generales
            val hashedAdminPw = BCrypt.hashpw(rawAdminPassword, BCrypt.gensalt(10))
            exec(
                "INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura, clave_kiosko) " +
                    "VALUES (1, 'CF', '$hashedAdminPw')",
            )

            // Seed device ready for pairing
            val shaPairingCode =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(rawPairingCode.toByteArray())
                    .joinToString("") { "%02x".format(it) }

            KioskDeviceTable.insert {
                it[id] = testDeviceId
                it[nombre] = "Kiosco Entrada 1"
                it[prefijoPedido] = "K1"
                it[idCaja] = "caja-kiosk-1"
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 101
                it[idClienteGenerico] = "CLI-GEN"
                it[codigoEmparejamientoHash] = shaPairingCode
                it[codigoExpiraEn] = LocalDateTime.now().plusHours(2)
                it[activo] = true
                it[creadoEn] = LocalDateTime.now()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun issueToken(
        tokenType: String,
        role: String,
        deviceId: String = testDeviceId,
    ): String =
        JWT
            .create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", tokenType)
            .withClaim("role", role)
            .withClaim("device_id", deviceId)
            .withClaim("device_name", "Kiosco Entrada 1")
            .withClaim("country_code", testCountry)
            .withClaim("admin_db", testCompanyDb)
            .withClaim("company_db", testCompanyDb)
            .withClaim("prefix", "K1")
            .withClaim("box_id", "caja-kiosk-1")
            .withClaim("branch_id", 1)
            .withClaim("warehouse_id", 1)
            .withClaim("seller_code", 101)
            .withClaim("customer_id", "CLI-GEN")
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    @Test
    fun `pairing fails with invalid or expired code and succeeds with valid code`() =
        testApplication {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
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

            // 1. Wrong code -> 401
            val wrongResponse =
                client.post("/api/v1/kiosk/pairing") {
                    contentType(ContentType.Application.Json)
                    setBody(KioskPairingRequest(testCountry, testCompanyDb, "99999999"))
                }
            assertEquals(HttpStatusCode.Unauthorized, wrongResponse.status)

            // 2. Correct code -> 200
            val okResponse =
                client.post("/api/v1/kiosk/pairing") {
                    contentType(ContentType.Application.Json)
                    setBody(KioskPairingRequest(testCountry, testCompanyDb, rawPairingCode))
                }
            assertEquals(HttpStatusCode.OK, okResponse.status)
            val pairingResult = json.decodeFromString<KioskPairingResponse>(okResponse.bodyAsText())
            assertEquals(testDeviceId, pairingResult.deviceId)
            assertEquals("K1", pairingResult.prefix)
            assertNotNull(pairingResult.deviceToken)

            // 3. Pairing again with same code fails (single-use)
            val reusedResponse =
                client.post("/api/v1/kiosk/pairing") {
                    contentType(ContentType.Application.Json)
                    setBody(KioskPairingRequest(testCountry, testCompanyDb, rawPairingCode))
                }
            assertEquals(HttpStatusCode.Unauthorized, reusedResponse.status)

            // 4. Verify DB updated
            transaction(database) {
                val dev = KioskDeviceTable.selectAll().where { KioskDeviceTable.id eq testDeviceId }.single()
                assertNull(dev[KioskDeviceTable.codigoEmparejamientoHash])
                assertNull(dev[KioskDeviceTable.codigoExpiraEn])
                assertNotNull(dev[KioskDeviceTable.tokenHash])
            }
        }

    @Test
    fun `token isolation - user company token is rejected on kiosk unlock`() =
        testApplication {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
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

            val userCompanyToken = issueToken(tokenType = "company", role = "user")
            val response =
                client.post("/api/v1/kiosk/unlock") {
                    header(HttpHeaders.Authorization, "Bearer $userCompanyToken")
                    contentType(ContentType.Application.Json)
                    setBody(KioskUnlockRequest(rawAdminPassword))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("Se requiere rol KIOSK"))
        }

    @Test
    fun `unlock endpoint validates bcrypt password and enforces rate limiting`() =
        testApplication {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
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

            val kioskToken = issueToken(tokenType = "kiosk", role = "KIOSK")

            // 1. Wrong password 5 times
            for (i in 1..5) {
                val failedResp =
                    client.post("/api/v1/kiosk/unlock") {
                        header(HttpHeaders.Authorization, "Bearer $kioskToken")
                        contentType(ContentType.Application.Json)
                        setBody(KioskUnlockRequest("wrongPassword$i"))
                    }
                assertEquals(HttpStatusCode.Unauthorized, failedResp.status)
            }

            // 2. 6th attempt should be blocked by rate limiter with 429 Too Many Requests
            val blockedResp =
                client.post("/api/v1/kiosk/unlock") {
                    header(HttpHeaders.Authorization, "Bearer $kioskToken")
                    contentType(ContentType.Application.Json)
                    setBody(KioskUnlockRequest(rawAdminPassword))
                }
            assertEquals(HttpStatusCode.TooManyRequests, blockedResp.status)
            assertTrue(blockedResp.bodyAsText().contains("Demasiados intentos fallidos"))

            // 3. Clear rate limiter to test happy path
            unlockRateLimiter.recordSuccess(testDeviceId)

            val successResp =
                client.post("/api/v1/kiosk/unlock") {
                    header(HttpHeaders.Authorization, "Bearer $kioskToken")
                    contentType(ContentType.Application.Json)
                    setBody(KioskUnlockRequest(rawAdminPassword))
                }
            assertEquals(HttpStatusCode.NoContent, successResp.status)
        }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installKtorSecurity() {
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
}
