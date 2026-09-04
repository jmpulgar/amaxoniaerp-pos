package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import io.ktor.client.request.HttpRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesApiFilterTest {
    @Test
    fun historyFilterSerializesAllBackendQueryParameters() {
        val request = HttpRequestBuilder()

        request.applyInvoiceHistoryFilter(
            InvoiceHistoryFilter(
                search = "INV-001",
                usuario = "alice",
                cajaId = "caja-1",
                fechaInicio = "2026-01-01",
                fechaFin = "2026-01-31",
            ),
        )

        assertEquals("INV-001", request.url.parameters["search"])
        assertEquals("alice", request.url.parameters["usuario"])
        assertEquals("caja-1", request.url.parameters["caja_id"])
        assertEquals("2026-01-01", request.url.parameters["fecha_inicio"])
        assertEquals("2026-01-31", request.url.parameters["fecha_fin"])
    }

    @Test
    fun emptyHistoryFilterDoesNotAddOptionalParameters() {
        val request = HttpRequestBuilder()

        request.applyInvoiceHistoryFilter(InvoiceHistoryFilter())

        assertNull(request.url.parameters["search"])
        assertNull(request.url.parameters["usuario"])
        assertNull(request.url.parameters["caja_id"])
        assertNull(request.url.parameters["fecha_inicio"])
        assertNull(request.url.parameters["fecha_fin"])
    }
}
