package com.amaxoniaerp.features.creditnotes.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

private const val SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH = 10
private const val SCHEMA_CAJA_SECUENCIA_MAX_LENGTH = 10
private const val SCHEMA_CODIGO_CAJA_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_MAX_LENGTH_32 = 32
private const val SCHEMA_CODIGO_MAX_LENGTH_50 = 50
private const val SCHEMA_CODIGO_REPARACION_MAX_LENGTH = 50
private const val SCHEMA_COD_CLIENTE_MAX_LENGTH = 80
private const val SCHEMA_COD_DEVOLUCION_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_COD_DEVOLUCION_MAX_LENGTH = 32
private const val SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_COD_FACTURA_MAX_LENGTH_32 = 32
private const val SCHEMA_COD_FACTURA_MAX_LENGTH_36 = 36
private const val SCHEMA_DESCUENTO_GLOBAL_PRECISION = 20
private const val SCHEMA_DESCUENTO_GLOBAL_VENTA_PRECISION = 20
private const val SCHEMA_FACTURAR_A_DIRECCION_MAX_LENGTH = 250
private const val SCHEMA_FACTURAR_A_MAX_LENGTH = 80
private const val SCHEMA_FACTURAR_A_RUC_MAX_LENGTH = 50
private const val SCHEMA_FACTURAR_A_TELEFONO_MAX_LENGTH = 50
private const val SCHEMA_FORMAPAGO_MAX_LENGTH = 20
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH = 36
private const val SCHEMA_ID_CERTIFICADO_REGALO_MAX_LENGTH = 36
private const val SCHEMA_ID_CLIENTE_MAX_LENGTH = 36
private const val SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_DEVOLUCION_DETALLE_MAX_LENGTH = 36
private const val SCHEMA_ID_DEVOLUCION_MAX_LENGTH = 36
private const val SCHEMA_ID_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_MAX_LENGTH = 36
private const val SCHEMA_ID_OPERACION_MAX_LENGTH = 36
private const val SCHEMA_ID_TRANSACCION_MAX_LENGTH = 36
private const val SCHEMA_IMPRESORA_MODELO_MAX_LENGTH = 50
private const val SCHEMA_IMPRESORA_SERIAL_MAX_LENGTH = 50
private const val SCHEMA_IMPUESTO_PRECISION = 20
private const val SCHEMA_INFORMACION_INTERES_MAX_LENGTH = 5000
private const val SCHEMA_ITEM_CANTIDAD_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_SCALE = 3
private const val SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_TOTAL_SCALE = 3
private const val SCHEMA_ITEM_CODIGO_MAX_LENGTH = 50
private const val SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH = 500
private const val SCHEMA_ITEM_DESCUENTO_PRECISION = 10
private const val SCHEMA_ITEM_MONTODESCUENTO_PRECISION = 20
private const val SCHEMA_ITEM_PIVA_PRECISION = 10
private const val SCHEMA_ITEM_PRECIOSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_REFERENCIA_MAX_LENGTH = 50
private const val SCHEMA_ITEM_TOTALCONIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALSINIVA_PRECISION = 20
private const val SCHEMA_MONTO_PRECISION = 20
private const val SCHEMA_NROZ_MAX_LENGTH = 20
private const val SCHEMA_NRO_PROTOCOLO_AUTORIZACION_MAX_LENGTH = 200
private const val SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_OBSERVACION_MAX_LENGTH = 300
private const val SCHEMA_PDESCUENTO_GLOBAL_PRECISION = 20
private const val SCHEMA_PERIODO_DEVOLUCION_MAX_LENGTH = 20
private const val SCHEMA_SALDO_PRECISION = 20
private const val SCHEMA_SECUENCIA_MAX_LENGTH = 10
private const val SCHEMA_SERIE_CAJA_MAX_LENGTH = 10
private const val SCHEMA_SERIE_SUCURSAL_MAX_LENGTH = 10
private const val SCHEMA_SUBTOTAL_PRECISION = 20
private const val SCHEMA_TASA_PRECISION = 20
private const val SCHEMA_TASA_SCALE = 4
private const val SCHEMA_TIPO_MAX_LENGTH = 20
private const val SCHEMA_TOTALIZAR_BASE_IMPONIBLE_PRECISION = 20
private const val SCHEMA_TOTALIZAR_DESCUENTO_GLOBAL_PRECISION = 20
private const val SCHEMA_TOTALIZAR_MONTO_IVA_PRECISION = 20
private const val SCHEMA_TOTALIZAR_PDESCUENTO_GLOBAL_PRECISION = 20
private const val SCHEMA_TOTALIZAR_SUB_TOTAL_PRECISION = 20
private const val SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION = 20
private const val SCHEMA_TOTALIZAR_TOTAL_OPERACION_PRECISION = 20
private const val SCHEMA_TOTAL_PRECISION = 20
private const val SCHEMA_TOTAL_REF_PRECISION = 20
private const val SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION = 20
private const val SCHEMA_USUARIO_ANULACION_MAX_LENGTH = 50
private const val SCHEMA_USUARIO_CREACION_MAX_LENGTH = 50
private const val SCHEMA_USUARIO_MODIFICACION_MAX_LENGTH = 50

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
    val idDevolucion = varchar("id_devolucion", SCHEMA_ID_DEVOLUCION_MAX_LENGTH)
    val codDevolucion = varchar("cod_devolucion", SCHEMA_COD_DEVOLUCION_MAX_LENGTH)
    val codFactura = varchar("cod_factura", SCHEMA_COD_FACTURA_MAX_LENGTH_36)
    val fechaDevolucion = date("fecha_devolucion")
    val codDevolucionFiscal = varchar("cod_devolucion_fiscal", SCHEMA_COD_DEVOLUCION_FISCAL_MAX_LENGTH).nullable()
    val observacion = varchar("observacion", SCHEMA_OBSERVACION_MAX_LENGTH).nullable()
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val codVendedor = integer("cod_vendedor")
    val fechaFactura = date("fecha_factura").nullable()
    val subtotal = decimal("subtotal", SCHEMA_SUBTOTAL_PRECISION, 2)
    val impuesto = decimal("impuesto", SCHEMA_IMPUESTO_PRECISION, 2)
    val total = decimal("total", SCHEMA_TOTAL_PRECISION, 2)
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val periodoDevolucion = varchar("periodo_devolucion", SCHEMA_PERIODO_DEVOLUCION_MAX_LENGTH).nullable()
    val contabilizado = integer("contabilizado").default(0)
    val numcomContabilizado = integer("numcom_contabilizado").default(0)
    val fechaContabilizado = date("fecha_contabilizado").nullable()
    val idCajaSecuencia = varchar("id_caja_secuencia", SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH).nullable()
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH).nullable()
    val cajaSecuencia = varchar("caja_secuencia", SCHEMA_CAJA_SECUENCIA_MAX_LENGTH).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH).nullable()
    val codigoCaja = varchar("codigo_caja", SCHEMA_CODIGO_CAJA_MAX_LENGTH).nullable()
    val codCliente = varchar("cod_cliente", SCHEMA_COD_CLIENTE_MAX_LENGTH).nullable()
    val descuentoGlobal = decimal("descuento_global", SCHEMA_DESCUENTO_GLOBAL_PRECISION, 2).nullable()
    val pdescuentoGlobal = decimal("pdescuento_global", SCHEMA_PDESCUENTO_GLOBAL_PRECISION, 2).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH).nullable()
    val registroMigrado = integer("registro_migrado").default(0)

    override val primaryKey = PrimaryKey(idDevolucion)
}

