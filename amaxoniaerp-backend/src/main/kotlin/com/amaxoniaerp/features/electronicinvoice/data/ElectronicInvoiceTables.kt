package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_APELLIDO_MAX_LENGTH = 20
private const val SCHEMA_API_THEFACTORYHKA_MAX_LENGTH = 500
private const val SCHEMA_CAJA_DETALLE_ID_MAX_LENGTH = 36
private const val SCHEMA_CAJA_ID_MAX_LENGTH = 36
private const val SCHEMA_CAMPO_MAX_LENGTH = 100
private const val SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH = 20
private const val SCHEMA_COD_FACTURA_DETALLE_FORMAPAGO_MAX_LENGTH = 36
private const val SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_COD_FACTURA_MAX_LENGTH = 32
private const val SCHEMA_DESTINO_OPERACION_MAX_LENGTH = 5
private const val SCHEMA_DIRECCION_ENVIO_MAX_LENGTH = 500
private const val SCHEMA_DIRECCION_MAX_LENGTH = 200
private const val SCHEMA_DIRECCION_NIVEL3_MAX_LENGTH = 100
private const val SCHEMA_DV_MAX_LENGTH = 255
private const val SCHEMA_EMAIL_MAX_LENGTH = 50
private const val SCHEMA_ENTREGA_CAFE_MAX_LENGTH = 5
private const val SCHEMA_ENVIO_CONTENEDOR_MAX_LENGTH = 5
private const val SCHEMA_FECHA_FACTURA_MAX_LENGTH = 20
private const val SCHEMA_FECHA_INICIO_CONTINGENCIA_MAX_LENGTH = 30
private const val SCHEMA_FORMAPAGO_MAX_LENGTH = 20
private const val SCHEMA_FORMATO_CAFE_MAX_LENGTH = 5
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_ID_CLIENTE_MAX_LENGTH = 36
private const val SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_MAX_LENGTH = 36
private const val SCHEMA_IMPORTE_ISC_PRECISION = 20
private const val SCHEMA_IMPORTE_OTI_PRECISION = 20
private const val SCHEMA_ISO_MAX_LENGTH = 5
private const val SCHEMA_ITEM_CANTIDAD_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_SCALE = 3
private const val SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION = 32
private const val SCHEMA_ITEM_CODIGO_MAX_LENGTH = 50
private const val SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH = 500
private const val SCHEMA_ITEM_MONTODESCUENTO_PRECISION = 20
private const val SCHEMA_ITEM_PIVA_PRECISION = 10
private const val SCHEMA_ITEM_PRECIOSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALCONIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_UNIDAD_EMPAQUE_MAX_LENGTH = 15
private const val SCHEMA_IVA_TOTAL_FACTURA_PRECISION = 20
private const val SCHEMA_MONTO_ITEMS_FACTURA_PRECISION = 20
private const val SCHEMA_MONTO_PRECISION = 10
private const val SCHEMA_MOTIVO_CONTINGENCIA_MAX_LENGTH = 300
private const val SCHEMA_NATURALEZA_OPERACION_MAX_LENGTH = 5
private const val SCHEMA_NOMBRE_MAX_LENGTH = 100
private const val SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_OBSERVACION_MAX_LENGTH = 300
private const val SCHEMA_PORCENTAJE_ISC_PRECISION = 10
private const val SCHEMA_PROCESO_GENERACION_MAX_LENGTH = 5
private const val SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_RIF_MAX_LENGTH = 50
private const val SCHEMA_SIMBOLO_MAX_LENGTH = 20
private const val SCHEMA_TELEFONOS_MAX_LENGTH = 50
private const val SCHEMA_TIPO_CLIENTE_FE_MAX_LENGTH = 5
private const val SCHEMA_TIPO_DOCUMENTO_MAX_LENGTH = 5
private const val SCHEMA_TIPO_EMISION_MAX_LENGTH = 5
private const val SCHEMA_TIPO_FACTURA_MAX_LENGTH = 50
private const val SCHEMA_TIPO_OPERACION_MAX_LENGTH = 5
private const val SCHEMA_TIPO_VENTA_MAX_LENGTH = 5
private const val SCHEMA_TOKEN_EMPRESA_MAX_LENGTH = 500
private const val SCHEMA_TOKEN_PASSWORD_MAX_LENGTH = 500
private const val SCHEMA_TOTALIZAR_CAMBIO_PRECISION = 10
private const val SCHEMA_TOTALIZAR_DESCUENTO_GLOBAL_PRECISION = 20
private const val SCHEMA_TOTALIZAR_MONTO_CANCELAR_PRECISION = 10
private const val SCHEMA_TOTALIZAR_MONTO_RETENCION_PRECISION = 10
private const val SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION = 20
private const val SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION = 20

// ─── Tablas de SOLO LECTURA para Facturación Electrónica ─────────────────────
// Estas tablas mapean columnas adicionales que existen en la DB de Panamá
// pero que las tablas principales del ORM no exponen.
// NO se usan para escritura general; solo para leer datos necesarios
// al construir el payload de facturación electrónica.

