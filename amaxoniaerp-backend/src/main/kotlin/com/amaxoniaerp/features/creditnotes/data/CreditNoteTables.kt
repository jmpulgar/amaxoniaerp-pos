package com.amaxoniaerp.features.creditnotes.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import com.amaxoniaerp.core.database.SchemaDimensions as S

/**
 * Columnas comunes de `factura_devolucion` en VE y PA.
 *
 * Columnas exclusivas de VE → [CreditNoteHeaderTableVE]:
 *   nroz, impresoraSerial (fiscalidad VE)
 *
 * Columnas exclusivas de PA → [CreditNoteHeaderTablePA]:
 *   cufe, qr, fechaRecepcionDGI, nroProtocoloAutorizacion, fechaLimite,
 *   tipoDocumento, naturalezaOperacion, tipoOperacion, formatoCAFE,
 *   entregaCAFE, envioContenedor, tipoVenta, informacionInteres,
 *   descuentoGlobalVenta (factura electrónica DGI Panamá)
 */
abstract class BaseCreditNoteHeaderTable(
    name: String = "factura_devolucion",
) : Table(name) {
    val idDevolucion = varchar("id_devolucion", S.VARCHAR_LENGTH_36)
    val codDevolucion = varchar("cod_devolucion", S.VARCHAR_LENGTH_32)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_36)
    val fechaDevolucion = date("fecha_devolucion")
    val codDevolucionFiscal = varchar("cod_devolucion_fiscal", S.VARCHAR_LENGTH_20).nullable()
    val observacion = varchar("observacion", S.VARCHAR_LENGTH_300).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val codVendedor = integer("cod_vendedor")
    val fechaFactura = date("fecha_factura").nullable()
    val subtotal = decimal("subtotal", S.DECIMAL_PRECISION_20, 2)
    val impuesto = decimal("impuesto", S.DECIMAL_PRECISION_20, 2)
    val total = decimal("total", S.DECIMAL_PRECISION_20, 2)
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_50)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val periodoDevolucion = varchar("periodo_devolucion", S.VARCHAR_LENGTH_20).nullable()
    val contabilizado = integer("contabilizado").default(0)
    val numcomContabilizado = integer("numcom_contabilizado").default(0)
    val fechaContabilizado = date("fecha_contabilizado").nullable()
    val idCajaSecuencia = varchar("id_caja_secuencia", S.VARCHAR_LENGTH_36).nullable()
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10).nullable()
    val cajaSecuencia = varchar("caja_secuencia", S.VARCHAR_LENGTH_10).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36).nullable()
    val codigoCaja = varchar("codigo_caja", S.VARCHAR_LENGTH_50).nullable()
    val codCliente = varchar("cod_cliente", S.VARCHAR_LENGTH_80).nullable()
    val descuentoGlobal = decimal("descuento_global", S.DECIMAL_PRECISION_20, 2).nullable()
    val pdescuentoGlobal = decimal("pdescuento_global", S.DECIMAL_PRECISION_20, 2).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", S.VARCHAR_LENGTH_20).nullable()
    val registroMigrado = integer("registro_migrado").default(0)

    override val primaryKey = PrimaryKey(idDevolucion)
}

/** Venezuela: campos de impresora fiscal VE (nroz / impresora_serial). */
object CreditNoteHeaderTableVE : BaseCreditNoteHeaderTable() {
    val nroz = varchar("nroz", S.VARCHAR_LENGTH_20).nullable()
    val impresoraSerial = varchar("impresora_serial", S.VARCHAR_LENGTH_50).nullable()
}

/**
 * Panamá: campos de factura electrónica DGI.
 * `cufe` y `qr` son `text` en ambos esquemas pero no existen en VE.
 */
