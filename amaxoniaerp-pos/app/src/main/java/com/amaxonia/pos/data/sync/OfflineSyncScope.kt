package com.amaxonia.pos.data.sync

import com.amaxonia.pos.domain.model.offline.OfflineScopeSelection

/**
 * Alcance de sincronización offline en el dispositivo (ADR-008).
 * Conjuntos vacíos = TODOS.
 */
data class OfflineSyncScope(
    val enabled: Boolean = false,
    val departmentIds: Set<Int> = emptySet(),
    val branchIds: Set<Int> = emptySet(),
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = branchIds.isEmpty()

    fun toSelection(): OfflineScopeSelection =
        OfflineScopeSelection(
            departmentIds = departmentIds,
            sucursalIds = branchIds.map { it.toString() }.toSet(),
        )

    companion object {
        val DISABLED = OfflineSyncScope(enabled = false)
        val ALL = OfflineSyncScope(enabled = true)

        fun fromSelection(selection: OfflineScopeSelection, enabled: Boolean): OfflineSyncScope =
            OfflineSyncScope(
                enabled = enabled,
                departmentIds = selection.departmentIds,
                branchIds = selection.sucursalIds.mapNotNull { it.toIntOrNull() }.toSet(),
            )
    }
}
