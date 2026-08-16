package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

object ItemExistenciaAlmacenTable : Table("item_existencia_almacen") {
    val idItem = integer("id_item")
    val codAlmacen = integer("cod_almacen")
    val cantidad = decimal("cantidad", 18, 4).nullable()
    val cantidadMuestra = decimal("cantidad_muestra", 18, 4).nullable()
    val minimo = decimal("minimo", 18, 4).nullable()
    val maximo = decimal("maximo", 18, 4).nullable()
}

object ItemPrecompromisoTable : Table("item_precompromiso") {
    val idItem = integer("id_item")
    val idAlmacen = integer("id_almacen")
    val cantidad = decimal("cantidad", 18, 4).nullable()
}

object AlmacenTable : Table("almacen") {
    val codAlmacen = integer("cod_almacen")
    val descripcion = varchar("descripcion", 120).nullable()
    val tipo = varchar("tipo", 40).nullable()
    val orden = integer("orden").nullable()
}
