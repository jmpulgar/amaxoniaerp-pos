package com.amaxonia.pos.data.sync

/**
 * Alcance de sincronización offline en el dispositivo (ADR-008).
 * Conjuntos vacíos = TODOS.
 */
data class OfflineSyncScope(
    val departmentIds: Set<Int> = emptySet(),
    val branchIds: Set<Int> = emptySet(),
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = branchIds.isEmpty()

    companion object {
        val ALL = OfflineSyncScope()
    }
}
