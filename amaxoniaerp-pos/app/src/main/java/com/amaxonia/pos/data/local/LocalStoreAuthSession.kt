package com.amaxonia.pos.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore.Companion.SECURE_AUTH_SNAPSHOT
import com.amaxonia.pos.data.local.LocalStore.Companion.SECURE_COMPANY_SESSION
import com.amaxonia.pos.data.local.LocalStore.Companion.TAG
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString

// Sesión de autenticación/compañía sobre LocalStore: snapshots seguros (Auth/Company),
// limpieza de sesión y lectura de tenant. Extensiones sobre LocalStore: mismo
// comportamiento que cuando eran miembros.

suspend fun LocalStore.saveAuthSnapshot(snapshot: AuthSnapshot) {
    val json = AppJson.encodeToString(snapshot)
    writeSecureAndRemoveLegacy(SECURE_AUTH_SNAPSHOT, authSnapshotKey, json)
}

suspend fun LocalStore.readAuthSnapshot(): AuthSnapshot? {
    val json = readSecureOrMigrate(SECURE_AUTH_SNAPSHOT, authSnapshotKey) ?: return null
    return runCatching { AppJson.decodeFromString(AuthSnapshot.serializer(), json) }
        .onFailure { SafeLog.e(TAG, "Stored authentication snapshot is invalid", it) }
        .getOrNull()
}

suspend fun LocalStore.saveCompanySession(session: CompanySessionSnapshot) {
    val json = AppJson.encodeToString(session)
    writeSecureAndRemoveLegacy(SECURE_COMPANY_SESSION, companySessionKey, json)
}

suspend fun LocalStore.readCompanySession(): CompanySessionSnapshot? {
    val json = readSecureOrMigrate(SECURE_COMPANY_SESSION, companySessionKey) ?: return null
    return runCatching { AppJson.decodeFromString(CompanySessionSnapshot.serializer(), json) }
        .onFailure { SafeLog.e(TAG, "Stored company session is invalid", it) }
        .getOrNull()
}

suspend fun LocalStore.clearAuthSession() {
    removeSecureValue(SECURE_AUTH_SNAPSHOT)
    removeSecureValue(SECURE_COMPANY_SESSION)
    dataStore.edit { prefs ->
        prefs.remove(authSnapshotKey)
        prefs.remove(companySessionKey)
        prefs.remove(activeCajaKey)
        // La configuración de salón es por sucursal: al cerrar sesión debe desaparecer para
        // que un login distinto nunca vea áreas ni mesas de la sesión anterior.
        prefs.remove(areasKey)
        prefs.remove(mesasKey)
    }
}

suspend fun LocalStore.currentTenantId(): String? = readCompanySession()?.let { SaleTenant.idFor(it.company.id) }

internal suspend fun LocalStore.writeSecureAndRemoveLegacy(
    secureKey: String,
    legacyKey: Preferences.Key<String>,
    value: String,
) {
    secureWriter.write(secureKey, value)
    dataStore.edit { prefs -> prefs.remove(legacyKey) }
}

internal suspend fun LocalStore.readSecureOrMigrate(
    secureKey: String,
    legacyKey: Preferences.Key<String>,
): String? {
    val secureValue = readSecureValue(secureKey)
    if (secureValue == null) {
        return migrateLegacyToSecureStore(secureKey, legacyKey)
    }
    if (dataStore.data.first()[legacyKey] != null) {
        dataStore.edit { prefs -> prefs.remove(legacyKey) }
    }
    return secureValue
}

private suspend fun LocalStore.migrateLegacyToSecureStore(
    secureKey: String,
    legacyKey: Preferences.Key<String>,
): String? {
    val legacyValue = dataStore.data.first()[legacyKey] ?: return null
    return runCatching {
        secureWriter.write(secureKey, legacyValue)
        dataStore.edit { prefs -> prefs.remove(legacyKey) }
        legacyValue
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            // Same boundary as the original catches: cancellation and fatal errors propagate.
            when (error) {
                is CancellationException -> throw error
                is Exception -> {
                    SafeLog.e(TAG, "Secure migration failed; legacy value was preserved", error)
                    legacyValue
                }
                else -> throw error
            }
        },
    )
}

internal fun LocalStore.readSecureValue(key: String): String? =
    runCatching { secureStore.readString(key) }
        .onFailure { SafeLog.e(TAG, "Secure value could not be read", it) }
        .getOrNull()

internal fun LocalStore.removeSecureValue(key: String) {
    runCatching { secureStore.remove(key) }
        .onFailure { SafeLog.e(TAG, "Secure value could not be removed", it) }
        .getOrThrow()
}
