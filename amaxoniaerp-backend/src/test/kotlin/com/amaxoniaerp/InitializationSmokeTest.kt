package com.amaxoniaerp

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-123 — Smoke de inicialización del backend con medición informativa:
 * mide el arranque en frío del módulo completo ([module] + primer /health)
 * y lo imprime para el PLAN (§FASE 12). NO afirma duración (no flaky); solo
 * que la inicialización completa termina sana.
 */
class InitializationSmokeTest {
    @Test
    fun `la inicializacion del modulo completa y responde health`() =
        testApplication {
            val startedAt = System.nanoTime()
            application {
                module()
            }
            val response = client.get("/health")
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().isNotBlank())
            println("INIT_METRIC backend module()+first-health=${elapsedMs}ms")
        }
}
