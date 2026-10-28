package com.amaxonia.pos.data.sync

import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import com.amaxonia.pos.domain.model.offline.OfflineScopeSelection

/**
 * Alcance de sincronización offline en el dispositivo (ADR-008).
 * Conjuntos vacíos = TODOS.
 */
data class OfflineSyncScope(
    val enabled: Boolean = false,
    val departmentIds: Set<Int> = emptySet(),
    val branchIds: Set<Int> = emptySet(),
    val zoneCodes: Set<String> = emptySet(),
    val clientFilterMode: ClientFilterMode =
        when {
            zoneCodes.isNotEmpty() -> ClientFilterMode.ZONE
            branchIds.isNotEmpty() -> ClientFilterMode.SUCURSAL
            else -> ClientFilterMode.ALL
        },
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = clientFilterMode == ClientFilterMode.ALL

    fun isDepartmentVisible(id: Int): Boolean = allProducts || id in departmentIds
    fun isBranchVisible(id: Int): Boolean = allClients || (clientFilterMode == ClientFilterMode.SUCURSAL && id in branchIds)
    fun isZoneVisible(code: String): Boolean = allClients || (clientFilterMode == ClientFilterMode.ZONE && code in zoneCodes)

    fun deptParams(): List<Int>? = if (allProducts) null else departmentIds.toList()
    fun branchParams(): List<Int>? = if (clientFilterMode == ClientFilterMode.SUCURSAL) branchIds.toList() else null
    fun zoneParams(): List<String>? = if (clientFilterMode == ClientFilterMode.ZONE) zoneCodes.toList() else null

    fun toSelection(): OfflineScopeSelection =
        OfflineScopeSelection(
            departmentIds = departmentIds,
            sucursalIds = branchIds.map { it.toString() }.toSet(),
            zoneCodes = zoneCodes,
            clientFilterMode = clientFilterMode,
        )

    companion object {
        val DISABLED = OfflineSyncScope(enabled = false)
        val ALL = OfflineSyncScope(enabled = true)

        fun fromSelection(selection: OfflineScopeSelection, enabled: Boolean): OfflineSyncScope =
            OfflineSyncScope(
                enabled = enabled,
                departmentIds = selection.departmentIds,
                branchIds = selection.sucursalIds.mapNotNull { it.toIntOrNull() }.toSet(),
                zoneCodes = selection.zoneCodes,
                clientFilterMode = selection.clientFilterMode,
            )
    }
}
