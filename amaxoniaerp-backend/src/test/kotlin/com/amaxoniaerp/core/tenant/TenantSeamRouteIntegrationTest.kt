package com.amaxoniaerp.core.tenant

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.items.data.AlmacenTable
import com.amaxoniaerp.loadJwtConfig
import com.amaxoniaerp.module
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de integración del seam canónico de tenant ([resolveCompanyRequestContext]
 * + [requireCompanyDbHeader]) ejercitado a través de una ruta real
 * (`GET /api/cajas`) sobre el módulo completo ([com.amaxoniaerp.module]).
 *
 * Matriz cubierta:
 *   - 401 sin JWT / 403 con token que no es de empresa.
 *   - 400 cuando falta country_code o admin_db en el JWT firmado.
 *   - 400 sin header Company-DB / 403 si no coincide con admin_db.
 *   - 200 feliz contra H2 sembrado: el [DatabaseManager] recibe por reflejo un
 *     DataSource H2 para la llave `<país>:<companyDb>`, de modo que
 *     `connectToCompanyDb` resuelve esa base sin tocar MySQL. La respuesta se
 *     valida también como contrato de serialización (nombres de campo del DTO).
 *
 * El reflejo es exclusivamente de prueba: NO modifica código de producción ni
 * abre conexiones reales.
 */
class TenantSeamRouteIntegrationTest {
    private lateinit var jwt: JwtConfig
    private var dataSource: HikariDataSource? = null

    private fun companyDbName(): String = "seam_${System.nanoTime()}"

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                jwt = loadJwtConfig()
            }
            // Arranque perezoso: garantiza DatabaseManager.init + config JWT.
            client.get("/health")
            block()
        }

    /** Siembra un DataSource H2 en la caché privada de [DatabaseManager]. */
    private fun seedH2CompanyDb(companyDb: String): Database {
        val ds =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:${companyDb}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        dataSource = ds
        val field = DatabaseManager::class.java.getDeclaredField("companyDataSources")
        field.isAccessible = true
        val cache = field.get(DatabaseManager) as MutableMap<Any?, Any?>
        // Producción lee/escribe esta caché dentro de synchronized(map); usar el
        // MISMO monitor establece el happens-before que el worker del servidor
        // necesita para ver la entrada (JMM).
        synchronized(cache) {
            cache["PA:$companyDb"] = ds
        }
        return Database.connect(ds)
    }

    private fun createSchemaAndSeed(database: Database) {
        transaction(database) {
            SchemaUtils.create(
                CajaTable,
                SucursalTable,
                SucursalAlmacenTable,
                VendedorTable,
                ParametrosGeneralesTablePA,
                AlmacenTable,
            )
            CajaTable.insert {
                it[idCaja] = "1"
                it[codCaja] = "CAJA1"
                it[descripcion] = "Caja principal"
                it[idSucursal] = 1
                it[serieCaja] = "A"
                it[caja] = "01"
            }
            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC1"
                it[serie] = "001"
                it[sucursal] = "Principal"
                it[descripcion] = "Sucursal Principal"
            }
            VendedorTable.insert {
                it[idVendedor] = 5
                it[codVendedor] = 5
                it[nombre] = "Ana"
                it[codUsuarios] = "7"
                it[idTiendas] = "1"
                it[idCajas] = "1"
                it[activo] = 1
            }
        }
    }

    private fun companyToken(
        companyDb: String,
        withCountryCode: Boolean = true,
        withAdminDb: Boolean = true,
        tokenType: String = "company",
        userId: Int = 7,
    ): String {
        val builder =
            JWT
                .create()
                .withIssuer(jwt.domain)
                .withAudience(jwt.audience)
                .withClaim("token_type", tokenType)
                .withClaim("user_id", userId)
        if (withCountryCode) builder.withClaim("country_code", "PA")
        if (withAdminDb) builder.withClaim("admin_db", companyDb)
        return builder.sign(Algorithm.HMAC256(jwt.secret))
    }

    private suspend fun ApplicationTestBuilder.getCajas(
        token: String?,
        companyDbHeader: String?,
    ) = client.get("/api/cajas") {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        if (companyDbHeader != null) header("Company-DB", companyDbHeader)
    }

    @AfterTest
    fun tearDown() {
        dataSource?.close()
        dataSource = null
    }

    @Test
    fun `get cajas sin token responde 401 del plugin jwt`() =
        app {
            val response = getCajas(token = null, companyDbHeader = "x")
            // Characterization: el challenge 401 lo emite el plugin authenticate{}
            // ANTES de llegar al handler, por lo que el cuerpo es el default de
            // Ktor (no el mensaje "Token inválido" del seam, que aplica ya
            // autenticado).
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `get cajas con token identity responde 403 exigiendo token de empresa`() =
        app {
            val db = companyDbName()
            val response = getCajas(token = companyToken(db, tokenType = "identity"), companyDbHeader = db)
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("token de empresa"))
        }

    @Test
    fun `get cajas sin country_code en el jwt responde 400`() =
        app {
            val db = companyDbName()
            val response =
                getCajas(
                    token = companyToken(db, withCountryCode = false),
                    companyDbHeader = db,
                )
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("country_code"))
        }

    @Test
    fun `get cajas sin admin_db en el jwt responde 400`() =
        app {
            val db = companyDbName()
            val response =
                getCajas(
                    token = companyToken(db, withAdminDb = false),
                    companyDbHeader = db,
                )
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("admin_db"))
        }

    @Test
    fun `get cajas sin header Company-DB responde 400`() =
        app {
            val db = companyDbName()
            val response = getCajas(token = companyToken(db), companyDbHeader = null)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Company-DB"))
        }

    @Test
    fun `get cajas con Company-DB distinto al admin_db responde 403`() =
        app {
            val db = companyDbName()
            val response = getCajas(token = companyToken(db), companyDbHeader = "otra_empresa")
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("no coincide"))
        }

    @Test
    fun `get cajas con seam completo responde 200 y serializa el catalogo`() =
        app {
            val db = companyDbName()
            val database = seedH2CompanyDb(db)
            createSchemaAndSeed(database)

            // Nota: el seam valida Company-DB case-insensitive contra admin_db,
            // pero pasa el header VERBATIM a DatabaseManager, cuyo cache key es
            // case-sensitive. El header usa entonces el mismo casing del claim.
            val response = getCajas(token = companyToken(db), companyDbHeader = db)
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.bodyAsText()
            // Contrato de serialización del DTO Caja tal como sale por cable:
            // mezcla de camelCase y @SerialName snake_case (characterization).
            assertTrue(body.contains("\"idCaja\":\"1\""), body)
            assertTrue(body.contains("\"codCaja\":\"CAJA1\""), body)
            assertTrue(body.contains("\"estatus\":1"), body)
            assertTrue(body.contains("\"default_vendedor_id\":5"), body)
            assertTrue(body.contains("\"default_vendedor_name\":\"Ana\""), body)
            assertTrue(body.contains("\"available_sellers\":[{\"id\":5,\"nombre\":\"Ana\"}]"), body)
            assertTrue(body.contains("\"serie_sucursal\":\"001\""), body)
            assertTrue(body.contains("\"serieCaja\":\"A\""), body)
            assertTrue(body.contains("\"sucursalNombre\":\"Principal\""), body)
            assertTrue(body.contains("\"sucursalCodigo\":\"SUC1\""), body)
            assertTrue(body.contains("\"abr_moneda_base\":\"USD\""), body)
        }
}
