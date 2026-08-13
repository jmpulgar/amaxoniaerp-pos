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
                sucursalId = 7,
                fechaInicio = "2026-01-01",
                fechaFin = "2026-01-31",
                estatus = listOf(1, 2),
            ),
        )

        assertEquals("INV-001", request.url.parameters["search"])
        assertEquals("alice", request.url.parameters["usuario"])
        assertEquals("7", request.url.parameters["sucursal_id"])
        assertEquals("2026-01-01", request.url.parameters["fecha_inicio"])
        assertEquals("2026-01-31", request.url.parameters["fecha_fin"])
        assertEquals("1,2", request.url.parameters["estatus"])
    }

    @Test
    fun emptyHistoryFilterDoesNotAddOptionalParameters() {
        val request = HttpRequestBuilder()

        request.applyInvoiceHistoryFilter(InvoiceHistoryFilter())

        assertNull(request.url.parameters["search"])
        assertNull(request.url.parameters["usuario"])
        assertNull(request.url.parameters["sucursal_id"])
        assertNull(request.url.parameters["fecha_inicio"])
        assertNull(request.url.parameters["fecha_fin"])
        assertNull(request.url.parameters["estatus"])
    }
}
