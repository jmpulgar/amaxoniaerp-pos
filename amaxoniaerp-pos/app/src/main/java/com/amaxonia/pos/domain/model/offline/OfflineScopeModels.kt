package com.amaxonia.pos.domain.model.offline

enum class ClientFilterMode {
    ALL,
    SUCURSAL,
    ZONE,
}

/**
 * Alcance de sincronización offline seleccionado por el usuario (ADR-008).
 * Conjuntos vacíos = sincronizar TODO.
 */
data class OfflineScopeSelection(
    val departmentIds: Set<Int> = emptySet(),
    val sucursalIds: Set<String> = emptySet(),
    val zoneCodes: Set<String> = emptySet(),
    val clientFilterMode: ClientFilterMode =
        when {
            zoneCodes.isNotEmpty() -> ClientFilterMode.ZONE
            sucursalIds.isNotEmpty() -> ClientFilterMode.SUCURSAL
            else -> ClientFilterMode.ALL
        },
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = clientFilterMode == ClientFilterMode.ALL
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