/**
 * Vista de lectura sobre `factura` con los campos exclusivos de FE Panamá.
 * Complementa a [FacturasTablePA] y [BaseSalesFacturaTable] sin duplicarlas.
 */
object FEFacturaReadTable : Table("factura") {
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val codFactura = varchar("cod_factura", SCHEMA_COD_FACTURA_MAX_LENGTH)
    val codFacturaFiscal = varchar("cod_factura_fiscal", SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH).nullable()
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val idSucursal = integer("id_sucursal")
    val fechaFactura = varchar("fechaFactura", SCHEMA_FECHA_FACTURA_MAX_LENGTH).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION, 2)
    val montoItemsFactura = decimal("montoItemsFactura", SCHEMA_MONTO_ITEMS_FACTURA_PRECISION, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", SCHEMA_IVA_TOTAL_FACTURA_PRECISION, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", SCHEMA_TOTALIZAR_DESCUENTO_GLOBAL_PRECISION, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION, 2)
    val formaPago = varchar("formapago", SCHEMA_FORMAPAGO_MAX_LENGTH)
    val observacion = varchar("observacion", SCHEMA_OBSERVACION_MAX_LENGTH).nullable()

    val tipoDocumento = varchar("tipo_documento", SCHEMA_TIPO_DOCUMENTO_MAX_LENGTH).nullable()
    val naturalezaOperacion = varchar("NaturalezaOperacion", SCHEMA_NATURALEZA_OPERACION_MAX_LENGTH).nullable()
    val tipoOperacion = varchar("tipoOperacion", SCHEMA_TIPO_OPERACION_MAX_LENGTH).nullable()
    val formatoCAFE = varchar("formatoCAFE", SCHEMA_FORMATO_CAFE_MAX_LENGTH).nullable()
    val entregaCAFE = varchar("entregaCAFE", SCHEMA_ENTREGA_CAFE_MAX_LENGTH).nullable()
    val envioContenedor = varchar("envioContenedor", SCHEMA_ENVIO_CONTENEDOR_MAX_LENGTH).nullable()
    val tipoVenta = varchar("tipoVenta", SCHEMA_TIPO_VENTA_MAX_LENGTH).nullable()
    val tipoFactura = varchar("tipo_factura", SCHEMA_TIPO_FACTURA_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idFactura)
}

object FECientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val rif = varchar("rif", SCHEMA_RIF_MAX_LENGTH)
    val dv = varchar("dv", SCHEMA_DV_MAX_LENGTH)
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val apellido = varchar("apellido", SCHEMA_APELLIDO_MAX_LENGTH)
    val direccion = varchar("direccion", SCHEMA_DIRECCION_MAX_LENGTH)
    val direccionNivel3 = varchar("direccion_nivel3", SCHEMA_DIRECCION_NIVEL3_MAX_LENGTH).nullable()
    val telefonos = varchar("telefonos", SCHEMA_TELEFONOS_MAX_LENGTH)
    val email = varchar("email", SCHEMA_EMAIL_MAX_LENGTH)
    val pais = integer("pais")
    val paisExtranjero = integer("paisExtranjero").nullable()
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")

    override val primaryKey = PrimaryKey(idCliente)
}