object CreditNoteHeaderTablePA : BaseCreditNoteHeaderTable() {
    val tipoDocumento = varchar("tipoDocumento", 2).default("04")
    val naturalezaOperacion = varchar("NaturalezaOperacion", 2).default("01")
    val tipoOperacion = integer("tipoOperacion").default(1)
    val formatoCAFE = integer("formatoCAFE").default(1)
    val entregaCAFE = integer("entregaCAFE").default(1)
    val envioContenedor = integer("envioContenedor").default(1)
    val tipoVenta = integer("tipoVenta").default(1)
    val informacionInteres = varchar("informacionInteres", S.VARCHAR_LENGTH_5000).default("")
    val cufe = text("cufe").default("")
    val qr = text("qr").default("")
    val fechaRecepcionDGI = datetime("fechaRecepcionDGI").nullable()
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", S.VARCHAR_LENGTH_200).default("")
    val fechaLimite = datetime("fechaLimite").nullable()
    val descuentoGlobalVenta = decimal("descuento_global_venta", S.DECIMAL_PRECISION_20, 2).nullable()
}

/** Devuelve la tabla correcta según el país. */
object CreditNoteHeaderTableFactory {
    fun forCountry(countryCode: String): BaseCreditNoteHeaderTable =
        when (countryCode.uppercase()) {
            "VE" -> CreditNoteHeaderTableVE
            "PA" -> CreditNoteHeaderTablePA
            else -> CreditNoteHeaderTableVE
        }
}

