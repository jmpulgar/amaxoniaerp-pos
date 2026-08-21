package com.amaxoniaerp

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests del contrato de mapeo de errores ([statusFor] + StatusPages instalado
 * por el módulo real): cada categoría de [ApiException] tiene un código HTTP
 * estable y un cuerpo público `{"error": "<mensaje>"}` sin detalles internos;
 * cualquier excepción no tipada cae al fallback 500 con mensaje genérico.
 */
class StatusPagesErrorContractTest {
    private fun appWithThrowingRoute(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                routing {
                    get("/test-error/{category}") {
                        when (call.parameters["category"]) {
                            "validation" -> throw ApiException(ErrorCategory.Validation, "dato invalido")
                            "unauthorized" -> throw ApiException(ErrorCategory.Unauthorized, "sin sesion")
                            "forbidden" -> throw ApiException(ErrorCategory.Forbidden, "no permitido")
                            "notfound" -> throw ApiException(ErrorCategory.NotFound, "no existe")
                            "conflict" -> throw ApiException(ErrorCategory.Conflict, "duplicado")
                            "domainrule" -> throw ApiException(ErrorCategory.DomainRule, "regla de negocio")
                            "externalservice" -> throw ApiException(ErrorCategory.ExternalService, "pac no responde")
                            "unexpected" -> throw ApiException(ErrorCategory.Unexpected, "falla interna")
                            else -> error("boom crudo con detalle interno")
                        }
                    }
                    get("/test-ok") { call.respondText("ok") }
                }
            }
            block()
        }

    @Test
    fun `validation se mapea a 400`() = assertCategory("validation", HttpStatusCode.BadRequest)

    @Test
    fun `unauthorized se mapea a 401`() = assertCategory("unauthorized", HttpStatusCode.Unauthorized)

    @Test
    fun `forbidden se mapea a 403`() = assertCategory("forbidden", HttpStatusCode.Forbidden)

    @Test
    fun `notfound se mapea a 404`() = assertCategory("notfound", HttpStatusCode.NotFound)

    @Test
    fun `conflict se mapea a 409`() = assertCategory("conflict", HttpStatusCode.Conflict)

    @Test
    fun `domainrule se mapea a 400`() = assertCategory("domainrule", HttpStatusCode.BadRequest)

    @Test
    fun `externalservice se mapea a 502`() = assertCategory("externalservice", HttpStatusCode.BadGateway)

    @Test
    fun `unexpected se mapea a 500`() = assertCategory("unexpected", HttpStatusCode.InternalServerError)

    @Test
    fun `excepcion no tipada cae al fallback 500 sin filtrar detalles internos`() =
        appWithThrowingRoute {
            val response = client.get("/test-error/crudo")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertEquals("""{"error":"Error interno del servidor"}""", body)
            assertEquals(false, body.contains("boom crudo"), "no debe filtrar el mensaje interno")
        }

    @Test
    fun `ruta sana sigue respondiendo 200`() =
        appWithThrowingRoute {
            val response = client.get("/test-ok")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    private fun assertCategory(
        category: String,
        expected: HttpStatusCode,
    ) = appWithThrowingRoute {
        val response = client.get("/test-error/$category")
        assertEquals(expected, response.status)
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"error\""), "el cuerpo público debe tener la clave error: $body")
    }
}
