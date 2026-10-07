package com.amaxonia.erp.data.sync

/**
 * Alcance de sincronización y visualización offline en el dispositivo.
 * Conjuntos vacíos = TODOS.
 */
data class OfflineSyncScope(
    val enabled: Boolean = false,
    val departmentIds: Set<Int> = emptySet(),
    val branchIds: Set<Int> = emptySet(),
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = branchIds.isEmpty()

    fun isDepartmentVisible(id: Int): Boolean = allProducts || id in departmentIds
    fun isBranchVisible(id: Int): Boolean = allClients || id in branchIds

    companion object {
        val DISABLED = OfflineSyncScope(enabled = false)
        val ALL = OfflineSyncScope(enabled = true)
    }
}
