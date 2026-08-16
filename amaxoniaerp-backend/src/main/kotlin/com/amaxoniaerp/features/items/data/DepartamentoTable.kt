package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

/**
 * Tabla departamento. Relación: item.departamento_id = departamento.id
 */
object DepartamentoTable : Table("departamento") {
    val id = integer("id").autoIncrement()
    val codigo = varchar("codigo", SchemaDimensions.VARCHAR_LENGTH_10).nullable()
    val descripcion = varchar("descripcion", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val visible = bool("visible").default(true)

    override val primaryKey = PrimaryKey(id)
}
