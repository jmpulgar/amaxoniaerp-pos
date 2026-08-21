package com.amaxoniaerp.features.caja

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.loadJwtConfig
import com.amaxoniaerp.module
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validación de payload en `POST /api/cajas/close` sobre el módulo real.
 *
 * El handler envuelve `receive<CajaCierreSaveRequest>()` en runCatching y
 * responde 400 con la forma pública [CajaCierreSaveResponse] ANTES de tocar la
 * base de datos — por eso estos tests no necesitan sembrar H2. Contrasta con
 * /auth/login y /api/pos/ventas/procesar, donde el body malformado cae al 500
 * genérico de StatusPages (characterized en sus propios tests).
 */
class CajaClosePayloadValidationTest {
    private lateinit var jwt: JwtConfig

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                jwt = loadJwtConfig()
            }
            client.get("/health")
            block()
        }

    private fun companyToken(adminDb: String): String =
        JWT
            .create()
            .withIssuer(jwt.domain)
            .withAudience(jwt.audience)
            .withClaim("token_type", "company")
            .withClaim("user_id", 7)
            .withClaim("country_code", "PA")
            .withClaim("admin_db", adminDb)
            .sign(Algorithm.HMAC256(jwt.secret))

    @Test
    fun `close sin token responde 401`() =
        app {
            val response =
                client.post("/api/cajas/close") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"id":"x"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `close con payload malformado responde 400 con forma publica estable`() =
        app {
            val db = "caja_close_seam"
            val response =
                client.post("/api/cajas/close") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken(db)}")
                    header("Company-DB", db)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{cierre roto""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"success\":false"), body)
            assertTrue(body.contains("\"error\""), body)
        }
}
