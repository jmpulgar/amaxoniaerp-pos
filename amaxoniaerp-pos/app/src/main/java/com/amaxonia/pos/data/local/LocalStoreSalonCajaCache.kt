package com.amaxonia.pos.data.local

import androidx.datastore.preferences.core.edit
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore.Companion.TAG
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.payment.FormaPago
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import java.time.LocalDate

// Cachés de salón/caja sobre LocalStore: caja activa del día, formas de pago y
// decodificación de snapshots de áreas/mesas. Extensiones sobre LocalStore: mismo
// comportamiento que cuando eran miembros.

suspend fun LocalStore.saveActiveCaja(caja: Caja) {
    val session = readCompanySession() ?: return
    val snapshot =
        ActiveCajaSnapshot(
            companyDb = session.company.adminDb,
            date = LocalDate.now().toString(),
            caja = caja,
        )
    val json = AppJson.encodeToString(snapshot)
    dataStore.edit { prefs ->
        prefs[activeCajaKey] = json
    }
}

suspend fun LocalStore.readActiveCajaForToday(): Caja? {
    val json = dataStore.data.first()[activeCajaKey] ?: return null
    val snapshot =
        runCatching {
            AppJson.decodeFromString(ActiveCajaSnapshot.serializer(), json)
        }.getOrNull()
    val usable = snapshot != null && isActiveCajaSnapshotUsable(snapshot)
    return if (usable) {
        snapshot?.caja
    } else {
        clearActiveCaja()
        null
    }
}

private suspend fun LocalStore.isActiveCajaSnapshotUsable(snapshot: ActiveCajaSnapshot): Boolean {
    val session = readCompanySession()
    val isSameCompany = session?.company?.adminDb == snapshot.companyDb
    val isToday = snapshot.date == LocalDate.now().toString()
    return isSameCompany && isToday
}

suspend fun LocalStore.clearActiveCaja() {
    dataStore.edit { prefs ->
        prefs.remove(activeCajaKey)
    }
}

suspend fun LocalStore.saveFormasPago(
    cajaId: String?,
    formasPago: List<FormaPago>,
) {
    val session = readCompanySession() ?: return
    val snapshot =
        FormasPagoSnapshot(
            companyDb = session.company.adminDb,
            cajaId = cajaId,
            formasPago = formasPago,
        )
    val json = AppJson.encodeToString(snapshot)
    dataStore.edit { prefs ->
        prefs[formasPagoKey] = json
    }
}

suspend fun LocalStore.readFormasPago(cajaId: String?): List<FormaPago> {
    val json = dataStore.data.first()[formasPagoKey] ?: return emptyList()
    val snapshot =
        runCatching {
            AppJson.decodeFromString(FormasPagoSnapshot.serializer(), json)
        }.getOrNull()
    val usable = snapshot != null && isFormasPagoSnapshotUsable(snapshot, cajaId)
    return if (usable) snapshot?.formasPago ?: emptyList() else emptyList()
}

private suspend fun LocalStore.isFormasPagoSnapshotUsable(
    snapshot: FormasPagoSnapshot,
    cajaId: String?,
): Boolean {
    val session = readCompanySession()
    val isSameCompany = session?.company?.adminDb == snapshot.companyDb
    val isSameCaja = snapshot.cajaId == cajaId
    return isSameCompany && isSameCaja
}

internal fun LocalStore.decodeAreasSnapshot(json: String): AreasSnapshot? =
    runCatching { AppJson.decodeFromString(AreasSnapshot.serializer(), json) }
        .onFailure { SafeLog.e(TAG, "Stored areas snapshot is invalid", it) }
        .getOrNull()

internal fun LocalStore.decodeMesasSnapshot(json: String): MesasSnapshot? =
    runCatching { AppJson.decodeFromString(MesasSnapshot.serializer(), json) }
        .onFailure { SafeLog.e(TAG, "Stored mesas snapshot is invalid", it) }
        .getOrNull()
