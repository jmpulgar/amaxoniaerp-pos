package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistencia del alcance de sincronización offline (ADR-008, PLAN §18).
 * Conjuntos vacíos = sincronizar TODO. Vive en DataStore por empresa/dispositivo.
 */
class OfflineSyncSettingsStore(
    context: Context,
) {
    private val dataStore = LocalStore(context).dataStore

    val scopeFlow: Flow<OfflineSyncScope> =
        dataStore.data.map { prefs ->
            val branchIds = parseIds(prefs[KEY_BRANCH_IDS])
            val zoneCodes = parseStrings(prefs[KEY_ZONE_CODES])
            val clientFilterMode =
                when (prefs[KEY_CLIENT_MODE]) {
                    ClientFilterMode.ZONE.name -> ClientFilterMode.ZONE
                    ClientFilterMode.SUCURSAL.name -> ClientFilterMode.SUCURSAL
                    ClientFilterMode.ALL.name -> ClientFilterMode.ALL
                    else -> if (branchIds.isNotEmpty()) ClientFilterMode.SUCURSAL else ClientFilterMode.ALL
                }
            OfflineSyncScope(
                enabled = prefs[KEY_ENABLED] ?: false,
                departmentIds = parseIds(prefs[KEY_DEPT_IDS]),
                branchIds = branchIds,
                zoneCodes = zoneCodes,
                clientFilterMode = clientFilterMode,
            )
        }.distinctUntilChanged()

    val scopeSelectionFlow: Flow<com.amaxonia.pos.domain.model.offline.OfflineScopeSelection> =
        scopeFlow.map { it.toSelection() }.distinctUntilChanged()

    suspend fun load(): OfflineSyncScope {
        val prefs = dataStore.data.first()
        val branchIds = parseIds(prefs[KEY_BRANCH_IDS])
        val zoneCodes = parseStrings(prefs[KEY_ZONE_CODES])
        val clientFilterMode =
            when (prefs[KEY_CLIENT_MODE]) {
                ClientFilterMode.ZONE.name -> ClientFilterMode.ZONE
                ClientFilterMode.SUCURSAL.name -> ClientFilterMode.SUCURSAL
                ClientFilterMode.ALL.name -> ClientFilterMode.ALL
                else -> if (branchIds.isNotEmpty()) ClientFilterMode.SUCURSAL else ClientFilterMode.ALL
            }
        return OfflineSyncScope(
            enabled = prefs[KEY_ENABLED] ?: false,
            departmentIds = parseIds(prefs[KEY_DEPT_IDS]),
            branchIds = branchIds,
            zoneCodes = zoneCodes,
            clientFilterMode = clientFilterMode,
        )
    }

    suspend fun save(scope: OfflineSyncScope) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = scope.enabled
            if (scope.allProducts) {
                prefs.remove(KEY_DEPT_IDS)
            } else {
                prefs[KEY_DEPT_IDS] = scope.departmentIds.joinToString(",")
            }
            prefs[KEY_CLIENT_MODE] = scope.clientFilterMode.name
            when (scope.clientFilterMode) {
                ClientFilterMode.ALL -> {
                    prefs.remove(KEY_BRANCH_IDS)
                    prefs.remove(KEY_ZONE_CODES)
                }
                ClientFilterMode.SUCURSAL -> {
                    prefs[KEY_BRANCH_IDS] = scope.branchIds.joinToString(",")
                    prefs.remove(KEY_ZONE_CODES)
                }
                ClientFilterMode.ZONE -> {
                    prefs[KEY_ZONE_CODES] = scope.zoneCodes.joinToString(",")
                    prefs.remove(KEY_BRANCH_IDS)
                }
            }
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ENABLED)
            prefs.remove(KEY_DEPT_IDS)
            prefs.remove(KEY_BRANCH_IDS)
            prefs.remove(KEY_ZONE_CODES)
            prefs.remove(KEY_CLIENT_MODE)
        }
    }

    private fun parseIds(csv: String?): Set<Int> =
        csv
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            .orEmpty()

    private fun parseStrings(csv: String?): Set<String> =
        csv
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("offline_sync_enabled")
        val KEY_DEPT_IDS = stringPreferencesKey("offline_sync_dept_ids")
        val KEY_BRANCH_IDS = stringPreferencesKey("offline_sync_branch_ids")
        val KEY_ZONE_CODES = stringPreferencesKey("offline_sync_zone_codes")
        val KEY_CLIENT_MODE = stringPreferencesKey("offline_sync_client_mode")
    }
}
