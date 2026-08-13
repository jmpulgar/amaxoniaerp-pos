package com.amaxonia.pos.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import com.amaxonia.pos.data.remote.configureCajaRetry
import java.net.SocketException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(InternalAPI::class)
class CajaApiRetryTest {
    @Test
    fun `GET falla una vez por conexión y luego éxito realiza dos llamadas`() = runTest {
        val engine =
            ScriptedEngine(
                listOf(
                    { throw SocketException("Connection reset") },
                    { response(HttpStatusCode.OK) },
                ),
            )
        val client = testClient(engine)

        try {
            client.get("https://test.local/api/cajas")

            assertEquals(2, engine.calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `GET agotado tras tres llamadas devuelve el error final`() = runTest {
        val engine =
            ScriptedEngine(
                listOf(
                    { throw SocketException("Connection reset") },
                    { throw SocketException("Connection reset") },
                    { throw SocketException("Connection reset") },
                ),
            )
        val client = testClient(engine)

        try {
            val error = requireNotNull(runCatching { client.get("https://test.local/api/cajas") }.exceptionOrNull())

            assertEquals(3, engine.calls)
            assertNotNull(error.message)
            assertEquals(SocketException::class.java, error::class.java)
        } finally {
            client.close()
        }
    }

    @Test
    fun `POST no se reintenta`() = runTest {
        val engine =
            ScriptedEngine(
                listOf(
                    { throw SocketException("Connection reset") },
                ),
            )
        val client = testClient(engine)

        try {
            val error = requireNotNull(runCatching { client.post("https://test.local/api/cajas/open") }.exceptionOrNull())

            assertEquals(1, engine.calls)
            assertEquals(SocketException::class.java, error.javaClass)
        } finally {
            client.close()
        }
    }

    private fun testClient(engine: ScriptedEngine): HttpClient =
        HttpClient(engine) {
            install(HttpRequestRetry) {
                configureCajaRetry()
                delay { _ -> }
            }
        }

    private fun response(status: HttpStatusCode): HttpResponseData =
        HttpResponseData(
            statusCode = status,
            requestTime = GMTDate(0),
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel(ByteArray(0)),
            callContext = SupervisorJob() + Dispatchers.Unconfined,
        )

    @OptIn(InternalAPI::class)
    private class ScriptedEngine(
        private val actions: List<suspend (HttpRequestData) -> HttpResponseData>,
    ) : HttpClientEngine {
        private val job = SupervisorJob()
        private val actionQueue = ArrayDeque(actions)

        var calls: Int = 0
            private set

        override val coroutineContext: CoroutineContext = job + Dispatchers.Unconfined
        override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override val config: HttpClientEngineConfig = HttpClientEngineConfig()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            calls += 1
            return actionQueue.removeFirst()(data)
        }

        override fun close() {
            coroutineContext.cancel()
        }
    }
}
