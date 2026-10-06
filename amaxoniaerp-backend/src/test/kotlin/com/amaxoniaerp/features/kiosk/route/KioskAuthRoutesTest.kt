package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.caja.data.CajaTablePA
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.kiosk.application.KioskCajaSelection
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskClient
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Autenticación del kiosco con el login del POS (token de empresa) + headers de caja/prefijo,
 * resolución del contexto desde la caja y desbloqueo con la clave del kiosco.
 */
class KioskAuthRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var kioskService: KioskService
    private val unlockRateLimiter = UnlockRateLimiter()
    private val rawAdminPassword = "AdminKiosko2026!"

    @BeforeTest
    fun setUp() {
        dataSource = KioskTestSupport.newDataSource("kiosk_auth")
        database = Database.connect(dataSource)
        kioskService = KioskService(unlockRateLimiter = unlockRateLimiter, databaseResolver = { _, _ -> database })

        KioskTestSupport.createParametrosGenerales(database)
        KioskTestSupport.createCajaSchema(database, cajaDescripcion = "Kiosco Albrook")
        transaction(database) {
            exec("UPDATE parametros_generales SET clave_kiosko = '${BCrypt.hashpw(rawAdminPassword, BCrypt.gensalt())}'")
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private suspend fun HttpClient.unlock(
        password: String,
        token: String = KioskTestSupport.companyToken(),
    ): HttpResponse =
        post("/api/v1/kiosk/unlock") {
            kioskHeaders(token = token)
            contentType(ContentType.Application.Json)
            setBody(KioskUnlockRequest(password))
        }

    @Test
    fun `kiosk endpoints require the POS company token`() =
        testApplication {
            val client = kioskClient(kioskService)

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/kiosk/config").status)

            val identityToken = KioskTestSupport.companyToken(tokenType = "identity")
            val forbidden = client.get("/api/v1/kiosk/config") { kioskHeaders(token = identityToken) }
            assertEquals(HttpStatusCode.Forbidden, forbidden.status)
            assertTrue(forbidden.bodyAsText().contains("Se requiere token de empresa"))

            val ok = client.get("/api/v1/kiosk/config") { kioskHeaders() }
            assertEquals(HttpStatusCode.OK, ok.status)
        }

    @Test
    fun `pairing endpoint no longer exists`() =
        testApplication {
            val client = kioskClient(kioskService)
            val response = client.post("/api/v1/kiosk/pairing") { kioskHeaders() }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `missing or invalid kiosk headers answer 400 with a clear message`() =
        testApplication {
            val client = kioskClient(kioskService)

            val noCaja = client.get("/api/v1/kiosk/config") { kioskHeaders(caja = null) }
            assertEquals(HttpStatusCode.BadRequest, noCaja.status)
            assertTrue(noCaja.bodyAsText().contains("Falta el header X-Kiosk-Caja"))

            val noPrefix = client.get("/api/v1/kiosk/config") { kioskHeaders(prefix = null) }
            assertEquals(HttpStatusCode.BadRequest, noPrefix.status)
            assertTrue(noPrefix.bodyAsText().contains("Falta el header X-Kiosk-Prefix"))

            for (invalid in listOf("K-1", "TOOLONG", "K 1")) {
                val response = client.get("/api/v1/kiosk/config") { kioskHeaders(prefix = invalid) }
                assertEquals(HttpStatusCode.BadRequest, response.status, "prefijo $invalid")
                assertTrue(response.bodyAsText().contains("X-Kiosk-Prefix inválido"))
            }
        }

    @Test
    fun `unknown or inactive caja is rejected`() =
        testApplication {
            val client = kioskClient(kioskService)

            val unknown = client.get("/api/v1/kiosk/config") { kioskHeaders(caja = "NO-EXISTE") }
            assertEquals(HttpStatusCode.BadRequest, unknown.status)
            assertTrue(unknown.bodyAsText().contains("Caja del kiosco no válida"))

            transaction(database) {
                CajaTablePA.update({ CajaTablePA.idCaja eq KioskTestSupport.CAJA_ID }) { it[codEstatus] = 0 }
            }
            val inactive = client.get("/api/v1/kiosk/config") { kioskHeaders() }
            assertEquals(HttpStatusCode.BadRequest, inactive.status)
            assertTrue(inactive.bodyAsText().contains("Caja del kiosco no válida"))
        }

    @Test
    fun `context is resolved from the caja, the logged user and parametros_generales`() =
        runBlocking {
            transaction(database) {
                exec("UPDATE parametros_generales SET default_cod_cliente_factura = '0001'")
                ClientsTable.insert {
                    it[idCliente] = "UUID-CLIENTE-GENERICO"
                    it[codCliente] = "1"
                    it[rif] = "CF"
                    it[dv] = "0"
                    it[nombre] = "CLIENTE CONTADO"
                    it[direccion] = "Panamá"
                }
            }

            val context =
                kioskService.resolveContext(
                    KioskCajaSelection(
                        countryCode = "PA",
                        companyDb = KioskTestSupport.COMPANY_DB,
                        userId = KioskTestSupport.USER_ID,
                        idCaja = KioskTestSupport.CAJA_ID,
                        prefix = "K2",
                    ),
                )

            assertNotNull(context)
            assertEquals(KioskTestSupport.CAJA_ID, context.idCaja)
            assertEquals(KioskTestSupport.CAJA_ID, context.deviceId)
            assertEquals("Kiosco Albrook", context.deviceName)
            assertEquals("K2", context.prefix)
            assertEquals(1, context.idSucursal)
            // PA no tiene almacén en la caja: almacén por defecto de ventas de la sucursal.
            assertEquals(3, context.idAlmacen)
            assertEquals(KioskTestSupport.SELLER_ID, context.codVendedor)
            // default_cod_cliente_factura '0001' → cod_cliente '1' → id_cliente.
            assertEquals("UUID-CLIENTE-GENERICO", context.idClienteGenerico)
            assertEquals(KioskTestSupport.USER_ID, context.userId)
        }

    @Test
    fun `context falls back to the configured code when the generic client does not exist`() =
        runBlocking {
            transaction(database) { exec("UPDATE parametros_generales SET default_cod_cliente_factura = 'NOEXISTE'") }
            val context =
                kioskService.resolveContext(
                    KioskCajaSelection("PA", KioskTestSupport.COMPANY_DB, null, KioskTestSupport.CAJA_ID, "K1"),
                )
            assertNotNull(context)
            assertEquals("NOEXISTE", context.idClienteGenerico)
            assertNull(context.userId)
            assertNull(
                kioskService.resolveContext(KioskCajaSelection("PA", KioskTestSupport.COMPANY_DB, null, "OTRA", "K1")),
            )
        }

    @Test
    fun `unlock endpoint validates bcrypt password and enforces rate limiting per caja and user`() =
        testApplication {
            val client = kioskClient(kioskService)

            for (i in 1..5) {
                assertEquals(HttpStatusCode.Unauthorized, client.unlock("wrongPassword$i").status)
            }

            val blocked = client.unlock(rawAdminPassword)
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            assertTrue(blocked.bodyAsText().contains("Demasiados intentos fallidos"))

            // Otro usuario en la misma caja tiene su propio contador.
            val otherUser = client.unlock(rawAdminPassword, token = KioskTestSupport.companyToken(userId = 99))
            assertEquals(HttpStatusCode.NoContent, otherUser.status)

            unlockRateLimiter.recordSuccess("${KioskTestSupport.COMPANY_DB}|${KioskTestSupport.CAJA_ID}|${KioskTestSupport.USER_ID}")
            assertEquals(HttpStatusCode.NoContent, client.unlock(rawAdminPassword).status)
        }

    @Test
    fun `unlock without clave_kiosko column answers 400`() =
        testApplication {
            transaction(database) { exec("ALTER TABLE parametros_generales DROP COLUMN clave_kiosko") }
            val service = KioskService(unlockRateLimiter = UnlockRateLimiter(), databaseResolver = { _, _ -> database })
            val client = kioskClient(service)

            val response = client.unlock(rawAdminPassword)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("No hay clave de kiosco configurada"))
        }
}
