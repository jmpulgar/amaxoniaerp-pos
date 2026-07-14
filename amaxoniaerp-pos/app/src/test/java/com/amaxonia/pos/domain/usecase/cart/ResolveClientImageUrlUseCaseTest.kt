package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductSessionReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResolveClientImageUrlUseCaseTest {
    @Test
    fun `resolves existing client image with current tenant`() =
        runTest {
            val resolver = RecordingImageUrlResolver()
            val useCase = ResolveClientImageUrlUseCase(sessionReader("tenant"), resolver)

            assertEquals("client://tenant/C-1/photo.jpg", useCase(Client(id = "C-1", photoFilename = "photo.jpg")))
            assertEquals(Triple("tenant", "C-1", "photo.jpg"), resolver.lastRequest)
        }

    @Test
    fun `blank image data avoids resolver`() =
        runTest {
            val resolver = RecordingImageUrlResolver()
            val useCase = ResolveClientImageUrlUseCase(sessionReader("tenant"), resolver)

            assertEquals("", useCase(Client(id = "C-1", photoFilename = "")))
            assertFalse(resolver.wasCalled)
        }

    private class RecordingImageUrlResolver : ImageUrlResolver {
        var lastRequest: Triple<String, String, String>? = null
        val wasCalled: Boolean get() = lastRequest != null

        override fun product(
            companyDatabase: String,
            photoPath: String,
        ): String = error("not used")

        override fun client(
            companyDatabase: String,
            clientId: String,
            filename: String,
        ): String {
            lastRequest = Triple(companyDatabase, clientId, filename)
            return "client://$companyDatabase/$clientId/$filename"
        }
    }

    private fun sessionReader(database: String): ProductSessionReader =
        object : ProductSessionReader {
            override suspend fun currentAdminDatabase(): String = database
        }
}
