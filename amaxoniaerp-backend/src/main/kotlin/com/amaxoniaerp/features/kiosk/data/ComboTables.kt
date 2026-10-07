package com.amaxoniaerp.features.kiosk.data

import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal
import com.amaxoniaerp.core.database.SchemaDimensions as S

/*
 * Proyección de solo lectura del modelo de combos del ERP (módulo Combos y ficha del producto
 * del administrativo, db/2026/2026-10-01-RECETA-COMBO-VARIANTES.sql). El kiosco no crea ni
 * modifica estas tablas. Solo se consultan con `select` explícito tras comprobar con
 * [KioskSchemaInspector.hasComboModel] que el tenant tiene las columnas nuevas; `grupos.orden`
 * es opcional (hay bases sin ella) y se lee únicamente si existe.
 */

/** Producto que al venderse abre un combo (`item_combos`). */
object ComboItemTable : Table("item_combos") {
    val idItemCombo = integer("id_item_combo")
    val idItem = integer("id_item")
    val idCombo = integer("id_combo")

    override val primaryKey = PrimaryKey(idItemCombo)
}

/** Pasos del combo (`grupos`): INCLUIDO, COMBO o MODIFICADOR. */
object ComboGroupTable : Table("grupos") {
    val idGrupo = integer("id_grupo")
    val idCombo = integer("id_combo")
    val nombreGrupo = varchar("nombre_grupo", S.VARCHAR_LENGTH_255)
    val adiciona = integer("adiciona").default(0)
    val maximoVeces = integer("maximo_veces").default(1)
    val tipo = varchar("tipo", 12).default(KIOSK_COMBO_DEFAULT_TIPO)
    val minimo = integer("minimo").default(0)
    val orden = integer("orden").nullable()

    override val primaryKey = PrimaryKey(idGrupo)
}

/** Opciones de cada paso (`grupo_items`); sin `id_item` es una opción de texto ("SIN CEBOLLA"). */
object ComboGroupItemTable : Table("grupo_items") {
    val idGrupoItem = integer("id_grupo_item")
    val idGrupo = integer("id_grupo")
    val idItem = integer("id_item").nullable()
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_150).nullable()
    val precio = decimal("precio", 12, 2).nullable()
    val cantidad = decimal("cantidad", 12, 2).default(BigDecimal.ONE)
    val porDefecto = integer("por_defecto").default(0)
    val orden = integer("orden").default(0)

    override val primaryKey = PrimaryKey(idGrupoItem)
}

private const val KIOSK_COMBO_DEFAULT_TIPO = "COMBO"