object CreditNoteDetailTable : Table("factura_devolucion_detalle") {
    val idDevolucionDetalle = varchar("id_devolucion_detalle", S.VARCHAR_LENGTH_36)
    val idDevolucion = varchar("id_devolucion", S.VARCHAR_LENGTH_36)
    val idDetalleFactura = varchar("id_detalle_factura", S.VARCHAR_LENGTH_36)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemCantidad = decimal("_item_cantidad", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val itemPrecioSinIva = decimal("_item_preciosiniva", S.DECIMAL_PRECISION_20, 2)
    val itemDescuento = decimal("_item_descuento", S.DECIMAL_PRECISION_10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", S.DECIMAL_PRECISION_20, 2)
    val itemPIva = decimal("_item_piva", S.DECIMAL_PRECISION_10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", S.DECIMAL_PRECISION_20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", S.DECIMAL_PRECISION_20, 2)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", S.VARCHAR_LENGTH_50)
    val itemReferencia = varchar("_item_referencia", S.VARCHAR_LENGTH_50)

    override val primaryKey = PrimaryKey(idDevolucionDetalle)
}

object CreditNoteFacturaTable : Table("factura") {
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", S.VARCHAR_LENGTH_20).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", S.VARCHAR_LENGTH_20).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val fechaFactura = date("fechaFactura").nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val subtotal = decimal("subtotal", S.DECIMAL_PRECISION_20, 2)
    val totalizarSubTotal = decimal("totalizar_sub_total", S.DECIMAL_PRECISION_20, 2)
    val totalizarTotalOperacion = decimal("totalizar_total_operacion", S.DECIMAL_PRECISION_20, 2)
    val totalizarPDescuentoGlobal = decimal("totalizar_pdescuento_global", S.DECIMAL_PRECISION_20, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", S.DECIMAL_PRECISION_20, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", S.DECIMAL_PRECISION_20, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", S.DECIMAL_PRECISION_20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", S.DECIMAL_PRECISION_20, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", S.DECIMAL_PRECISION_20, 2)
    val formaPago = varchar("formapago", S.VARCHAR_LENGTH_20)
    val idCajaSecuencia = varchar("id_caja_secuencia", S.VARCHAR_LENGTH_36)
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal")
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10)
    val codigoCaja = varchar("codigo_caja", S.VARCHAR_LENGTH_50)
    val facturarA = varchar("facturar_a", S.VARCHAR_LENGTH_80)
    val facturarARuc = varchar("facturar_a_ruc", S.VARCHAR_LENGTH_50)
    val facturarADireccion = varchar("facturar_a_direccion", S.VARCHAR_LENGTH_250)
    val facturarATelefono = varchar("facturar_a_telefono", S.VARCHAR_LENGTH_50)
    val abrMonedaBase = varchar("abr_moneda_base", S.VARCHAR_LENGTH_10).nullable()
    val tasa = decimal("tasa", S.DECIMAL_PRECISION_20, S.DECIMAL_SCALE_4).nullable()
    val totalRef = decimal("total_ref", S.DECIMAL_PRECISION_20, 2).nullable()

    override val primaryKey = PrimaryKey(idFactura)
}

object CreditNoteFacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemDescripcion = varchar("_item_descripcion", S.VARCHAR_LENGTH_500)
    val itemCantidad = decimal("_item_cantidad", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val itemPrecioSinIva = decimal("_item_preciosiniva", S.DECIMAL_PRECISION_20, 2)
    val itemDescuento = decimal("_item_descuento", S.DECIMAL_PRECISION_10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", S.DECIMAL_PRECISION_20, 2)
    val itemPIva = decimal("_item_piva", S.DECIMAL_PRECISION_10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", S.DECIMAL_PRECISION_20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", S.DECIMAL_PRECISION_20, 2)
    val itemCantidadTotal = decimal("_item_cantidad_total", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", S.VARCHAR_LENGTH_50).nullable()
    val itemReferencia = varchar("_item_referencia", S.VARCHAR_LENGTH_50).nullable()
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

object CreditNoteCajaTable : Table("caja") {
    val idCaja = varchar("id", S.VARCHAR_LENGTH_36)
    val codigo = varchar("codigo", S.VARCHAR_LENGTH_50).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val serieCaja = varchar("serie_caja", S.VARCHAR_LENGTH_10).nullable()
    val impresoraModelo = varchar("impresora_modelo", S.VARCHAR_LENGTH_50).nullable()
    val notacreditoCorrelativo = integer("notacredito_correlativo").nullable()
    val abonoCorrelativo = integer("abono_correlativo").nullable()
    val certificadoCorrelativo = integer("certificado_correlativo").nullable()

    override val primaryKey = PrimaryKey(idCaja)
}

object CreditNoteCajaSecuenciaTable : Table("caja_secuencia") {
    val idCajaSecuencia = varchar("id", S.VARCHAR_LENGTH_36)
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val secuencia = varchar("secuencia", S.VARCHAR_LENGTH_10).nullable()
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10).nullable()

    override val primaryKey = PrimaryKey(idCajaSecuencia)
}

object CreditNoteAbonoTable : Table("abono") {
    val idAbono = varchar("id", S.VARCHAR_LENGTH_36)
    val codAbono = varchar("codigo", S.VARCHAR_LENGTH_50)
    val fecha = datetime("fecha_emision")
    val vencimiento = integer("vencimiento")
    val fechaVencimiento = datetime("fecha_vencimiento")
    val idVendedor = integer("id_vendedor")
    val idCajero = integer("id_cajero")
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val idCajaSecuencia = varchar("id_caja_secuencia", S.VARCHAR_LENGTH_36)
    val monto = decimal("monto", S.DECIMAL_PRECISION_20, 2)
    val saldo = decimal("saldo", S.DECIMAL_PRECISION_20, 2)
    val estatus = integer("estatus")
    val descripcion = text("descripcion")
    val observacion = text("observacion")
    val tipo = varchar("tipo", S.VARCHAR_LENGTH_20)
    val idOperacion = varchar("id_operacion", S.VARCHAR_LENGTH_36)
    val codigoReparacion = varchar("codigo_reparacion", S.VARCHAR_LENGTH_50)
    val fechaCreacion = datetime("fecha_creacion")
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_50)
    val fechaModificacion = datetime("fecha_modificacion")
    val usuarioModificacion = varchar("usuario_modificacion", S.VARCHAR_LENGTH_50)
    val fechaAnulacion = datetime("fecha_anulacion")
    val usuarioAnulacion = varchar("usuario_anulacion", S.VARCHAR_LENGTH_50)
    val idTransaccion = varchar("id_transaccion", S.VARCHAR_LENGTH_36)
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10)
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)

    override val primaryKey = PrimaryKey(idAbono)
}

object CreditNoteGiftCertificateTable : Table("certificado_regalo") {
    val idCertificado = varchar("id_certificado_regalo", S.VARCHAR_LENGTH_36)
    val codigo = varchar("codigo", S.VARCHAR_LENGTH_32)
    val monto = decimal("monto", S.DECIMAL_PRECISION_20, 2)
    val saldo = decimal("saldo", S.DECIMAL_PRECISION_20, 2)
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val estatus = integer("estatus")
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_50)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val idTransaccion = varchar("id_transaccion", S.VARCHAR_LENGTH_36).nullable()

    override val primaryKey = PrimaryKey(idCertificado)
}
