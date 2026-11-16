package com.amaxoniaerp.core.database

import org.jetbrains.exposed.sql.Transaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Acceso SQL parametrizado a tablas legacy del ERP cuyo esquema varía por país/tenant
 * (`pedido`, `pedido_detalle`, ...). Las inserciones se filtran contra las columnas reales de
 * la tabla, de modo que un tenant al que le falte una columna opcional no rompe la escritura.
 *
 * Siempre se usa dentro de una transacción Exposed abierta (`dbQuery`); todos los valores
 * viajan como parámetros JDBC, nunca concatenados.
 */
object LegacySql {
    /** Columnas (minúsculas) de [table]; vacío si la tabla no existe. */
    fun Transaction.columnsOf(table: String): Set<String> =
        runCatching {
            exec("SELECT * FROM $table WHERE 1 = 0") { rs ->
                val meta = rs.metaData
                (1..meta.columnCount).map { meta.getColumnLabel(it).lowercase() }.toSet()
            } ?: emptySet()
        }.getOrDefault(emptySet())

    /** Inserta [values] en [table] descartando las columnas que el tenant no tiene. */
    fun Transaction.insertAdaptive(
        table: String,
        values: Map<String, Any?>,
        knownColumns: Set<String> = columnsOf(table),
    ) {
        val present = values.filterKeys { it.lowercase() in knownColumns }
        require(present.isNotEmpty()) { "La tabla $table no tiene las columnas esperadas" }
        val columns = present.keys.joinToString(", ") { "`$it`" }
        val placeholders = present.keys.joinToString(", ") { "?" }
        execUpdate("INSERT INTO $table ($columns) VALUES ($placeholders)", present.values.toList())
    }

    /** Ejecuta un UPDATE/DELETE/INSERT parametrizado y devuelve las filas afectadas. */
    fun Transaction.execUpdate(
        sql: String,
        params: List<Any?> = emptyList(),
    ): Int = jdbc().prepareStatement(sql).use { ps -> ps.bind(params).executeUpdate() }

    /** Ejecuta un SELECT parametrizado y mapea cada fila. */
    fun <T> Transaction.execQuery(
        sql: String,
        params: List<Any?> = emptyList(),
        mapper: (ResultSet) -> T,
    ): List<T> =
        jdbc().prepareStatement(sql).use { ps ->
            ps.bind(params).executeQuery().use { rs ->
                buildList { while (rs.next()) add(mapper(rs)) }
            }
        }

    /**
     * Reserva atómicamente el siguiente valor de `correlativos.contador` para [campo].
     *
     * `correlativos` es MyISAM en el ERP (sin bloqueo de fila), por eso se usa el idiom
     * `LAST_INSERT_ID(expr)`, atómico por conexión. Devuelve el contador ANTES del incremento
     * cuando [returnPrevious] es true (convención de `cod_pedido` en el web) o el nuevo valor
     * (convención de `cod_caja_ingr_cobranza`). Devuelve null si no existe el registro.
     */
    fun Transaction.reserveCorrelativo(
        campo: String,
        returnPrevious: Boolean,
    ): Pair<Long, String>? {
        val formato =
            execQuery("SELECT formato FROM correlativos WHERE campo = ? ORDER BY id LIMIT 1", listOf(campo)) {
                it.getString("formato").orEmpty()
            }.firstOrNull() ?: return null
        val expression = if (returnPrevious) "LAST_INSERT_ID(contador) + 1" else "LAST_INSERT_ID(contador + 1)"
        val updated =
            execUpdate(
                "UPDATE correlativos SET contador = $expression WHERE campo = ? ORDER BY id LIMIT 1",
                listOf(campo),
            )
        if (updated == 0) return null
        val value = execQuery("SELECT LAST_INSERT_ID() AS v") { it.getLong("v") }.first()
        return value to formato
    }

    private fun Transaction.jdbc(): Connection = connection.connection as Connection

    private fun PreparedStatement.bind(params: List<Any?>): PreparedStatement {
        params.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> setObject(position, null)
                is LocalDateTime -> setTimestamp(position, Timestamp.valueOf(value))
                is LocalDate -> setObject(position, java.sql.Date.valueOf(value))
                is BigDecimal -> setBigDecimal(position, value)
                is Double -> setBigDecimal(position, BigDecimal.valueOf(value))
                is Boolean -> setInt(position, if (value) 1 else 0)
                else -> setObject(position, value)
            }
        }
        return this
    }
}

/**
 * Formatea un correlativo según el `formato` de `correlativos` (equivale al
 * `substr_replace(formato, contador, -strlen(contador))` del web): el contador reemplaza los
 * caracteres de la derecha del formato, conservando prefijos como `0000-`.
 */
fun formatCorrelativo(
    value: Long,
    formato: String,
): String {
    val digits = value.toString()
    if (formato.length <= digits.length) return digits
    return formato.substring(0, formato.length - digits.length) + digits
}
