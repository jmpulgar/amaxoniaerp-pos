package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.SchemaDimensions as S
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * Grupos de modificadores (ej. Tamaño, Término, Extras, Bebidas).
 */
object ItemModifierGroupTable : Table("item_modificador_grupo") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_80)
    val minSeleccion = integer("min_seleccion").default(0)
    val maxSeleccion = integer("max_seleccion").default(1)
    val esObligatorio = bool("es_obligatorio").default(false)
    val esCombo = bool("es_combo").default(false)
    val orden = integer("orden").default(0)
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Opciones dentro de un grupo de modificadores (ej. Con queso, Sin cebolla, Doble carne).
 */
object ItemModifierTable : Table("item_modificador") {
    val id = integer("id").autoIncrement()
    val idGrupo = integer("id_grupo").references(ItemModifierGroupTable.id, onDelete = ReferenceOption.CASCADE)
    val idItemAsociado = integer("id_item_asociado").nullable()
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_80)
    val precioAdicional = decimal("precio_adicional", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).default(0.toBigDecimal())
    val orden = integer("orden").default(0)
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_item_modificador_grupo", false, idGrupo)
    }
}

/**
 * Relación entre un producto del catálogo (item.id_item) y los grupos de modificadores asignados.
 */
object ItemModifierRelationTable : Table("item_modificador_relacion") {
    val idItem = integer("id_item")
    val idGrupo = integer("id_grupo").references(ItemModifierGroupTable.id, onDelete = ReferenceOption.CASCADE)
    val orden = integer("orden").default(0)

    override val primaryKey = PrimaryKey(idItem, idGrupo)

    init {
        index("ix_item_mod_rel_grupo", false, idGrupo)
    }
}
