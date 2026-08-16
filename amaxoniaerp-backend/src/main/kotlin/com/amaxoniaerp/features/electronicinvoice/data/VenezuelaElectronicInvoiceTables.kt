package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH = 10
private const val SCHEMA_ABR_MONEDA_SECUNDARIA_MAX_LENGTH = 10
private const val SCHEMA_CAMPO_MAX_LENGTH = 100
private const val SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH = 20
private const val SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_COD_FACTURA_MAX_LENGTH = 32
private const val SCHEMA_DESCUENTOS_ITEM_FACTURA_PRECISION = 20
private const val SCHEMA_DIRECCION_MAX_LENGTH_200 = 200
private const val SCHEMA_DIRECCION_MAX_LENGTH_250 = 250
private const val SCHEMA_EMAIL_MAX_LENGTH = 50
private const val SCHEMA_FACTURAR_A_DIRECCION_MAX_LENGTH = 250
private const val SCHEMA_FACTURAR_A_MAX_LENGTH = 80
private const val SCHEMA_FACTURAR_A_RUC_MAX_LENGTH = 50
private const val SCHEMA_FACTURAR_A_TELEFONO_MAX_LENGTH = 50
private const val SCHEMA_FECHA_CREACION_MAX_LENGTH = 25
private const val SCHEMA_FECHA_FACTURA_MAX_LENGTH = 20
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_ID_CLIENTE_MAX_LENGTH = 36
private const val SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_ID_MAX_LENGTH = 36
private const val SCHEMA_IGTF_PRECISION = 10
private const val SCHEMA_IGTF_SCALE = 6
private const val SCHEMA_IMPORTE_ACARREO_PRECISION = 20
private const val SCHEMA_IMPORTE_ISC_PRECISION = 20
private const val SCHEMA_IMPORTE_OTI_PRECISION = 20
private const val SCHEMA_IMPORTE_SEGURO_PRECISION = 20
private const val SCHEMA_ITEM_CANTIDAD_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_SCALE = 3
private const val SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION = 32
private const val SCHEMA_ITEM_CODIGO_MAX_LENGTH = 50
private const val SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH = 500
private const val SCHEMA_ITEM_DESCUENTO_PRECISION = 10
private const val SCHEMA_ITEM_MONTODESCUENTO_PRECISION = 20
private const val SCHEMA_ITEM_PIVA_PRECISION = 10
private const val SCHEMA_ITEM_PRECIOSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_REFERENCIA_MAX_LENGTH = 50
private const val SCHEMA_ITEM_TOTALCONIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_UNIDAD_EMPAQUE_MAX_LENGTH = 15
private const val SCHEMA_IVA_TOTAL_FACTURA_PRECISION = 20
private const val SCHEMA_MONTO_ITEMS_FACTURA_PRECISION = 20
private const val SCHEMA_NOMBRE_EMPRESA_MAX_LENGTH = 200
private const val SCHEMA_NOMBRE_MAX_LENGTH = 100
private const val SCHEMA_NUMERO_CONTROL_THKA_MAX_LENGTH = 50
private const val SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_PORCENTAJE_ISC_PRECISION = 10
private const val SCHEMA_PROCESO_GENERACION_MAX_LENGTH = 5
private const val SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_RIF_MAX_LENGTH = 50
private const val SCHEMA_SERIE_CAJA_MAX_LENGTH = 10
private const val SCHEMA_SERIE_MAX_LENGTH = 10
private const val SCHEMA_SERIE_SUCURSAL_MAX_LENGTH = 10
private const val SCHEMA_TELEFONOS_MAX_LENGTH = 50
private const val SCHEMA_TIPO_DOCUMENTO_MAX_LENGTH = 5
private const val SCHEMA_TIPO_EMISION_MAX_LENGTH = 5
private const val SCHEMA_TOKEN_EMPRESA_MAX_LENGTH = 500
private const val SCHEMA_TOKEN_PASSWORD_MAX_LENGTH = 500
private const val SCHEMA_TOTALIZAR_BASE_IMPONIBLE_PRECISION = 20
private const val SCHEMA_TOTALIZAR_MONTO_IVA_PRECISION = 20
private const val SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION = 20
private const val SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION = 20

