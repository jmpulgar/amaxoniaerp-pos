package com.amaxoniaerp.features.geography.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.geography.domain.AddressLevelCatalog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

class GeographyRepository {
    suspend fun listCatalog(
        database: Database,
        tableName: String,
        limit: Int,
        offset: Long,
        includeTotal: Boolean,
    ): Pair<List<JsonObject>, Long> = dbQuery(database) {
        val total = if (includeTotal) countRows(tableName) else -1L
        val data = fetchRows(tableName, limit, offset)
        data to total
    }

    suspend fun listAddressLevels(
        database: Database,
        tableName: String,
        limit: Int,
        offset: Long,
        includeTotal: Boolean,
    ): Pair<List<AddressLevelCatalog>, Long> = dbQuery(database) {
        val total = if (includeTotal) countRows(tableName) else -1L
        val data = fetchAddressLevelRows(tableName, limit, offset)
        data to total
    }

    private fun countRows(tableName: String): Long {
        val sql = "select count(*) from $tableName"
        return TransactionManager.current().exec(sql) { result ->
            if (result.next()) result.getLong(1) else 0L
        } ?: 0L
    }

    private fun fetchRows(tableName: String, limit: Int, offset: Long): List<JsonObject> {
        val sql = "select * from $tableName order by 1 limit $limit offset $offset"
        return TransactionManager.current().exec(sql) { result ->
            val rows = mutableListOf<JsonObject>()
            while (result.next()) {
                rows.add(mapRow(result))
            }
            rows
        } ?: emptyList()
    }

    private fun mapRow(result: ResultSet): JsonObject {
        val meta = result.metaData
        val values = LinkedHashMap<String, JsonElement>(meta.columnCount)
        for (index in 1..meta.columnCount) {
            val key = meta.getColumnLabel(index)
            val value = result.getObject(index)
            values[key] = toJsonElement(value)
        }
        return JsonObject(values)
    }

    private fun fetchAddressLevelRows(tableName: String, limit: Int, offset: Long): List<AddressLevelCatalog> {
        val sql = "select codigo_pais, codigo, denominacion from $tableName " +
            "order by codigo_pais, codigo limit $limit offset $offset"
        return TransactionManager.current().exec(sql) { result ->
            val rows = mutableListOf<AddressLevelCatalog>()
            while (result.next()) {
                rows.add(
                    AddressLevelCatalog(
                        countryCode = result.getString("codigo_pais") ?: "",
                        code = result.getString("codigo") ?: "",
                        name = result.getString("denominacion") ?: "",
                    )
                )
            }
            rows
        } ?: emptyList()
    }

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is java.sql.Date -> JsonPrimitive(value.toString())
            is java.sql.Timestamp -> JsonPrimitive(value.toString())
            is java.time.LocalDate -> JsonPrimitive(value.toString())
            is java.time.LocalDateTime -> JsonPrimitive(value.toString())
            else -> JsonPrimitive(value.toString())
        }
    }
}
