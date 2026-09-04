package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.math.RoundingMode

internal fun queryOne(sql: String): SqlRow? = queryMany(sql).firstOrNull()

internal fun queryMany(sql: String): List<SqlRow> =
    TransactionManager.current().exec(sql) { rs ->
        val meta = rs.metaData
        val columns = (1..meta.columnCount).map { index -> meta.getColumnLabel(index) }
        val rows = mutableListOf<SqlRow>()
        while (rs.next()) {
            rows += SqlRow(columns.associateWith { column -> rs.getObject(column) })
        }
        rows
    } ?: emptyList()

internal data class SqlRow(
    private val values: Map<String, Any?>,
) {
    private val lookup = values.mapKeys { it.key.lowercase() }

    fun string(column: String): String = stringOrNull(column).orEmpty()

    fun stringOrNull(column: String): String? = lookup[column.lowercase()]?.toString()?.takeIf { it.isNotBlank() }

    fun decimal(column: String): BigDecimal = decimalOrNull(column) ?: BigDecimal.ZERO

    fun decimalOrNull(column: String): BigDecimal? =
        when (val value = lookup[column.lowercase()]) {
            null -> null
            is BigDecimal -> value
            is Number -> BigDecimal.valueOf(value.toDouble())
            else -> value.toString().toBigDecimalOrNull()
        }
}

internal fun BigDecimal.toMoneyString(trimZeros: Boolean = false): String {
    val scaled = setScale(2, RoundingMode.HALF_UP)
    return if (trimZeros) scaled.stripTrailingZeros().toPlainString() else scaled.toPlainString()
}

internal fun String.sqlLiteral(): String = replace("'", "''")
