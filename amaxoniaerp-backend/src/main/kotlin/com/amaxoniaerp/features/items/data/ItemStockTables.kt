package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_CANTIDAD_MUESTRA_PRECISION = 18
private const val SCHEMA_CANTIDAD_MUESTRA_SCALE = 4
private const val SCHEMA_CANTIDAD_PRECISION = 18
private const val SCHEMA_CANTIDAD_SCALE = 4
private const val SCHEMA_DESCRIPCION_MAX_LENGTH = 120
private const val SCHEMA_MAXIMO_PRECISION = 18
private const val SCHEMA_MAXIMO_SCALE = 4
private const val SCHEMA_MINIMO_PRECISION = 18
private const val SCHEMA_MINIMO_SCALE = 4
private const val SCHEMA_TIPO_MAX_LENGTH = 40

object ItemExistenciaAlmacenTable : Table("item_existencia_almacen") {
    val idItem = integer("id_item")
    val codAlmacen = integer("cod_almacen")
    val cantidad = decimal("cantidad", SCHEMA_CANTIDAD_PRECISION, SCHEMA_CANTIDAD_SCALE).nullable()
    val cantidadMuestra = decimal("cantidad_muestra", SCHEMA_CANTIDAD_MUESTRA_PRECISION, SCHEMA_CANTIDAD_MUESTRA_SCALE).nullable()
    val minimo = decimal("minimo", SCHEMA_MINIMO_PRECISION, SCHEMA_MINIMO_SCALE).nullable()
    val maximo = decimal("maximo", SCHEMA_MAXIMO_PRECISION, SCHEMA_MAXIMO_SCALE).nullable()
}

object ItemPrecompromisoTable : Table("item_precompromiso") {
    val idItem = integer("id_item")
    val idAlmacen = integer("id_almacen")
    val cantidad = decimal("cantidad", SCHEMA_CANTIDAD_PRECISION, SCHEMA_CANTIDAD_SCALE).nullable()
}

object AlmacenTable : Table("almacen") {
    val codAlmacen = integer("cod_almacen")
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH).nullable()
    val tipo = varchar("tipo", SCHEMA_TIPO_MAX_LENGTH).nullable()
    val orden = integer("orden").nullable()
}
