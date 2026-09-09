package com.amaxoniaerp.features.sync.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Change feed de catálogo (DDL: migrations/005_catalog_changes.sql).
 * Apéndice puro: los triggers del ERP insertan filas; esta capa solo lee y
 * poda. El payload se hidrata contra la fila fuente al servir el delta (§3.3).
 */
object CatalogChangesTable : Table("catalog_changes") {
    val changeId = long("change_id").autoIncrement()
    val entityType = varchar("entity_type", 32)
    val entityId = varchar("entity_id", 64)
    val op = varchar("op", 8)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(changeId)
}
