package com.amaxoniaerp.features.auth.route

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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de integración de las rutas de autenticación (`/auth/login`,
 * `/auth/company`) sobre el módulo real ([com.amaxoniaerp.module]).
 *
 * Solo ejercitan las capas de validación y seam que NO requieren base de
 * datos: la resolución feliz de login/select-company cae a la BD de
 * configuración del país (MySQL) y está cubierta por los tests de repositorio
 * con H2. Aquí se documenta además el comportamiento ACTUAL ante bodies
 * malformados (characterization, sin cambios de contrato).
 */
class AuthRoutesIntegrationTest {
    private lateinit var jwt: JwtConfig

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                jwt = loadJwtConfig()
            }
            // El módulo arranca perezosamente con la primera petición: este
            // ping garantiza que la config JWT esté resuelta antes de firmar
            // tokens en el cuerpo del test.
            client.get("/health")
            block()
        }

    private fun identityToken(withUserId: Boolean): String {
        val builder =
            JWT
                .create()
                .withIssuer(jwt.domain)
                .withAudience(jwt.audience)
                .withClaim("token_type", "identity")
                .withClaim("username", "tester")
                .withClaim("country_code", "PA")
        if (withUserId) builder.withClaim("user_id", 7)
        return builder.sign(Algorithm.HMAC256(jwt.secret))
    }

    @Test
    fun `login sin header X-Country-Code responde 400 con mensaje estable`() =
        app {
            val response =
                client.post("/auth/login") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"username":"u","password":"p"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("X-Country-Code"))
        }

    @Test
    fun `login con pais no soportado responde 400`() =
        app {
            val response =
                client.post("/auth/login") {
                    header("X-Country-Code", "XX")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"username":"u","password":"p"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("País no soportado"))
        }

    @Test
    fun `login con body malformado responde segun el mapping vigente`() =
        app {
            val response =
                client.post("/auth/login") {
                    header("X-Country-Code", "VE")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{username:: no-json""")
                }
            // Characterization: hoy el receive malformado lanza
            // BadRequestException, que NO es ApiException y cae al handler
            // genérico de StatusPages (500 estable). Contraste: POST /api/cajas/close
            // sí traduce payload malformado a 400.
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Error interno del servidor"))
        }

    @Test
    fun `select company sin token responde 401`() =
        app {
            val response =
                client.post("/auth/company") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"companyId":1}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `select company con token sin user_id responde 401 con mensaje estable`() =
        app {
            val response =
                client.post("/auth/company") {
                    header(HttpHeaders.Authorization, "Bearer ${identityToken(withUserId = false)}")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"companyId":1}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("user_id"))
        }
}
