package com.amaxonia.pos.data.local

import androidx.datastore.preferences.core.edit
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import kotlinx.coroutines.flow.first

// Último pago exitoso cacheado sobre LocalStore. Extensiones sobre LocalStore:
// mismo comportamiento que cuando eran miembros.

suspend fun LocalStore.saveLastPaymentSuccess(payload: PaymentSuccessPayload) {
    val json = AppJson.encodeToString(PaymentSuccessPayload.serializer(), payload)
    dataStore.edit { prefs ->
        prefs[lastPaymentSuccessKey] = json
        prefs[lastPaymentSuccessTransactionIdKey] = payload.transactionId
    }
}

/**
 * Reads the cached payment success payload.
 * We cache only the most recent transaction to keep preferences storage small.
 */
suspend fun LocalStore.readLastPaymentSuccess(transactionId: String): PaymentSuccessPayload? {
    val prefs = dataStore.data.first()
    val json = prefs[lastPaymentSuccessKey]
    val cachedTransactionId = prefs[lastPaymentSuccessTransactionIdKey]
    return if (json == null || cachedTransactionId != transactionId) {
        null
    } else {
        runCatching {
            AppJson.decodeFromString(PaymentSuccessPayload.serializer(), json)
        }.getOrNull()
    }
}
