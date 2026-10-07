package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

/**
 * Tabla departamento. Relación: item.departamento_id = departamento.id
 */
object DepartamentoTable : Table("departamento") {
    val id = integer("id").autoIncrement()
    val codigo = varchar("codigo", S.VARCHAR_LENGTH_10).nullable()
    val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_100).nullable()
    val visible = bool("visible").default(true)
    val visiblePos = flexibleInt("visible_pos").default(1)

    /** Foto propia del departamento, p. ej. "fotos/21_foto.png" (archivo en {data}/departamento/21_foto.png). */
    val foto = varchar("foto", S.VARCHAR_LENGTH_60).nullable()

    override val primaryKey = PrimaryKey(id)
}
