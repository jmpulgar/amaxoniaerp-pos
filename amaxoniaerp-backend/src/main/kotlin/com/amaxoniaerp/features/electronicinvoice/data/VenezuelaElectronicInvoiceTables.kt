package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

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
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", S.VARCHAR_LENGTH_10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", S.VARCHAR_LENGTH_20).nullable()
    val numeroControlThka = varchar("numero_control_thka", S.VARCHAR_LENGTH_50).nullable()
    val tipoDocumento = varchar("tipo_documento", S.VARCHAR_LENGTH_5).nullable()
    val fechaFactura = varchar("fechaFactura", S.VARCHAR_LENGTH_20).nullable()
    val fechaCreacion = varchar("fecha_creacion", S.VARCHAR_LENGTH_25).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36).nullable()
    val facturarARuc = varchar("facturar_a_ruc", S.VARCHAR_LENGTH_50)
    val facturarANombre = varchar("facturar_a", S.VARCHAR_LENGTH_80)
    val facturarADireccion = varchar("facturar_a_direccion", S.VARCHAR_LENGTH_250)
    val facturarATelefono = varchar("facturar_a_telefono", S.VARCHAR_LENGTH_50)
    val totalTotalFactura = decimal("TotalTotalFactura", S.DECIMAL_PRECISION_20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", S.DECIMAL_PRECISION_20, 2)
    val descuentosItemFactura = decimal("descuentosItemFactura", S.DECIMAL_PRECISION_20, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", S.DECIMAL_PRECISION_20, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", S.DECIMAL_PRECISION_20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", S.DECIMAL_PRECISION_20, 2)
    val montoItemsFactura = decimal("montoItemsFactura", S.DECIMAL_PRECISION_20, 2)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", S.VARCHAR_LENGTH_10)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", S.VARCHAR_LENGTH_10)
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal")

    override val primaryKey = PrimaryKey(idFactura)
}

/**
 * Vista de lectura sobre `clientes` para FE Venezuela.
 * Reutiliza `rif` (RIF/NIT del comprador), nombre, dirección y teléfono.
 */
object VEClientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val rif = varchar("rif", S.VARCHAR_LENGTH_50)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_200)
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50)
    val email = varchar("email", S.VARCHAR_LENGTH_50)
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
    val idDetalleFactura = varchar("id_detalle_factura", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", S.VARCHAR_LENGTH_500)
    val itemCodigo = varchar("_item_codigo", S.VARCHAR_LENGTH_50)
    val itemReferencia = varchar("_item_referencia", S.VARCHAR_LENGTH_50)
    val itemCantidad = decimal("_item_cantidad", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val itemCantidadTotal = decimal("_item_cantidad_total", S.DECIMAL_PRECISION_32, 0)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", S.VARCHAR_LENGTH_15).nullable()
    val itemPrecioSinIva = decimal("_item_preciosiniva", S.DECIMAL_PRECISION_20, 2)
    val itemDescuento = decimal("_item_descuento", S.DECIMAL_PRECISION_10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", S.DECIMAL_PRECISION_20, 2)
    val itemPiva = decimal("_item_piva", S.DECIMAL_PRECISION_10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", S.DECIMAL_PRECISION_20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", S.DECIMAL_PRECISION_20, 2)
    val importeIsc = decimal("importe_isc", S.DECIMAL_PRECISION_20, 2).nullable()
    val porcentajeIsc = decimal("porcentaje_isc", S.DECIMAL_PRECISION_10, 2).nullable()
    val importeOti = decimal("importe_oti", S.DECIMAL_PRECISION_20, 2).nullable()
    val importeAcarreo = decimal("importe_acarreo", S.DECIMAL_PRECISION_20, 2).nullable()
    val importeSeguro = decimal("importe_seguro", S.DECIMAL_PRECISION_20, 2).nullable()
    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Vista de lectura sobre `caja`. Provee `serie_caja`, `serie_sucursal`
 * (nombre físico en VE) y `codigoSucursalEmisor`/`puntoFacturacionFiscal` si la
 * integración local los define en la DB del tenant.
 */
object VECajaReadTable : Table("caja") {
    val id = varchar("id", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal").nullable()
    val serieCaja = varchar("serie_caja", S.VARCHAR_LENGTH_10)
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", S.VARCHAR_LENGTH_20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", S.VARCHAR_LENGTH_10).nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `sucursal` para FE Venezuela.
 */
object VESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", S.VARCHAR_LENGTH_20).nullable()
    val serie = varchar("serie", S.VARCHAR_LENGTH_10).nullable()
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
    val tokenEmpresa = varchar("token_empresa", S.VARCHAR_LENGTH_500).nullable()
    val tokenPassword = varchar("token_password", S.VARCHAR_LENGTH_500).nullable()
    val rif = varchar("rif", S.VARCHAR_LENGTH_50).nullable()
    val nombreEmpresa = varchar("nombre_empresa", S.VARCHAR_LENGTH_200).nullable()
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_250).nullable()
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50).nullable()
    val igtf = decimal("igtf", S.DECIMAL_PRECISION_10, S.DECIMAL_SCALE_6).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", S.VARCHAR_LENGTH_20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", S.VARCHAR_LENGTH_10).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", S.VARCHAR_LENGTH_5).nullable()
    val tipoEmision = varchar("tipoEmision", S.VARCHAR_LENGTH_5).nullable()
}

/**
 * Tabla de correlativos: en FE Venezuela usamos
 * `correlativos.campo = 'correlativo_factura_electronica'`.
 * El campo `contador` se incrementa atómicamente post-emisión y `formato`
 * indica la longitud esperada del número fiscal.
 */
object VECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", S.VARCHAR_LENGTH_100)
    val contador = integer("contador")
    val formato = integer("formato").nullable()
    override val primaryKey = PrimaryKey(id)
}
