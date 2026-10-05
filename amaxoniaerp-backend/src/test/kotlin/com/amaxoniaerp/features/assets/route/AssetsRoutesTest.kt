package com.amaxoniaerp.features.assets.route

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetsRoutesTest {

    private lateinit var tempDir: File
    private lateinit var bannerFile: File
    private val fileContent = ByteArray(200) { it.toByte() }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("assets_test").toFile()
        val bannersDir = File(tempDir, "testdb/banners").apply { mkdirs() }
        bannerFile = File(bannersDir, "sample.mp4").apply {
            writeBytes(fileContent)
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `range request returns 206 partial content with immutable cache control`() = testApplication {
        install(PartialContent)
        routing {
            assetsRoutes(
                assetsBaseUrls = emptyMap(),
                dataBasePath = tempDir.absolutePath,
            )
        }

        val response = client.get("/api/data/PA/testdb/banners/sample.mp4") {
            header(HttpHeaders.Range, "bytes=0-99")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        val contentRange = response.headers[HttpHeaders.ContentRange]
        assertNotNull(contentRange)
        assertEquals("bytes 0-99/200", contentRange)

        val cacheControl = response.headers[HttpHeaders.CacheControl]
        assertNotNull(cacheControl)
        assertTrue(cacheControl.contains("immutable"), "Cache-Control must contain 'immutable'")

        val bytes = response.bodyAsBytes()
        assertEquals(100, bytes.size)
        assertContentEquals(fileContent.sliceArray(0..99), bytes)
    }

    @Test
    fun `full request returns 200 OK with immutable cache control`() = testApplication {
        install(PartialContent)
        routing {
            assetsRoutes(
                assetsBaseUrls = emptyMap(),
                dataBasePath = tempDir.absolutePath,
            )
        }

        val response = client.get("/api/data/PA/testdb/banners/sample.mp4")

        assertEquals(HttpStatusCode.OK, response.status)
        val cacheControl = response.headers[HttpHeaders.CacheControl]
        assertNotNull(cacheControl)
        assertTrue(cacheControl.contains("immutable"), "Cache-Control must contain 'immutable'")

        val bytes = response.bodyAsBytes()
        assertEquals(200, bytes.size)
        assertContentEquals(fileContent, bytes)
    }

    @Test
    fun `path traversal returns 400 bad request`() = testApplication {
        install(PartialContent)
        routing {
            assetsRoutes(
                assetsBaseUrls = emptyMap(),
                dataBasePath = tempDir.absolutePath,
            )
        }

        val response = client.get("/api/data/PA/testdb/banners/..%2Fsecret.txt")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid country code returns 400 bad request`() = testApplication {
        install(PartialContent)
        routing {
            assetsRoutes(
                assetsBaseUrls = emptyMap(),
                dataBasePath = tempDir.absolutePath,
            )
        }

        val response = client.get("/api/data/INVALID/testdb/banners/sample.mp4")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
