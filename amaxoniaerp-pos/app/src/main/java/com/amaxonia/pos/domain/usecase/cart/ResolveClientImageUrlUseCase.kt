package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductSessionReader

class ResolveClientImageUrlUseCase(
    private val sessionReader: ProductSessionReader,
    private val imageUrlResolver: ImageUrlResolver,
) {
    suspend operator fun invoke(client: Client): String {
        val filename = client.photoFilename.takeIf { it.isNotBlank() }
        val adminDatabase = if (filename != null && client.id.isNotBlank()) sessionReader.currentAdminDatabase() else ""
        return if (filename != null && adminDatabase.isNotBlank()) {
            imageUrlResolver.client(adminDatabase, client.id, filename)
        } else {
            ""
        }
    }
}
