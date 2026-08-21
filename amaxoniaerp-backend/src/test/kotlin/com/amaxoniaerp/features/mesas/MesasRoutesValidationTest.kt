package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.loadJwtConfig
import com.amaxoniaerp.module
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validación de parámetros en `/api/pos/areas` sobre el módulo real.
 * Ambas reglas responden ANTES de tocar base de datos (characterization del
 * orden interno: contexto POS -> cajaId -> areaId -> conexión).
 */
class MesasRoutesValidationTest {
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

    /** Token de empresa completo: el POS POS deriva todo del JWT + user_id. */
    private fun posToken(): String =
        JWT
            .create()
            .withIssuer(jwt.domain)
            .withAudience(jwt.audience)
            .withClaim("token_type", "company")
            .withClaim("user_id", 7)
            .withClaim("country_code", "PA")
            .withClaim("admin_db", "mesas_seam")
            .sign(Algorithm.HMAC256(jwt.secret))

    @Test
    fun `areas sin cajaId responde 400 con mensaje estable`() =
        app {
            val response =
                client.get("/api/pos/areas") {
                    header(HttpHeaders.Authorization, "Bearer ${posToken()}")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("cajaId es requerido"))
        }

    @Test
    fun `mesas con areaId no numerico responde 400`() =
        app {
            val response =
                client.get("/api/pos/areas/abc/mesas?cajaId=1") {
                    header(HttpHeaders.Authorization, "Bearer ${posToken()}")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("identificador de área"))
        }
}
