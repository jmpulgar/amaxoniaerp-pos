package com.amaxoniaerp.features.sync.domain

/**
 * Alcance de sincronización offline declarado por el dispositivo (ADR-008).
 * Viaja como parámetro en cada GET de la API de sync; el servidor filtra
 * bootstrap/manifest/reconcile server-side y se mantiene sin estado.
 *
 * `null` o conjunto vacío = TODOS (default de instalación).
 */
data class SyncScope(
    val departmentIds: Set<Int>,
    val branchIds: Set<Int>,
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = branchIds.isEmpty()

    companion object {
        const val MAX_IDS_PER_FILTER = 500

        /** Scope por defecto: sin restricción. */
        val ALL = SyncScope(departmentIds = emptySet(), branchIds = emptySet())

        /**
         * Parsea los parámetros CSV de la request (`deptIds=1,5,9`). Tolera
         * blancos, deduplica, descarta no numéricos y acota el tamaño para
         * evitar URLs desbordadas.
         */
        fun fromParams(
            deptIds: String?,
            branchIds: String?,
        ): SyncScope =
            SyncScope(
                departmentIds = parseIds(deptIds),
                branchIds = parseIds(branchIds),
            )

        private fun parseIds(csv: String?): Set<Int> {
            if (csv.isNullOrBlank()) return emptySet()
            return csv
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }
                .take(MAX_IDS_PER_FILTER)
                .toSet()
        }
    }
}
