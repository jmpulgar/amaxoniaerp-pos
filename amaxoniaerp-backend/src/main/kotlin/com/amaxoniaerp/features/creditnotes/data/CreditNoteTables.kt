package com.amaxoniaerp.features.creditnotes.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object CreditNoteHeaderTable : Table("factura_devolucion") {
    val idDevolucion = varchar("id_devolucion", 36)
    val codDevolucion = varchar("cod_devolucion", 32)
    val codFactura = varchar("cod_factura", 36)
    val fechaDevolucion = date("fecha_devolucion")
    val codDevolucionFiscal = varchar("cod_devolucion_fiscal", 20).nullable()
    val nroz = varchar("nroz", 20).nullable()
    val impresoraSerial = varchar("impresora_serial", 50).nullable()
    val observacion = varchar("observacion", 300).nullable()
    val idCliente = varchar("id_cliente", 36)
    val codVendedor = integer("cod_vendedor")
    val fechaFactura = date("fecha_factura").nullable()
    val subtotal = decimal("subtotal", 20, 2)
    val impuesto = decimal("impuesto", 20, 2)
    val total = decimal("total", 20, 2)
    val usuarioCreacion = varchar("usuario_creacion", 50)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val periodoDevolucion = varchar("periodo_devolucion", 20).nullable()
    val contabilizado = integer("contabilizado").default(0)
    val numcomContabilizado = integer("numcom_contabilizado").default(0)
    val fechaContabilizado = date("fecha_contabilizado").nullable()
    val idCajaSecuencia = varchar("id_caja_secuencia", 36).nullable()
    val serieSucursal = varchar("serie_sucursal", 10).nullable()
    val cajaSecuencia = varchar("caja_secuencia", 10).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val idCaja = varchar("id_caja", 36).nullable()
    val codigoCaja = varchar("codigo_caja", 50).nullable()
    val codCliente = varchar("cod_cliente", 80).nullable()
    val descuentoGlobal = decimal("descuento_global", 20, 2).nullable()
    val pdescuentoGlobal = decimal("pdescuento_global", 20, 2).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()

    override val primaryKey = PrimaryKey(idDevolucion)
}

object CreditNoteDetailTable : Table("factura_devolucion_detalle") {
    val idDevolucionDetalle = varchar("id_devolucion_detalle", 36)
    val idDevolucion = varchar("id_devolucion", 36)
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val itemPrecioSinIva = decimal("_item_preciosiniva", 20, 2)
    val itemDescuento = decimal("_item_descuento", 10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", 20, 2)
    val itemPIva = decimal("_item_piva", 10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", 20, 2)
    val codVendedor = integer("cod_vendedor").nullable()
    val itemCodigo = varchar("_item_codigo", 50).nullable()
    val itemReferencia = varchar("_item_referencia", 50).nullable()

    override val primaryKey = PrimaryKey(idDevolucionDetalle)
}

object CreditNoteFacturaTable : Table("factura") {
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", 20).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    val idCliente = varchar("id_cliente", 36)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val fechaFactura = date("fechaFactura").nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val subtotal = decimal("subtotal", 20, 2)
    val totalizarSubTotal = decimal("totalizar_sub_total", 20, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", 20, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", 20, 2)
    val formaPago = varchar("formapago", 20)
    val idCajaSecuencia = varchar("id_caja_secuencia", 36)
    val idCaja = varchar("id_caja", 36)
    val idSucursal = integer("id_sucursal")
    val serieSucursal = varchar("serie_sucursal", 10)
    val codigoCaja = varchar("codigo_caja", 50)
    val facturarA = varchar("facturar_a", 80)
    val facturarARuc = varchar("facturar_a_ruc", 50)
    val facturarADireccion = varchar("facturar_a_direccion", 250)
    val facturarATelefono = varchar("facturar_a_telefono", 50)
    val abrMonedaBase = varchar("abr_moneda_base", 10).nullable()

    override val primaryKey = PrimaryKey(idFactura)
}

object CreditNoteFacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idFactura = varchar("id_factura", 36)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemDescripcion = varchar("_item_descripcion", 500)
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val itemPrecioSinIva = decimal("_item_preciosiniva", 20, 2)
    val itemDescuento = decimal("_item_descuento", 10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", 20, 2)
    val itemPIva = decimal("_item_piva", 10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", 20, 2)
    val itemCantidadTotal = decimal("_item_cantidad_total", 32, 3)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", 50).nullable()
    val itemReferencia = varchar("_item_referencia", 50).nullable()
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

object CreditNoteCajaTable : Table("caja") {
    val idCaja = varchar("id", 36)
    val codigo = varchar("codigo", 50).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val serieCaja = varchar("serie_caja", 10).nullable()
    val impresoraModelo = varchar("impresora_modelo", 50).nullable()
    val notacreditoCorrelativo = integer("notacredito_correlativo").nullable()
    val abonoCorrelativo = integer("abono_correlativo").nullable()
    val certificadoCorrelativo = integer("certificado_correlativo").nullable()

    override val primaryKey = PrimaryKey(idCaja)
}

object CreditNoteCajaSecuenciaTable : Table("caja_secuencia") {
    val idCajaSecuencia = varchar("id", 36)
    val idCaja = varchar("id_caja", 36)
    val secuencia = varchar("secuencia", 10).nullable()
    val serieSucursal = varchar("serie_sucursal", 10).nullable()

    override val primaryKey = PrimaryKey(idCajaSecuencia)
}

object CreditNoteAbonoTable : Table("abono") {
    val idAbono = varchar("id_abono", 36)
    val codAbono = varchar("cod_abono", 32)
    val fecha = date("fecha")
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val usuarioCreacion = varchar("usuario_creacion", 50)
    val idCliente = varchar("id_cliente", 36)
    val idCaja = varchar("id_caja", 36)
    val monto = decimal("monto", 20, 2)
    val saldo = decimal("saldo", 20, 2)
    val estatus = integer("estatus")
    val descripcion = varchar("descripcion", 300).nullable()
    val tipo = varchar("tipo", 50).nullable()
    val idOperacion = varchar("id_operacion", 36).nullable()

    override val primaryKey = PrimaryKey(idAbono)
}

object CreditNoteGiftCertificateTable : Table("certificado_regalo") {
    val idCertificado = varchar("id_certificado_regalo", 36)
    val codigo = varchar("codigo", 32)
    val monto = decimal("monto", 20, 2)
    val saldo = decimal("saldo", 20, 2)
    val idCliente = varchar("id_cliente", 36)
    val idCaja = varchar("id_caja", 36)
    val estatus = integer("estatus")
    val usuarioCreacion = varchar("usuario_creacion", 50)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val idTransaccion = varchar("id_transaccion", 36).nullable()

    override val primaryKey = PrimaryKey(idCertificado)
}
