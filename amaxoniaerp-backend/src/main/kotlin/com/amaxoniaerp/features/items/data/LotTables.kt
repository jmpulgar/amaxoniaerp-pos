package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import java.math.BigDecimal

/** Configuracion de lote por item (si el producto maneja lotes) */
object ConfiguracionLoteTable : Table("configuracion_lote") {
    val id = integer("id").autoIncrement()
    val idItem = integer("id_item")
    val habilitado = integer("habilitado").default(1)
    val loteVence = varchar("lote_vence", SchemaDimensions.VARCHAR_LENGTH_20).default("si")

    override val primaryKey = PrimaryKey(id)
}

/** Lotes disponibles por item */
object ItemLoteTable : Table("item_lote") {
    val idLoteItem = integer("id_lote_item").autoIncrement()
    val codAlmacen = integer("cod_almacen")
    val idItem = integer("id_item")
    val codigoLoteItem = varchar("codigo_lote_item", SchemaDimensions.VARCHAR_LENGTH_100)
    val vencimiento = date("vencimiento").nullable()
    val disponibilidad = decimal("disponibilidad", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(BigDecimal.ZERO)
    val procesamiento = decimal("procesamiento", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(BigDecimal.ZERO)
    val venta = decimal("venta", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(BigDecimal.ZERO)

    override val primaryKey = PrimaryKey(idLoteItem)
}

/** Trazabilidad por lote en factura_detalle */
object FacturaDetalleProductoLoteTable : Table("factura_detalle_producto_lote") {
    val id = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idDetalleFactura = varchar("id_detalle_factura", SchemaDimensions.VARCHAR_LENGTH_36)
    val idItem = integer("id_item")
    val idLoteItem = integer("id_lote_item")
    val cantidad = integer("cantidad")

    override val primaryKey = PrimaryKey(id)
}
