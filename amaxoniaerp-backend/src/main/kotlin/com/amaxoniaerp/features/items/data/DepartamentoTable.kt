package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_CODIGO_MAX_LENGTH = 10
private const val SCHEMA_DESCRIPCION_MAX_LENGTH = 100

/**
 * Tabla departamento. Relación: item.departamento_id = departamento.id
 */
object DepartamentoTable : Table("departamento") {
    val id = integer("id").autoIncrement()
    val codigo = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH).nullable()
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH).nullable()
    val visible = bool("visible").default(true)

    override val primaryKey = PrimaryKey(id)
}
