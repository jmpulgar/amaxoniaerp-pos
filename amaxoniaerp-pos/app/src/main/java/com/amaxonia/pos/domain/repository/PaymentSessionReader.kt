package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.tenant.SaleTenant

interface PaymentCountryReader {
    suspend fun currentCountryCode(): String
}

interface PaymentSessionReader : PaymentCountryReader {
    suspend fun currentUsername(): String

    /**
     * The tenant that owns every sale attempt opened by this session
     * (auditoría ítem 3 / TEN-001). Returns null when no company session is
     * active — callers MUST NOT persist a row under those conditions because
     * the row could later be processed under the wrong tenant.
     */
    suspend fun currentTenant(): SaleTenant?
}
