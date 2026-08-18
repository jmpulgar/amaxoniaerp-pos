package com.amaxoniaerp.core.error

import com.amaxoniaerp.statusFor
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ErrorModelTest {
    @Test
    fun `cada categoria mapea al HTTP correcto`() {
        assertEquals(HttpStatusCode.BadRequest, statusFor(ErrorCategory.Validation))
        assertEquals(HttpStatusCode.Unauthorized, statusFor(ErrorCategory.Unauthorized))
        assertEquals(HttpStatusCode.Forbidden, statusFor(ErrorCategory.Forbidden))
        assertEquals(HttpStatusCode.NotFound, statusFor(ErrorCategory.NotFound))
        assertEquals(HttpStatusCode.Conflict, statusFor(ErrorCategory.Conflict))
        assertEquals(HttpStatusCode.BadRequest, statusFor(ErrorCategory.DomainRule))
        assertEquals(HttpStatusCode.BadGateway, statusFor(ErrorCategory.ExternalService))
        assertEquals(HttpStatusCode.InternalServerError, statusFor(ErrorCategory.Unexpected))
    }

    @Test
    fun `ApiException conserva categoria, mensaje publico y causa interna`() {
        val internal = IllegalStateException("detalle SQL interno")
        val ex = ApiException(ErrorCategory.NotFound, "Registro no encontrado", internal)

        assertSame(ErrorCategory.NotFound, ex.category)
        assertEquals("Registro no encontrado", ex.message)
        assertSame(internal, ex.cause)
    }

    @Test
    fun `ApiException puede crearse sin causa interna`() {
        val ex = ApiException(ErrorCategory.Validation, "Solicitud inválida")

        assertSame(ErrorCategory.Validation, ex.category)
        assertNull(ex.cause)
    }
}