object FETipoClienteReadTable : Table("tipo_cliente") {
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoClienteFE = varchar("TipoClienteFE", SCHEMA_TIPO_CLIENTE_FE_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(codTipoCliente)
}

object FEPaisesReadTable : Table("paises") {
    val id = integer("id")
    val iso = varchar("iso", SCHEMA_ISO_MAX_LENGTH)
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `parametros_generales` con campos de configuración PAC.
 */
object FEParametrosReadTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val tokenEmpresa = varchar("token_empresa", SCHEMA_TOKEN_EMPRESA_MAX_LENGTH).nullable()
    val tokenPassword = varchar("token_password", SCHEMA_TOKEN_PASSWORD_MAX_LENGTH).nullable()
    val direccionEnvio = varchar("direccion_envio", SCHEMA_DIRECCION_ENVIO_MAX_LENGTH).nullable()
    val api_thefactoryhka = varchar("api_thefactoryhka", SCHEMA_API_THEFACTORYHKA_MAX_LENGTH).nullable()
    val tipoEmision = varchar("tipoEmision", SCHEMA_TIPO_EMISION_MAX_LENGTH).nullable()
    val destinoOperacion = varchar("destinoOperacion", SCHEMA_DESTINO_OPERACION_MAX_LENGTH).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", SCHEMA_PROCESO_GENERACION_MAX_LENGTH).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH).nullable()
    val fechaInicioContingencia = varchar("fechaInicioContingencia", SCHEMA_FECHA_INICIO_CONTINGENCIA_MAX_LENGTH).nullable()
    val motivoContingencia = varchar("motivoContingencia", SCHEMA_MOTIVO_CONTINGENCIA_MAX_LENGTH).nullable()
    val tipoFacturacion = integer("tipo_facturacion").default(0)
}

/**
 * Vista de lectura sobre `sucursal` para código de sucursal emisor.
 */
object FESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `caja` para punto de facturación fiscal.
 */
object FECajaReadTable : Table("caja") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idSucursal = integer("id_sucursal").nullable()
    val codigoSucursalEmisor = varchar("CodigoSucursalEmisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `item` para obtener unidad_medida (código).
 */
object FEItemReadTable : Table("item") {
    val idItem = integer("id_item")
    val unidadMedida = integer("unidad_medida").nullable()
    val idSegmentoGob = integer("id_segmento_gob").nullable()
    val idFamiliaGob = integer("id_familia_gob").nullable()

    override val primaryKey = PrimaryKey(idItem)
}

/**
 * Vista de lectura sobre `unidad_empaques` para obtener simbolo.
 */
object FEUnidadEmpaquesReadTable : Table("unidad_empaques") {
    val codUnidad = integer("cod_unidad")
    val simbolo = varchar("simbolo", SCHEMA_SIMBOLO_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(codUnidad)
}

/**
 * Vista de lectura sobre `factura_detalle` con campos extra para FE (ISC, OTI).
 */
object FEFacturaDetalleReadTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH)
    val itemCodigo = varchar("_item_codigo", SCHEMA_ITEM_CODIGO_MAX_LENGTH)
    val idSegmento = integer("id_segmento").nullable()
    val idFamilia = integer("id_familia").nullable()
    val itemCantidad = decimal("_item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val itemCantidadTotal = decimal("_item_cantidad_total", SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION, 0)
    val itemPrecioSinIva = decimal("_item_preciosiniva", SCHEMA_ITEM_PRECIOSINIVA_PRECISION, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", SCHEMA_ITEM_MONTODESCUENTO_PRECISION, 2)
    val itemPiva = decimal("_item_piva", SCHEMA_ITEM_PIVA_PRECISION, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", SCHEMA_ITEM_TOTALSINIVA_PRECISION, 2)
    val itemTotalConIva = decimal("_item_totalconiva", SCHEMA_ITEM_TOTALCONIVA_PRECISION, 2)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", SCHEMA_ITEM_UNIDAD_EMPAQUE_MAX_LENGTH).nullable()

    // Campos FE adicionales que existen en la DB de Panamá
    val porcentajeIsc = decimal("porcentaje_isc", SCHEMA_PORCENTAJE_ISC_PRECISION, 2).nullable()
    val importeIsc = decimal("importe_isc", SCHEMA_IMPORTE_ISC_PRECISION, 2).nullable()
    val idOti = integer("id_oti").nullable()
    val importeOti = decimal("importe_oti", SCHEMA_IMPORTE_OTI_PRECISION, 2).nullable()

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Tabla de correlativos para el número de documento fiscal.
 * Se busca con campo = 'numeroDocumentoFiscal' y se incrementa `contador`.
 */
object FECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", SCHEMA_CAMPO_MAX_LENGTH)
    val contador = integer("contador")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre formas de pago asociadas a la factura.
 * Se lee desde caja_nueva_detalle y se cruza con caja_forma_pago.
 */
object FECajaNuevaDetalleReadTable : Table("caja_nueva_detalle") {
    val cajaDetalleId = varchar("caja_detalle_id", SCHEMA_CAJA_DETALLE_ID_MAX_LENGTH)
    val cajaId = varchar("caja_id", SCHEMA_CAJA_ID_MAX_LENGTH)
    val idFormaPago = integer("id_forma_pago").nullable()
    val monto = decimal("monto", SCHEMA_MONTO_PRECISION, 2).nullable()

    override val primaryKey = PrimaryKey(cajaDetalleId)
}

/**
 * Vista de lectura sobre caja_nueva para vincular con la factura.
 */
object FECajaNuevaReadTable : Table("caja_nueva") {
    val cajaId = varchar("caja_id", SCHEMA_CAJA_ID_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)

    override val primaryKey = PrimaryKey(cajaId)
}

/**
 * Vista de lectura sobre la tabla de detalle de forma de pago de la factura
 * para obtener el cambio (vuelto) del efectivo.
 */
object FEFacturaDetalleFormaPagoReadTable : Table("factura_detalle_formapago") {
    val codFacturaDetalleFormaPago = varchar("cod_factura_detalle_formapago", SCHEMA_COD_FACTURA_DETALLE_FORMAPAGO_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val codigoRetencion = integer("codigo_retencion").nullable()
    val totalizarMontoRetencion = decimal("totalizar_monto_retencion", SCHEMA_TOTALIZAR_MONTO_RETENCION_PRECISION, 2).nullable()
    val totalizarMontoCancelar = decimal("totalizar_monto_cancelar", SCHEMA_TOTALIZAR_MONTO_CANCELAR_PRECISION, 2).nullable()
    val totalizarCambio = decimal("totalizar_cambio", SCHEMA_TOTALIZAR_CAMBIO_PRECISION, 2)

    override val primaryKey = PrimaryKey(codFacturaDetalleFormaPago)
}
