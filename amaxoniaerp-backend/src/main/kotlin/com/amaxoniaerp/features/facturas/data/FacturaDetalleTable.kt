package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

/**
 * Tabla factura_detalle. Campos mínimos para best sellers.
 */
object FacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val idItem = integer("id_item").nullable()
    val itemCantidad = decimal("_item_cantidad", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}
