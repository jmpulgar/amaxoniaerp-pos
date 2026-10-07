package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * Ajustes del kiosco guardados por el administrativo en `parametros_generales`.
 * [mediaFiles] son los nombres de archivo de `banner_1`, `banner_2`, `banner_3` y `menu_1`
 * (en ese orden, solo los no vacíos).
 */
data class KioskSettings(
    val dispatch: String = DEFAULT_DISPATCH,
    val kitchenPrinterIp: String? = null,
    val diningModes: List<String> = DEFAULT_DINING_MODES,
    val mediaFiles: List<String> = emptyList(),
    val claveKiosko: String? = null,
    val defaultCodClienteFactura: String? = null,
) {
    companion object {
        const val DEFAULT_DISPATCH = "RETIRO_MOSTRADOR"
        val DISPATCH_VALUES = setOf("RETIRO_MOSTRADOR", "IMPRESORA_COCINA", "MESAS")
        val DEFAULT_DINING_MODES = listOf("COMER_AQUI", "PARA_LLEVAR")
    }
}

/**
 * Lee [KioskSettings] de forma defensiva: las columnas `kiosco_*`, `clave_kiosko` y los
 * banners pueden no existir todavía en un tenant, así que primero se detectan con
 * [KioskSchemaInspector] y luego se seleccionan solo las presentes (nunca `selectAll`).
 * Cualquier columna ausente toma su valor por defecto.
 */
class KioskSettingsRepository(
    private val schemaInspector: KioskSchemaInspector = KioskSchemaInspector(),
) {
    private val logger = LoggerFactory.getLogger(KioskSettingsRepository::class.java)

    suspend fun load(database: Database): KioskSettings {
        val present = schemaInspector.columns(database, KioskParametrosTable.tableName)
        val columns = READABLE_COLUMNS.filter { it.name in present }
        if (columns.isEmpty()) return KioskSettings()

        val row =
            try {
                dbQuery(database) {
                    KioskParametrosTable
                        .select(columns)
                        .orderBy(KioskParametrosTable.codEmpresa)
                        .limit(1)
                        .singleOrNull()
                }
            } catch (e: SQLException) {
                schemaInspector.invalidate(database)
                logger.warn("[KIOSK] No se pudieron leer los ajustes del kiosco: {}", e.javaClass.simpleName)
                null
            } ?: return KioskSettings()

        return row.toSettings(columns.toSet())
    }

    private fun ResultRow.toSettings(columns: Set<Column<String?>>): KioskSettings {
        fun read(column: Column<String?>): String? = if (column in columns) this[column]?.trim()?.takeIf { it.isNotEmpty() } else null

        val dispatch =
            read(KioskParametrosTable.kioscoDestinoPedido)
                ?.uppercase()
                ?.takeIf { it in KioskSettings.DISPATCH_VALUES }
                ?: KioskSettings.DEFAULT_DISPATCH
        val diningModes =
            read(KioskParametrosTable.kioscoModalidades)
                ?.split(",")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: KioskSettings.DEFAULT_DINING_MODES

        return KioskSettings(
            dispatch = dispatch,
            kitchenPrinterIp = read(KioskParametrosTable.kioscoImpresoraCocinaIp),
            diningModes = diningModes,
            mediaFiles = MEDIA_COLUMNS.mapNotNull { read(it) },
            claveKiosko = read(KioskParametrosTable.claveKiosko),
            defaultCodClienteFactura = read(KioskParametrosTable.defaultCodClienteFactura),
        )
    }

    private companion object {
        val MEDIA_COLUMNS =
            listOf(
                KioskParametrosTable.banner1,
                KioskParametrosTable.banner2,
                KioskParametrosTable.banner3,
                KioskParametrosTable.menu1,
            )
        val READABLE_COLUMNS =
            listOf(
                KioskParametrosTable.defaultCodClienteFactura,
                KioskParametrosTable.kioscoDestinoPedido,
                KioskParametrosTable.kioscoImpresoraCocinaIp,
                KioskParametrosTable.kioscoModalidades,
                KioskParametrosTable.claveKiosko,
            ) + MEDIA_COLUMNS
    }
}
