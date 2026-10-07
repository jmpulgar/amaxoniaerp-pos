package com.amaxonia.erp.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amaxonia.erp.data.local.LocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistencia del alcance de sincronización y visualización offline en DataStore.
 * Conjuntos vacíos = sincronizar / mostrar TODO.
 */
class OfflineSyncSettingsStore(
    context: Context,
) {
    private val dataStore = LocalStore(context).dataStore

    val scopeFlow: Flow<OfflineSyncScope> =
        dataStore.data.map { prefs ->
            OfflineSyncScope(
                enabled = prefs[KEY_ENABLED] ?: false,
                departmentIds = parseIds(prefs[KEY_DEPT_IDS]),
                branchIds = parseIds(prefs[KEY_BRANCH_IDS]),
            )
        }.distinctUntilChanged()

    suspend fun load(): OfflineSyncScope {
        val prefs = dataStore.data.first()
        return OfflineSyncScope(
            enabled = prefs[KEY_ENABLED] ?: false,
            departmentIds = parseIds(prefs[KEY_DEPT_IDS]),
            branchIds = parseIds(prefs[KEY_BRANCH_IDS]),
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
            if (scope.allClients) {
                prefs.remove(KEY_BRANCH_IDS)
            } else {
                prefs[KEY_BRANCH_IDS] = scope.branchIds.joinToString(",")
            }
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ENABLED)
            prefs.remove(KEY_DEPT_IDS)
            prefs.remove(KEY_BRANCH_IDS)
        }
    }

    private fun parseIds(csv: String?): Set<Int> =
        csv
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            .orEmpty()

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("offline_sync_enabled")
        val KEY_DEPT_IDS = stringPreferencesKey("offline_sync_dept_ids")
        val KEY_BRANCH_IDS = stringPreferencesKey("offline_sync_branch_ids")
    }
}
