package com.amaxoniaerp.features.sales.route

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
 * Tests de integración de la capa de acceso a `POST /api/pos/ventas/procesar`
 * sobre el módulo real. Cubren la barrera de autenticación y la traducción del
 * body malformado según el comportamiento vigente (characterization); la ruta
 * resuelve la empresa directamente desde `admin_db` del JWT (sin header).
 */
class SalesRoutesIntegrationTest {
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
    fun `procesar venta sin token responde 401`() =
        app {
            val response =
                client.post("/api/pos/ventas/procesar") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"factura":{}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `procesar venta con body malformado cae al fallback interno vigente`() =
        app {
            val response =
                client.post("/api/pos/ventas/procesar") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken(adminDb = "ventas_seam")}")
                    header("Company-DB", "ventas_seam")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{venta rota""")
                }
            // Characterization: igual que en /auth/login, el receive malformado
            // lanza BadRequestException que NO es ApiException y el handler
            // genérico de StatusPages responde 500 estable. Contraste:
            // POST /api/cajas/close sí traduce payload inválido a 400.
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Error interno del servidor"))
        }
}
