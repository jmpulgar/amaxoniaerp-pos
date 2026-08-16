package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

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
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", S.VARCHAR_LENGTH_10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", S.VARCHAR_LENGTH_20).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal")
    val fechaFactura = varchar("fechaFactura", S.VARCHAR_LENGTH_20).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", S.DECIMAL_PRECISION_20, 2)
    val montoItemsFactura = decimal("montoItemsFactura", S.DECIMAL_PRECISION_20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", S.DECIMAL_PRECISION_20, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", S.DECIMAL_PRECISION_20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", S.DECIMAL_PRECISION_20, 2)
    val formaPago = varchar("formapago", S.VARCHAR_LENGTH_20)
    val observacion = varchar("observacion", S.VARCHAR_LENGTH_300).nullable()

    val tipoDocumento = varchar("tipo_documento", S.VARCHAR_LENGTH_5).nullable()
    val naturalezaOperacion = varchar("NaturalezaOperacion", S.VARCHAR_LENGTH_5).nullable()
    val tipoOperacion = varchar("tipoOperacion", S.VARCHAR_LENGTH_5).nullable()
    val formatoCAFE = varchar("formatoCAFE", S.VARCHAR_LENGTH_5).nullable()
    val entregaCAFE = varchar("entregaCAFE", S.VARCHAR_LENGTH_5).nullable()
    val envioContenedor = varchar("envioContenedor", S.VARCHAR_LENGTH_5).nullable()
    val tipoVenta = varchar("tipoVenta", S.VARCHAR_LENGTH_5).nullable()
    val tipoFactura = varchar("tipo_factura", S.VARCHAR_LENGTH_50)

    override val primaryKey = PrimaryKey(idFactura)
}

object FECientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val rif = varchar("rif", S.VARCHAR_LENGTH_50)
    val dv = varchar("dv", S.VARCHAR_LENGTH_255)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)
    val apellido = varchar("apellido", S.VARCHAR_LENGTH_20)
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_200)
    val direccionNivel3 = varchar("direccion_nivel3", S.VARCHAR_LENGTH_100).nullable()
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50)
    val email = varchar("email", S.VARCHAR_LENGTH_50)
    val pais = integer("pais")
    val paisExtranjero = integer("paisExtranjero").nullable()
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")

    override val primaryKey = PrimaryKey(idCliente)
}

object FETipoClienteReadTable : Table("tipo_cliente") {
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoClienteFE = varchar("TipoClienteFE", S.VARCHAR_LENGTH_5).nullable()

    override val primaryKey = PrimaryKey(codTipoCliente)
}

object FEPaisesReadTable : Table("paises") {
    val id = integer("id")
    val iso = varchar("iso", S.VARCHAR_LENGTH_5)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `parametros_generales` con campos de configuración PAC.
 */
object FEParametrosReadTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val tokenEmpresa = varchar("token_empresa", S.VARCHAR_LENGTH_500).nullable()
    val tokenPassword = varchar("token_password", S.VARCHAR_LENGTH_500).nullable()
    val direccionEnvio = varchar("direccion_envio", S.VARCHAR_LENGTH_500).nullable()
    val api_thefactoryhka = varchar("api_thefactoryhka", S.VARCHAR_LENGTH_500).nullable()
    val tipoEmision = varchar("tipoEmision", S.VARCHAR_LENGTH_5).nullable()
    val destinoOperacion = varchar("destinoOperacion", S.VARCHAR_LENGTH_5).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", S.VARCHAR_LENGTH_5).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", S.VARCHAR_LENGTH_20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", S.VARCHAR_LENGTH_10).nullable()
    val fechaInicioContingencia = varchar("fechaInicioContingencia", S.VARCHAR_LENGTH_30).nullable()
    val motivoContingencia = varchar("motivoContingencia", S.VARCHAR_LENGTH_300).nullable()
    val tipoFacturacion = integer("tipo_facturacion").default(0)
}

/**
 * Vista de lectura sobre `sucursal` para código de sucursal emisor.
 */
object FESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", S.VARCHAR_LENGTH_20).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `caja` para punto de facturación fiscal.
 */
object FECajaReadTable : Table("caja") {
    val id = varchar("id", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal").nullable()
    val codigoSucursalEmisor = varchar("CodigoSucursalEmisor", S.VARCHAR_LENGTH_20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", S.VARCHAR_LENGTH_10).nullable()

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
    val simbolo = varchar("simbolo", S.VARCHAR_LENGTH_20).nullable()

    override val primaryKey = PrimaryKey(codUnidad)
}

/**
 * Vista de lectura sobre `factura_detalle` con campos extra para FE (ISC, OTI).
 */
object FEFacturaDetalleReadTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", S.VARCHAR_LENGTH_500)
    val itemCodigo = varchar("_item_codigo", S.VARCHAR_LENGTH_50)
    val idSegmento = integer("id_segmento").nullable()
    val idFamilia = integer("id_familia").nullable()
    val itemCantidad = decimal("_item_cantidad", S.DECIMAL_PRECISION_32, S.DECIMAL_SCALE_3)
    val itemCantidadTotal = decimal("_item_cantidad_total", S.DECIMAL_PRECISION_32, 0)
    val itemPrecioSinIva = decimal("_item_preciosiniva", S.DECIMAL_PRECISION_20, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", S.DECIMAL_PRECISION_20, 2)
    val itemPiva = decimal("_item_piva", S.DECIMAL_PRECISION_10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", S.DECIMAL_PRECISION_20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", S.DECIMAL_PRECISION_20, 2)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", S.VARCHAR_LENGTH_15).nullable()

    // Campos FE adicionales que existen en la DB de Panamá
    val porcentajeIsc = decimal("porcentaje_isc", S.DECIMAL_PRECISION_10, 2).nullable()
    val importeIsc = decimal("importe_isc", S.DECIMAL_PRECISION_20, 2).nullable()
    val idOti = integer("id_oti").nullable()
    val importeOti = decimal("importe_oti", S.DECIMAL_PRECISION_20, 2).nullable()

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Tabla de correlativos para el número de documento fiscal.
 * Se busca con campo = 'numeroDocumentoFiscal' y se incrementa `contador`.
 */
object FECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", S.VARCHAR_LENGTH_100)
    val contador = integer("contador")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre formas de pago asociadas a la factura.
 * Se lee desde caja_nueva_detalle y se cruza con caja_forma_pago.
 */
object FECajaNuevaDetalleReadTable : Table("caja_nueva_detalle") {
    val cajaDetalleId = varchar("caja_detalle_id", S.VARCHAR_LENGTH_36)
    val cajaId = varchar("caja_id", S.VARCHAR_LENGTH_36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val monto = decimal("monto", S.DECIMAL_PRECISION_10, 2).nullable()

    override val primaryKey = PrimaryKey(cajaDetalleId)
}

/**
 * Vista de lectura sobre caja_nueva para vincular con la factura.
 */
object FECajaNuevaReadTable : Table("caja_nueva") {
    val cajaId = varchar("caja_id", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)

    override val primaryKey = PrimaryKey(cajaId)
}

/**
 * Vista de lectura sobre la tabla de detalle de forma de pago de la factura
 * para obtener el cambio (vuelto) del efectivo.
 */
object FEFacturaDetalleFormaPagoReadTable : Table("factura_detalle_formapago") {
    val codFacturaDetalleFormaPago = varchar("cod_factura_detalle_formapago", S.VARCHAR_LENGTH_36)
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codigoRetencion = integer("codigo_retencion").nullable()
    val totalizarMontoRetencion = decimal("totalizar_monto_retencion", S.DECIMAL_PRECISION_10, 2).nullable()
    val totalizarMontoCancelar = decimal("totalizar_monto_cancelar", S.DECIMAL_PRECISION_10, 2).nullable()
    val totalizarCambio = decimal("totalizar_cambio", S.DECIMAL_PRECISION_10, 2)

    override val primaryKey = PrimaryKey(codFacturaDetalleFormaPago)
}
