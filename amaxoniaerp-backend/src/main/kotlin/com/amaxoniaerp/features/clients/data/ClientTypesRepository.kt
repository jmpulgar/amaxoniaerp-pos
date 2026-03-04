package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.clients.domain.ClientTypeCatalog
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

class ClientTypesRepository {
    suspend fun listClientTypes(
        database: Database,
        limit: Int,
        offset: Long,
        includeTotal: Boolean,
    ): Pair<List<ClientTypeCatalog>, Long> = dbQuery(database) {
        val total = if (includeTotal) countRows("tipo_cliente") else -1L
        val data = fetchClientTypes(limit, offset)
        data to total
    }

    private fun countRows(tableName: String): Long {
        val sql = "select count(*) from $tableName"
        return TransactionManager.current().exec(sql) { result ->
            if (result.next()) result.getLong(1) else 0L
        } ?: 0L
    }

    private fun fetchClientTypes(limit: Int, offset: Long): List<ClientTypeCatalog> {
        val sql = "select * from tipo_cliente order by 1 limit $limit offset $offset"
        return TransactionManager.current().exec(sql) { result ->
            val meta = result.metaData
            val columns = (1..meta.columnCount).associateBy { meta.getColumnLabel(it).lowercase() }
            val idIndex = findColumnIndex(columns, "id", "cod_tipo_cliente", "codigo", "id_tipo_cliente")
            val descriptionIndex = findColumnIndex(columns, "descripcion", "description", "denominacion", "nombre")
            val feCodeIndex = findColumnIndex(columns, "tipoclientefe", "codigo_fe", "fe_code", "cod_fe", "fe")

            val rows = mutableListOf<ClientTypeCatalog>()
            while (result.next()) {
                val id = toInt(getObject(result, idIndex))
                val description = toStringValue(getObject(result, descriptionIndex)) ?: ""
                val feCode = toStringValue(getObject(result, feCodeIndex))
                rows.add(
                    ClientTypeCatalog(
                        id = id,
                        description = description,
                        feCode = feCode,
                    )
                )
            }
            rows
        } ?: emptyList()
    }

    private fun findColumnIndex(columns: Map<String, Int>, vararg names: String): Int? {
        for (name in names) {
            val index = columns[name.lowercase()]
            if (index != null) {
                return index
            }
        }
        return null
    }

    private fun getObject(result: java.sql.ResultSet, index: Int?): Any? {
        return index?.let { result.getObject(it) }
    }

    private fun toInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun toStringValue(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            else -> value.toString()
        }
    }
}
