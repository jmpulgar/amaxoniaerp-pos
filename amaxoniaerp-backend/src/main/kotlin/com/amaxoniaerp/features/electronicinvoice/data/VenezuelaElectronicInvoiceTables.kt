package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table

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
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", 10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    val numeroControlThka = varchar("numero_control_thka", 50).nullable()
    val tipoDocumento = varchar("tipo_documento", 5).nullable()
    val fechaFactura = varchar("fechaFactura", 20).nullable()
    val fechaCreacion = varchar("fecha_creacion", 25).nullable()
    val idCliente = varchar("id_cliente", 36).nullable()
    val facturarARuc = varchar("facturar_a_ruc", 50)
    val facturarANombre = varchar("facturar_a", 80)
    val facturarADireccion = varchar("facturar_a_direccion", 250)
    val facturarATelefono = varchar("facturar_a_telefono", 50)
    val totalTotalFactura = decimal("TotalTotalFactura", 20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", 20, 2)
    val descuentosItemFactura = decimal("descuentosItemFactura", 20, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", 20, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", 20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", 20, 2)
    val montoItemsFactura = decimal("montoItemsFactura", 20, 2)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", 10)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", 10)
    val idCaja = varchar("id_caja", 36)
    val idSucursal = integer("id_sucursal")

    override val primaryKey = PrimaryKey(idFactura)
}

/**
 * Vista de lectura sobre `clientes` para FE Venezuela.
 * Reutiliza `rif` (RIF/NIT del comprador), nombre, dirección y teléfono.
 */
object VEClientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", 36)
    val rif = varchar("rif", 50)
    val nombre = varchar("nombre", 100)
    val direccion = varchar("direccion", 200)
    val telefonos = varchar("telefonos", 50)
    val email = varchar("email", 50)
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
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idFactura = varchar("id_factura", 36)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", 500)
    val itemCodigo = varchar("_item_codigo", 50)
    val itemReferencia = varchar("_item_referencia", 50)
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val itemCantidadTotal = decimal("_item_cantidad_total", 32, 0)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", 15).nullable()
    val itemPrecioSinIva = decimal("_item_preciosiniva", 20, 2)
    val itemDescuento = decimal("_item_descuento", 10, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", 20, 2)
    val itemPiva = decimal("_item_piva", 10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", 20, 2)
    val importeIsc = decimal("importe_isc", 20, 2).nullable()
    val porcentajeIsc = decimal("porcentaje_isc", 10, 2).nullable()
    val importeOti = decimal("importe_oti", 20, 2).nullable()
    val importeAcarreo = decimal("importe_acarreo", 20, 2).nullable()
    val importeSeguro = decimal("importe_seguro", 20, 2).nullable()
    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Vista de lectura sobre `caja`. Provee `serie_caja`, `serie_sucursal`
 * (nombre físico en VE) y `codigoSucursalEmisor`/`puntoFacturacionFiscal` si la
 * integración local los define en la DB del tenant.
 */
object VECajaReadTable : Table("caja") {
    val id = varchar("id", 36)
    val idSucursal = integer("id_sucursal").nullable()
    val serieCaja = varchar("serie_caja", 10)
    val serieSucursal = varchar("serie_sucursal", 10).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", 20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `sucursal` para FE Venezuela.
 */
object VESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", 20).nullable()
    val serie = varchar("serie", 10).nullable()
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
    val tokenEmpresa = varchar("token_empresa", 500).nullable()
    val tokenPassword = varchar("token_password", 500).nullable()
    val rif = varchar("rif", 50).nullable()
    val nombreEmpresa = varchar("nombre_empresa", 200).nullable()
    val direccion = varchar("direccion", 250).nullable()
    val telefonos = varchar("telefonos", 50).nullable()
    val igtf = decimal("igtf", 10, 6).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", 20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", 5).nullable()
    val tipoEmision = varchar("tipoEmision", 5).nullable()
}

/**
 * Tabla de correlativos: en FE Venezuela usamos
 * `correlativos.campo = 'correlativo_factura_electronica'`.
 * El campo `contador` se incrementa atómicamente post-emisión y `formato`
 * indica la longitud esperada del número fiscal.
 */
object VECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", 100)
    val contador = integer("contador")
    val formato = integer("formato").nullable()
    override val primaryKey = PrimaryKey(id)
}
