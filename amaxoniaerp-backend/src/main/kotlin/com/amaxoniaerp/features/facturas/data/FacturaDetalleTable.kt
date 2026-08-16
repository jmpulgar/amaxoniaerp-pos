package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ITEM_CANTIDAD_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_SCALE = 3

/**
 * Tabla factura_detalle. Campos mínimos para best sellers.
 */
object FacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item").nullable()
    val itemCantidad = decimal("_item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}
