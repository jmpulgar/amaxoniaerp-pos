package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductSessionReader

/**
 * Resuelve la URL completa de la imagen de un producto para el carrito
 * a partir de la ruta relativa y la base de datos administrativa de la sesión.
 */
class ResolveProductImageUrlUseCase(
    private val sessionReader: ProductSessionReader,
    private val imageUrlResolver: ImageUrlResolver,
) {
    @Volatile
    private var cachedAdminDb: String = ""

    suspend fun warmUp() {
        if (cachedAdminDb.isBlank()) {
            cachedAdminDb = runCatching { sessionReader.currentAdminDatabase() }.getOrDefault("")
        }
    }

    operator fun invoke(photoPath: String): String {
        if (photoPath.isBlank()) return ""
        if (photoPath.startsWith("http://") || photoPath.startsWith("https://")) return photoPath
        if (cachedAdminDb.isBlank()) return ""
        return imageUrlResolver.product(cachedAdminDb, photoPath)
    }
}
