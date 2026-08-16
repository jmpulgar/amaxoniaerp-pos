package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table

/**
 * Tabla factura_detalle. Campos mínimos para best sellers.
 */
object FacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idFactura = varchar("id_factura", 36)
    val idItem = integer("id_item").nullable()
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}