/** Venezuela: campos de impresora fiscal VE (nroz / impresora_serial). */
object CreditNoteHeaderTableVE : BaseCreditNoteHeaderTable() {
    val nroz = varchar("nroz", SCHEMA_NROZ_MAX_LENGTH).nullable()
    val impresoraSerial = varchar("impresora_serial", SCHEMA_IMPRESORA_SERIAL_MAX_LENGTH).nullable()
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
    val informacionInteres = varchar("informacionInteres", SCHEMA_INFORMACION_INTERES_MAX_LENGTH).default("")
    val cufe = text("cufe").default("")
    val qr = text("qr").default("")
    val fechaRecepcionDGI = datetime("fechaRecepcionDGI").nullable()
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", SCHEMA_NRO_PROTOCOLO_AUTORIZACION_MAX_LENGTH).default("")
    val fechaLimite = datetime("fechaLimite").nullable()
    val descuentoGlobalVenta = decimal("descuento_global_venta", SCHEMA_DESCUENTO_GLOBAL_VENTA_PRECISION, 2).nullable()
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
    val idDevolucionDetalle = varchar("id_devolucion_detalle", SCHEMA_ID_DEVOLUCION_DETALLE_MAX_LENGTH)
    val idDevolucion = varchar("id_devolucion", SCHEMA_ID_DEVOLUCION_MAX_LENGTH)
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemCantidad = decimal("_item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val itemPrecioSinIva = decimal("_item_preciosiniva", SCHEMA_ITEM_PRECIOSINIVA_PRECISION, 2)
    val itemDescuento = decimal("_item_descuento", SCHEMA_ITEM_DESCUENTO_PRECISION, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", SCHEMA_ITEM_MONTODESCUENTO_PRECISION, 2)
    val itemPIva = decimal("_item_piva", SCHEMA_ITEM_PIVA_PRECISION, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", SCHEMA_ITEM_TOTALSINIVA_PRECISION, 2)
    val itemTotalConIva = decimal("_item_totalconiva", SCHEMA_ITEM_TOTALCONIVA_PRECISION, 2)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", SCHEMA_ITEM_CODIGO_MAX_LENGTH)
    val itemReferencia = varchar("_item_referencia", SCHEMA_ITEM_REFERENCIA_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idDevolucionDetalle)
}

object CreditNoteFacturaTable : Table("factura") {
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val codFactura = varchar("cod_factura", SCHEMA_COD_FACTURA_MAX_LENGTH_32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH).nullable()
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val fechaFactura = date("fechaFactura").nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val subtotal = decimal("subtotal", SCHEMA_SUBTOTAL_PRECISION, 2)
    val totalizarSubTotal = decimal("totalizar_sub_total", SCHEMA_TOTALIZAR_SUB_TOTAL_PRECISION, 2)
    val totalizarTotalOperacion = decimal("totalizar_total_operacion", SCHEMA_TOTALIZAR_TOTAL_OPERACION_PRECISION, 2)
    val totalizarPDescuentoGlobal = decimal("totalizar_pdescuento_global", SCHEMA_TOTALIZAR_PDESCUENTO_GLOBAL_PRECISION, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", SCHEMA_TOTALIZAR_DESCUENTO_GLOBAL_PRECISION, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", SCHEMA_TOTALIZAR_BASE_IMPONIBLE_PRECISION, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", SCHEMA_TOTALIZAR_MONTO_IVA_PRECISION, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION, 2)
    val formaPago = varchar("formapago", SCHEMA_FORMAPAGO_MAX_LENGTH)
    val idCajaSecuencia = varchar("id_caja_secuencia", SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val idSucursal = integer("id_sucursal")
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)
    val codigoCaja = varchar("codigo_caja", SCHEMA_CODIGO_CAJA_MAX_LENGTH)
    val facturarA = varchar("facturar_a", SCHEMA_FACTURAR_A_MAX_LENGTH)
    val facturarARuc = varchar("facturar_a_ruc", SCHEMA_FACTURAR_A_RUC_MAX_LENGTH)
    val facturarADireccion = varchar("facturar_a_direccion", SCHEMA_FACTURAR_A_DIRECCION_MAX_LENGTH)
    val facturarATelefono = varchar("facturar_a_telefono", SCHEMA_FACTURAR_A_TELEFONO_MAX_LENGTH)
    val abrMonedaBase = varchar("abr_moneda_base", SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH).nullable()
    val tasa = decimal("tasa", SCHEMA_TASA_PRECISION, SCHEMA_TASA_SCALE).nullable()
    val totalRef = decimal("total_ref", SCHEMA_TOTAL_REF_PRECISION, 2).nullable()

    override val primaryKey = PrimaryKey(idFactura)
}

object CreditNoteFacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemDescripcion = varchar("_item_descripcion", SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH)
    val itemCantidad = decimal("_item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val itemPrecioSinIva = decimal("_item_preciosiniva", SCHEMA_ITEM_PRECIOSINIVA_PRECISION, 2)
    val itemDescuento = decimal("_item_descuento", SCHEMA_ITEM_DESCUENTO_PRECISION, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", SCHEMA_ITEM_MONTODESCUENTO_PRECISION, 2)
    val itemPIva = decimal("_item_piva", SCHEMA_ITEM_PIVA_PRECISION, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", SCHEMA_ITEM_TOTALSINIVA_PRECISION, 2)
    val itemTotalConIva = decimal("_item_totalconiva", SCHEMA_ITEM_TOTALCONIVA_PRECISION, 2)
    val itemCantidadTotal = decimal("_item_cantidad_total", SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION, SCHEMA_ITEM_CANTIDAD_TOTAL_SCALE)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", SCHEMA_ITEM_CODIGO_MAX_LENGTH).nullable()
    val itemReferencia = varchar("_item_referencia", SCHEMA_ITEM_REFERENCIA_MAX_LENGTH).nullable()
    val anulado = bool("anulado").default(false)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

object CreditNoteCajaTable : Table("caja") {
    val idCaja = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val codigo = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH_50).nullable()
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val serieCaja = varchar("serie_caja", SCHEMA_SERIE_CAJA_MAX_LENGTH).nullable()
    val impresoraModelo = varchar("impresora_modelo", SCHEMA_IMPRESORA_MODELO_MAX_LENGTH).nullable()
    val notacreditoCorrelativo = integer("notacredito_correlativo").nullable()
    val abonoCorrelativo = integer("abono_correlativo").nullable()
    val certificadoCorrelativo = integer("certificado_correlativo").nullable()

    override val primaryKey = PrimaryKey(idCaja)
}

object CreditNoteCajaSecuenciaTable : Table("caja_secuencia") {
    val idCajaSecuencia = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val secuencia = varchar("secuencia", SCHEMA_SECUENCIA_MAX_LENGTH).nullable()
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(idCajaSecuencia)
}

object CreditNoteAbonoTable : Table("abono") {
    val idAbono = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val codAbono = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH_50)
    val fecha = datetime("fecha_emision")
    val vencimiento = integer("vencimiento")
    val fechaVencimiento = datetime("fecha_vencimiento")
    val idVendedor = integer("id_vendedor")
    val idCajero = integer("id_cajero")
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val idCajaSecuencia = varchar("id_caja_secuencia", SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH)
    val monto = decimal("monto", SCHEMA_MONTO_PRECISION, 2)
    val saldo = decimal("saldo", SCHEMA_SALDO_PRECISION, 2)
    val estatus = integer("estatus")
    val descripcion = text("descripcion")
    val observacion = text("observacion")
    val tipo = varchar("tipo", SCHEMA_TIPO_MAX_LENGTH)
    val idOperacion = varchar("id_operacion", SCHEMA_ID_OPERACION_MAX_LENGTH)
    val codigoReparacion = varchar("codigo_reparacion", SCHEMA_CODIGO_REPARACION_MAX_LENGTH)
    val fechaCreacion = datetime("fecha_creacion")
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH)
    val fechaModificacion = datetime("fecha_modificacion")
    val usuarioModificacion = varchar("usuario_modificacion", SCHEMA_USUARIO_MODIFICACION_MAX_LENGTH)
    val fechaAnulacion = datetime("fecha_anulacion")
    val usuarioAnulacion = varchar("usuario_anulacion", SCHEMA_USUARIO_ANULACION_MAX_LENGTH)
    val idTransaccion = varchar("id_transaccion", SCHEMA_ID_TRANSACCION_MAX_LENGTH)
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idAbono)
}

object CreditNoteGiftCertificateTable : Table("certificado_regalo") {
    val idCertificado = varchar("id_certificado_regalo", SCHEMA_ID_CERTIFICADO_REGALO_MAX_LENGTH)
    val codigo = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH_32)
    val monto = decimal("monto", SCHEMA_MONTO_PRECISION, 2)
    val saldo = decimal("saldo", SCHEMA_SALDO_PRECISION, 2)
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val estatus = integer("estatus")
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val idTransaccion = varchar("id_transaccion", SCHEMA_ID_TRANSACCION_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(idCertificado)
}
