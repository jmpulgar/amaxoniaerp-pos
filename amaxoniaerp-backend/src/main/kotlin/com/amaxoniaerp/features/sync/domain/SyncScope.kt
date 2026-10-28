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
    val zoneCodes: Set<String> = emptySet(),
) {
    val allProducts: Boolean get() = departmentIds.isEmpty()
    val allClients: Boolean get() = branchIds.isEmpty() && zoneCodes.isEmpty()

    companion object {
        const val MAX_IDS_PER_FILTER = 500

        /** Scope por defecto: sin restricción. */
        val ALL = SyncScope(departmentIds = emptySet(), branchIds = emptySet(), zoneCodes = emptySet())

        /**
         * Parsea los parámetros CSV de la request (`deptIds=1,5,9`, `zoneCodes=Z1,Z2`). Tolera
         * blancos, deduplica, descarta vacíos y acota el tamaño para
         * evitar URLs desbordadas.
         */
        fun fromParams(
            deptIds: String?,
            branchIds: String?,
            zoneCodes: String? = null,
        ): SyncScope =
            SyncScope(
                departmentIds = parseIds(deptIds),
                branchIds = parseIds(branchIds),
                zoneCodes = parseStrings(zoneCodes),
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

        private fun parseStrings(csv: String?): Set<String> {
            if (csv.isNullOrBlank()) return emptySet()
            return csv
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(MAX_IDS_PER_FILTER)
                .toSet()
        }
    }
}
