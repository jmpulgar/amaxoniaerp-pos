package com.amaxoniaerp

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObservabilityTest {
    @Test
    fun `call id se genera y se responde en el header`() =
        testApplication {
            application {
                module()
            }
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val callId = response.headers["X-Request-Id"]
            assertNotNull(callId, "debe responder el header X-Request-Id")
            assertTrue(callId.startsWith("req-"), "el call id debe tener el prefijo req-: $callId")
            assertTrue(response.bodyAsText().contains("UP"))
        }
}
