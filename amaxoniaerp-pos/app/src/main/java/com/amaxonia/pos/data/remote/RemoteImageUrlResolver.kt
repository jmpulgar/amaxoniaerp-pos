package com.amaxonia.pos.data.remote

import com.amaxonia.pos.domain.repository.ImageUrlResolver

class RemoteImageUrlResolver(
    private val configManager: ApiConfigManager,
) : ImageUrlResolver {
    override fun product(
        companyDatabase: String,
        photoPath: String,
    ): String =
        ImageUrlHelper.productImageUrl(
            baseUrl = configManager.baseUrl.value,
            countryCode = configManager.getCurrentCountryCode(),
            companyDb = companyDatabase,
            photoPath = photoPath,
        )

    override fun client(
        companyDatabase: String,
        clientId: String,
        filename: String,
    ): String =
        ImageUrlHelper.clientPhotoUrl(
            baseUrl = configManager.baseUrl.value,
            countryCode = configManager.getCurrentCountryCode(),
            companyDb = companyDatabase,
            idCliente = clientId,
            photoFilename = filename,
        )
}
