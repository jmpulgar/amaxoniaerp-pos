package com.amaxonia.pos.domain.model.offline

/**
 * Alcance de sincronización offline seleccionado por el usuario (ADR-008).
 * Conjuntos vacíos = sincronizar TODO.
 */
data class OfflineScopeSelection(
    val departmentIds: Set<Int> = emptySet(),
    val sucursalIds: Set<String> = emptySet(),
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = sucursalIds.isEmpty()
}

/** Entrada de los catálogos del picker de "Ajustes Offline". */
data class OfflineCatalogEntry(
    val id: String,
    val nombre: String? = null,
)

/** Conteo estimado del servidor para una selección de alcance. */
data class OfflineScopePreview(
    val productCount: Long? = null,
    val clientCount: Long? = null,
)
