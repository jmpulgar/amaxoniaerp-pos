package com.amaxonia.pos.domain.repository

interface PaymentCountryReader {
    suspend fun currentCountryCode(): String
}

interface PaymentSessionReader : PaymentCountryReader {
    suspend fun currentUsername(): String
}
