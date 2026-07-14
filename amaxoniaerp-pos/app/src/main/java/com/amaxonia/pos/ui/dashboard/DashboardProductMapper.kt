package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.ImageUrlResolver

class DashboardProductMapper(
    private val imageUrlResolver: ImageUrlResolver,
) {
    fun fromProduct(
        product: Product,
        adminDatabase: String,
    ): DashboardProduct =
        DashboardProduct(
            id = product.id,
            name = product.description,
            price = product.prices.firstOrNull()?.pricePlusTax ?: 0.0,
            taxRate = product.taxRate,
            isExempt = product.isExempt,
            imageUrl = imageUrl(adminDatabase, product.photoUrl),
            category = product.department.ifEmpty { "General" },
            code = product.code,
            barcode = product.barcode1,
            sourceProduct = product,
        )

    fun imageUrl(
        adminDatabase: String,
        photoPath: String,
    ): String {
        if (photoPath.isBlank() || adminDatabase.isBlank()) return ""
        return imageUrlResolver.product(adminDatabase, photoPath)
    }
}