// ─── Tablas de SOLO LECTURA para Facturación Electrónica VENEZUELA (HKA FE) ───
// Mapean las columnas que YA existen en la base de datos de tenant VE,
// necesarias para construir el payload The Factory HKA. Son de solo lectura:
// ningún flujo de escritura las usa. NO se duplican columnas de [FECientesReadTable]
// ni [FEFacturaDetalleReadTable] que ya mapean `clientes` y `factura_detalle`
// (esos esquemas son compartidos VE/PA en la DB).

/**
 * Vista de lectura sobre `factura` con los campos exclusivos de FE Venezuela.
 *
 * Selecciona únicamente las columnas que el pipeline Venezuela necesita. Reusa
 * la PK `id_factura`. No se incluye `cufe`/`qr`/`nroProtocoloAutorizacion`
 * porque en VE NO se persisten (corresponden a PA).
 */
object VEFacturaReadTable : Table("factura") {
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val codFactura = varchar("cod_factura", SCHEMA_COD_FACTURA_MAX_LENGTH)
    val codFacturaFiscal = varchar("cod_factura_fiscal", SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH).nullable()
    val numeroControlThka = varchar("numero_control_thka", SCHEMA_NUMERO_CONTROL_THKA_MAX_LENGTH).nullable()
    val tipoDocumento = varchar("tipo_documento", SCHEMA_TIPO_DOCUMENTO_MAX_LENGTH).nullable()
    val fechaFactura = varchar("fechaFactura", SCHEMA_FECHA_FACTURA_MAX_LENGTH).nullable()
    val fechaCreacion = varchar("fecha_creacion", SCHEMA_FECHA_CREACION_MAX_LENGTH).nullable()
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH).nullable()
    val facturarARuc = varchar("facturar_a_ruc", SCHEMA_FACTURAR_A_RUC_MAX_LENGTH)
    val facturarANombre = varchar("facturar_a", SCHEMA_FACTURAR_A_MAX_LENGTH)
    val facturarADireccion = varchar("facturar_a_direccion", SCHEMA_FACTURAR_A_DIRECCION_MAX_LENGTH)
    val facturarATelefono = varchar("facturar_a_telefono", SCHEMA_FACTURAR_A_TELEFONO_MAX_LENGTH)
    val totalTotalFactura = decimal("TotalTotalFactura", SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", SCHEMA_IVA_TOTAL_FACTURA_PRECISION, 2)
    val descuentosItemFactura = decimal("descuentosItemFactura", SCHEMA_DESCUENTOS_ITEM_FACTURA_PRECISION, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", SCHEMA_TOTALIZAR_BASE_IMPONIBLE_PRECISION, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", SCHEMA_TOTALIZAR_MONTO_IVA_PRECISION, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION, 2)
    val montoItemsFactura = decimal("montoItemsFactura", SCHEMA_MONTO_ITEMS_FACTURA_PRECISION, 2)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", SCHEMA_ABR_MONEDA_SECUNDARIA_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val idSucursal = integer("id_sucursal")

    override val primaryKey = PrimaryKey(idFactura)
}

/**
 * Vista de lectura sobre `clientes` para FE Venezuela.
 * Reutiliza `rif` (RIF/NIT del comprador), nombre, dirección y teléfono.
 */
object VEClientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val rif = varchar("rif", SCHEMA_RIF_MAX_LENGTH)
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val direccion = varchar("direccion", SCHEMA_DIRECCION_MAX_LENGTH_200)
    val telefonos = varchar("telefonos", SCHEMA_TELEFONOS_MAX_LENGTH)
    val email = varchar("email", SCHEMA_EMAIL_MAX_LENGTH)
    override val primaryKey = PrimaryKey(idCliente)
}

/**
 * Vista de lectura sobre `factura_detalle` para FE Venezuela.
 *
 * No incluye `factura_detalle.importe_igtf` porque ese campo NO existe en VE
 * (ver brief). El IGTF se calcula en el Builder desde `parametros_generales.igtf`
 * + formas de pago en divisa.
 */
object VEFacturaDetalleReadTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", SCHEMA_ID_DETALLE_FACTURA_MAX_LENGTH)
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH)
    val itemCodigo = varchar("_item_codigo", SCHEMA_ITEM_CODIGO_MAX_LENGTH)
    val itemReferencia = varchar("_item_referencia", SCHEMA_ITEM_REFERENCIA_MAX_LENGTH)
    val itemCantidad = decimal("_item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val itemCantidadTotal = decimal("_item_cantidad_total", SCHEMA_ITEM_CANTIDAD_TOTAL_PRECISION, 0)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", SCHEMA_ITEM_UNIDAD_EMPAQUE_MAX_LENGTH).nullable()
    val itemPrecioSinIva = decimal("_item_preciosiniva", SCHEMA_ITEM_PRECIOSINIVA_PRECISION, 2)
    val itemDescuento = decimal("_item_descuento", SCHEMA_ITEM_DESCUENTO_PRECISION, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", SCHEMA_ITEM_MONTODESCUENTO_PRECISION, 2)
    val itemPiva = decimal("_item_piva", SCHEMA_ITEM_PIVA_PRECISION, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", SCHEMA_ITEM_TOTALSINIVA_PRECISION, 2)
    val itemTotalConIva = decimal("_item_totalconiva", SCHEMA_ITEM_TOTALCONIVA_PRECISION, 2)
    val importeIsc = decimal("importe_isc", SCHEMA_IMPORTE_ISC_PRECISION, 2).nullable()
    val porcentajeIsc = decimal("porcentaje_isc", SCHEMA_PORCENTAJE_ISC_PRECISION, 2).nullable()
    val importeOti = decimal("importe_oti", SCHEMA_IMPORTE_OTI_PRECISION, 2).nullable()
    val importeAcarreo = decimal("importe_acarreo", SCHEMA_IMPORTE_ACARREO_PRECISION, 2).nullable()
    val importeSeguro = decimal("importe_seguro", SCHEMA_IMPORTE_SEGURO_PRECISION, 2).nullable()
    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Vista de lectura sobre `caja`. Provee `serie_caja`, `serie_sucursal`
 * (nombre físico en VE) y `codigoSucursalEmisor`/`puntoFacturacionFiscal` si la
 * integración local los define en la DB del tenant.
 */
object VECajaReadTable : Table("caja") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idSucursal = integer("id_sucursal").nullable()
    val serieCaja = varchar("serie_caja", SCHEMA_SERIE_CAJA_MAX_LENGTH)
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH).nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `sucursal` para FE Venezuela.
 */
object VESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val serie = varchar("serie", SCHEMA_SERIE_MAX_LENGTH).nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `parametros_generales` con los campos exclusivos
 * Venezuela: credenciales HKA, entorno demo/prod, RIF emisora, IGTF.
 *
 * `token_empresa` y `token_password` son SECRETO: solo viven aquí para lectura
 * por el repositorio. NUNCA se loguean, NUNCA se devuelven al POS.
 */
object VEParametrosReadTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val tipoFacturacion = integer("tipo_facturacion").default(0)
    val tipoEntornoVe = integer("tipo_entorno_ve").default(0)

    // FASE 1.1 (cleanup): la URL base del PAC NO se persiste en la base del
    // tenant. Se deriva por configuración de aplicación a partir de
    // `tipo_entorno_ve` (0=demo, 1=producción). Antes se leía la columna
    // `parametros_generales.api_thefactoryhka`, pero esa columna NO existe en
    // el esquema real del tenant: ya se eliminó del contrato Exposed.
    val tokenEmpresa = varchar("token_empresa", SCHEMA_TOKEN_EMPRESA_MAX_LENGTH).nullable()
    val tokenPassword = varchar("token_password", SCHEMA_TOKEN_PASSWORD_MAX_LENGTH).nullable()
    val rif = varchar("rif", SCHEMA_RIF_MAX_LENGTH).nullable()
    val nombreEmpresa = varchar("nombre_empresa", SCHEMA_NOMBRE_EMPRESA_MAX_LENGTH).nullable()
    val direccion = varchar("direccion", SCHEMA_DIRECCION_MAX_LENGTH_250).nullable()
    val telefonos = varchar("telefonos", SCHEMA_TELEFONOS_MAX_LENGTH).nullable()
    val igtf = decimal("igtf", SCHEMA_IGTF_PRECISION, SCHEMA_IGTF_SCALE).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", SCHEMA_PROCESO_GENERACION_MAX_LENGTH).nullable()
    val tipoEmision = varchar("tipoEmision", SCHEMA_TIPO_EMISION_MAX_LENGTH).nullable()
}

/**
 * Tabla de correlativos: en FE Venezuela usamos
 * `correlativos.campo = 'correlativo_factura_electronica'`.
 * El campo `contador` se incrementa atómicamente post-emisión y `formato`
 * indica la longitud esperada del número fiscal.
 */
object VECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", SCHEMA_CAMPO_MAX_LENGTH)
    val contador = integer("contador")
    val formato = integer("formato").nullable()
    override val primaryKey = PrimaryKey(id)
}
