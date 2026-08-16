package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

/**
 * Tabla factura_detalle. Campos mínimos para best sellers.
 */
object FacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", SchemaDimensions.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", SchemaDimensions.VARCHAR_LENGTH_36)
    val idItem = integer("id_item").nullable()
    val itemCantidad = decimal("_item_cantidad", SchemaDimensions.DECIMAL_PRECISION_32, SchemaDimensions.DECIMAL_SCALE_3)
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}
