package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

object ItemExistenciaAlmacenTable : Table("item_existencia_almacen") {
    val idItem = integer("id_item")
    val codAlmacen = integer("cod_almacen")
    val cantidad = decimal("cantidad", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).nullable()
    val cantidadMuestra = decimal("cantidad_muestra", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).nullable()
    val minimo = decimal("minimo", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).nullable()
    val maximo = decimal("maximo", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).nullable()
}

object ItemPrecompromisoTable : Table("item_precompromiso") {
    val idItem = integer("id_item")
    val idAlmacen = integer("id_almacen")
    val cantidad = decimal("cantidad", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4).nullable()
}

object AlmacenTable : Table("almacen") {
    val codAlmacen = integer("cod_almacen")
    val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_120).nullable()
    val tipo = varchar("tipo", S.VARCHAR_LENGTH_40).nullable()
    val orden = integer("orden").nullable()
}
