package com.amaxonia.pos.domain.repository

interface ImageUrlResolver {
    fun product(
        companyDatabase: String,
        photoPath: String,
    ): String

    fun client(
        companyDatabase: String,
        clientId: String,
        filename: String,
    ): String
}
