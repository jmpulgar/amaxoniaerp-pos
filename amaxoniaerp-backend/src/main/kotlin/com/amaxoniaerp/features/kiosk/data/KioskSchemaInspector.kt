package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Detecta qué tablas y columnas opcionales tiene la base de datos de una empresa.
 *
 * El kiosco reutiliza el esquema del ERP, pero varias piezas dependen de migraciones del
 * administrativo que pueden no estar aplicadas todavía en un tenant (columnas `kiosco_*` y
 * banners de `parametros_generales`, tablas `kiosco_pedido*`, columnas nuevas de combos).
 * Las columnas se leen de los metadatos de un `SELECT * ... WHERE 1 = 0` (no lee filas y
 * funciona igual en MySQL y H2) y se cachean por base de datos durante [CACHE_TTL_MS].
 * Una tabla ausente (o un error al consultarla) se reporta como conjunto vacío.
 */
class KioskSchemaInspector(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger(KioskSchemaInspector::class.java)
    private val cache = ConcurrentHashMap<CacheKey, CachedColumns>()

    /** Columnas (en minúsculas) de [table]; vacío si la tabla no existe. */
    suspend fun columns(
        database: Database,
        table: String,
    ): Set<String> {
        val key = CacheKey(database, table)
        val now = clock()
        cache[key]?.takeIf { now - it.checkedAtMs < CACHE_TTL_MS }?.let { return it.columns }
        val columns = probe(database, table)
        cache[key] = CachedColumns(columns, now)
        return columns
    }

    /** true si existen las tres tablas de pedidos del kiosco (migración del administrativo). */
    suspend fun hasKioskOrderTables(database: Database): Boolean = KIOSK_ORDER_TABLES.all { columns(database, it).isNotEmpty() }

    /** true si el modelo de combos tiene las tablas y columnas de 2026-10-01-RECETA-COMBO-VARIANTES. */
    suspend fun hasComboModel(database: Database): Boolean =
        columns(database, ComboItemTable.tableName).containsAll(REQUIRED_ITEM_COMBO_COLUMNS) &&
            columns(database, ComboGroupTable.tableName).containsAll(REQUIRED_GROUP_COLUMNS) &&
            columns(database, ComboGroupItemTable.tableName).containsAll(REQUIRED_GROUP_ITEM_COLUMNS)

    /** Olvida lo detectado para [database] (p. ej. tras un error de lectura). */
    fun invalidate(database: Database) {
        cache.keys.removeIf { it.database == database }
    }

    private suspend fun probe(
        database: Database,
        table: String,
    ): Set<String> =
        try {
            dbQuery(database) {
                TransactionManager.current().exec("SELECT * FROM $table WHERE 1 = 0") { rs ->
                    val meta = rs.metaData
                    (1..meta.columnCount).map { meta.getColumnLabel(it).lowercase() }.toSet()
                } ?: emptySet()
            }
        } catch (e: SQLException) {
            logger.info("[KIOSK] Tabla {} no disponible en el tenant: {}", table, e.javaClass.simpleName)
            emptySet()
        }

    private data class CacheKey(
        val database: Database,
        val table: String,
    )

    private data class CachedColumns(
        val columns: Set<String>,
        val checkedAtMs: Long,
    )

    companion object {
        const val CACHE_TTL_MS = 60_000L

        val KIOSK_ORDER_TABLES = listOf("kiosco_pedido", "kiosco_pedido_item", "kiosco_pedido_item_modificador")
        private val REQUIRED_ITEM_COMBO_COLUMNS = setOf("id_item_combo", "id_item", "id_combo")
        private val REQUIRED_GROUP_COLUMNS = setOf("id_grupo", "id_combo", "nombre_grupo", "adiciona", "maximo_veces", "tipo", "minimo")
        private val REQUIRED_GROUP_ITEM_COLUMNS =
            setOf("id_grupo_item", "id_grupo", "id_item", "nombre", "precio", "cantidad", "por_defecto", "orden")
    }
}
