package com.amaxoniaerp.features.creditnotes.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.core.database.DatabaseManager
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validación de query params en `GET /api/pos/notas-credito` sobre el módulo
 * real. `resolveScope` conecta la empresa ANTES de validar paginación/fechas,
 * así que se siembra un DataSource H2 vacío en la caché de [DatabaseManager]
 * (reflexión, misma técnica de [com.amaxoniaerp.core.tenant]); no se consultan
 * tablas: las reglas validadas responden antes del servicio.
 */
class CreditNoteRoutesValidationTest {
    private lateinit var jwt: JwtConfig
    private var dataSource: HikariDataSource? = null
    private val companyDb = "nc_seam_${System.nanoTime()}"

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                jwt = loadJwtConfig()
            }
            client.get("/health")
            block()
        }

    private fun seedEmptyH2() {
        val ds =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:nc_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        dataSource = ds
        val field = DatabaseManager::class.java.getDeclaredField("companyDataSources")
        field.isAccessible = true
        val cache = field.get(DatabaseManager) as MutableMap<Any?, Any?>
        synchronized(cache) { cache["PA:$companyDb"] = ds }
    }

    private fun companyToken(): String =
        JWT
            .create()
            .withIssuer(jwt.domain)
            .withAudience(jwt.audience)
            .withClaim("token_type", "company")
            .withClaim("user_id", 7)
            .withClaim("country_code", "PA")
            .withClaim("admin_db", companyDb)
            .sign(Algorithm.HMAC256(jwt.secret))

    @AfterTest
    fun tearDown() {
        dataSource?.close()
        dataSource = null
    }

    @Test
    fun `listar con limit mayor al maximo responde 400 de paginacion`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito?limit=999") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("paginación"))
        }

    @Test
    fun `fecha invalida se mapea a 400 via excepcion de validacion`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito?limit=5&fecha_inicio=31-12-2026") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Fecha inválida"))
        }

    @Test
    fun `listar facturas elegibles con fecha invalida responde 400`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito/facturas?limit=5&fecha_inicio=invalid-date") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Fecha inválida"))
        }

    @Test
    fun `listar facturas elegibles con fechaFin anterior a fechaInicio responde 400`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito/facturas?limit=5&fecha_inicio=2026-08-15&fecha_fin=2026-08-01") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("La fecha final debe ser mayor o igual a la fecha inicial"))
        }

    @Test
    fun `listar facturas elegibles con rango mayor a 1 mes responde 400`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito/facturas?limit=5&fecha_inicio=2026-06-01&fecha_fin=2026-08-01") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("El rango de consulta no puede superar 1 mes"))
        }

    @Test
    fun `listar notas credito con rango mayor a 1 mes responde 400`() =
        app {
            seedEmptyH2()
            val response =
                client.get("/api/pos/notas-credito?limit=5&fecha_inicio=2026-06-01&fecha_fin=2026-08-01") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken()}")
                    header("Company-DB", companyDb)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("El rango de consulta no puede superar 1 mes"))
        }
}
