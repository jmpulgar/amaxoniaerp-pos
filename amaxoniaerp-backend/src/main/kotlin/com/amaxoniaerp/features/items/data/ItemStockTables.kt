package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

object ItemExistenciaAlmacenTable : Table("item_existencia_almacen") {
    val idItem = integer("id_item")
    val codAlmacen = integer("cod_almacen")
    val cantidad = decimal("cantidad", SchemaDimensions.DECIMAL_PRECISION_18, SchemaDimensions.DECIMAL_SCALE_4).nullable()
    val cantidadMuestra = decimal("cantidad_muestra", SchemaDimensions.DECIMAL_PRECISION_18, SchemaDimensions.DECIMAL_SCALE_4).nullable()
    val minimo = decimal("minimo", SchemaDimensions.DECIMAL_PRECISION_18, SchemaDimensions.DECIMAL_SCALE_4).nullable()
    val maximo = decimal("maximo", SchemaDimensions.DECIMAL_PRECISION_18, SchemaDimensions.DECIMAL_SCALE_4).nullable()
}

object ItemPrecompromisoTable : Table("item_precompromiso") {
    val idItem = integer("id_item")
    val idAlmacen = integer("id_almacen")
    val cantidad = decimal("cantidad", SchemaDimensions.DECIMAL_PRECISION_18, SchemaDimensions.DECIMAL_SCALE_4).nullable()
}

object AlmacenTable : Table("almacen") {
    val codAlmacen = integer("cod_almacen")
    val descripcion = varchar("descripcion", SchemaDimensions.VARCHAR_LENGTH_120).nullable()
    val tipo = varchar("tipo", SchemaDimensions.VARCHAR_LENGTH_40).nullable()
    val orden = integer("orden").nullable()
}
