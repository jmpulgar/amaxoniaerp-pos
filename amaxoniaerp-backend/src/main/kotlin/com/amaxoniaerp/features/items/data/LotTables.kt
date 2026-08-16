package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import java.math.BigDecimal

private const val SCHEMA_CODIGO_LOTE_ITEM_MAX_LENGTH = 100
private const val SCHEMA_DISPONIBILIDAD_PRECISION = 10
private const val SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_MAX_LENGTH = 36
private const val SCHEMA_LOTE_VENCE_MAX_LENGTH = 20
private const val SCHEMA_PROCESAMIENTO_PRECISION = 10
private const val SCHEMA_VENTA_PRECISION = 10

/** Configuracion de lote por item (si el producto maneja lotes) */
object ConfiguracionLoteTable : Table("configuracion_lote") {
    val id = integer("id").autoIncrement()
    val idItem = integer("id_item")
    val habilitado = integer("habilitado").default(1)
    val loteVence = varchar("lote_vence", SCHEMA_LOTE_VENCE_MAX_LENGTH).default("si")

    override val primaryKey = PrimaryKey(id)
}

/** Lotes disponibles por item */
object ItemLoteTable : Table("item_lote") {
    val idLoteItem = integer("id_lote_item").autoIncrement()
    val codAlmacen = integer("cod_almacen")
    val idItem = integer("id_item")
    val codigoLoteItem = varchar("codigo_lote_item", SCHEMA_CODIGO_LOTE_ITEM_MAX_LENGTH)
    val vencimiento = date("vencimiento").nullable()
    val disponibilidad = decimal("disponibilidad", SCHEMA_DISPONIBILIDAD_PRECISION, 2).default(BigDecimal.ZERO)
    val procesamiento = decimal("procesamiento", SCHEMA_PROCESAMIENTO_PRECISION, 2).default(BigDecimal.ZERO)
    val venta = decimal("venta", SCHEMA_VENTA_PRECISION, 2).default(BigDecimal.ZERO)

    override val primaryKey = PrimaryKey(idLoteItem)
}

/** Trazabilidad por lote en factura_detalle */
object FacturaDetalleProductoLoteTable : Table("factura_detalle_producto_lote") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item")
    val idLoteItem = integer("id_lote_item")
    val cantidad = integer("cantidad")

    override val primaryKey = PrimaryKey(id)
}
