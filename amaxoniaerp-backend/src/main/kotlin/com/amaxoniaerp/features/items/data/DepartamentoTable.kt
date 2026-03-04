package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

/**
 * Tabla departamento. Relación: item.departamento_id = departamento.id
 */
object DepartamentoTable : Table("departamento") {
    val id = integer("id").autoIncrement()
    val codigo = varchar("codigo", 10).nullable()
    val descripcion = varchar("descripcion", 100).nullable()
    val visible = bool("visible").default(true)

    override val primaryKey = PrimaryKey(id)
}
