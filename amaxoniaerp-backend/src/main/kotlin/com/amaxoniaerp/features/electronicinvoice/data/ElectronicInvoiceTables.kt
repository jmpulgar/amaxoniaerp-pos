package com.amaxoniaerp.features.electronicinvoice.data

import org.jetbrains.exposed.sql.Table

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
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", 10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    val idCliente = varchar("id_cliente", 36)
    val idCaja = varchar("id_caja", 36)
    val idSucursal = integer("id_sucursal")
    val fechaFactura = varchar("fechaFactura", 20).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", 20, 2)
    val montoItemsFactura = decimal("montoItemsFactura", 20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", 20, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", 20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", 20, 2)
    val formaPago = varchar("formapago", 20)
    val observacion = varchar("observacion", 300).nullable()

    val tipoDocumento = varchar("tipo_documento", 5).nullable()
    val naturalezaOperacion = varchar("NaturalezaOperacion", 5).nullable()
    val tipoOperacion = varchar("tipoOperacion", 5).nullable()
    val formatoCAFE = varchar("formatoCAFE", 5).nullable()
    val entregaCAFE = varchar("entregaCAFE", 5).nullable()
    val envioContenedor = varchar("envioContenedor", 5).nullable()
    val tipoVenta = varchar("tipoVenta", 5).nullable()
    val tipoFactura = varchar("tipo_factura", 50)

    override val primaryKey = PrimaryKey(idFactura)
}

object FECientesReadTable : Table("clientes") {
    val idCliente = varchar("id_cliente", 36)
    val rif = varchar("rif", 50)
    val dv = varchar("dv", 255)
    val nombre = varchar("nombre", 100)
    val apellido = varchar("apellido", 20)
    val direccion = varchar("direccion", 200)
    val direccionNivel3 = varchar("direccion_nivel3", 100).nullable()
    val telefonos = varchar("telefonos", 50)
    val email = varchar("email", 50)
    val pais = integer("pais")
    val paisExtranjero = integer("paisExtranjero").nullable()
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")

    override val primaryKey = PrimaryKey(idCliente)
}

object FETipoClienteReadTable : Table("tipo_cliente") {
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoClienteFE = varchar("TipoClienteFE", 5).nullable()

    override val primaryKey = PrimaryKey(codTipoCliente)
}

object FEPaisesReadTable : Table("paises") {
    val id = integer("id")
    val iso = varchar("iso", 5)
    val nombre = varchar("nombre", 100)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `parametros_generales` con campos de configuración PAC.
 */
object FEParametrosReadTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val tokenEmpresa = varchar("token_empresa", 500).nullable()
    val tokenPassword = varchar("token_password", 500).nullable()
    val direccionEnvio = varchar("direccion_envio", 500).nullable()
    val api_thefactoryhka = varchar("api_thefactoryhka", 500).nullable()
    val tipoEmision = varchar("tipoEmision", 5).nullable()
    val destinoOperacion = varchar("destinoOperacion", 5).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", 5).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", 20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()
    val fechaInicioContingencia = varchar("fechaInicioContingencia", 30).nullable()
    val motivoContingencia = varchar("motivoContingencia", 300).nullable()
    val tipoFacturacion = integer("tipo_facturacion").default(0)
}

/**
 * Vista de lectura sobre `sucursal` para código de sucursal emisor.
 */
object FESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre `caja` para punto de facturación fiscal.
 */
object FECajaReadTable : Table("caja") {
    val id = varchar("id", 36)
    val idSucursal = integer("id_sucursal").nullable()
    val codigoSucursalEmisor = varchar("CodigoSucursalEmisor", 20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()

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
    val simbolo = varchar("simbolo", 20).nullable()

    override val primaryKey = PrimaryKey(codUnidad)
}

/**
 * Vista de lectura sobre `factura_detalle` con campos extra para FE (ISC, OTI).
 */
object FEFacturaDetalleReadTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idFactura = varchar("id_factura", 36)
    val idItem = integer("id_item").nullable()
    val itemDescripcion = varchar("_item_descripcion", 500)
    val itemCodigo = varchar("_item_codigo", 50)
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val itemCantidadTotal = decimal("_item_cantidad_total", 32, 0)
    val itemPrecioSinIva = decimal("_item_preciosiniva", 20, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", 20, 2)
    val itemPiva = decimal("_item_piva", 10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", 20, 2)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", 15).nullable()

    // Campos FE adicionales que existen en la DB de Panamá
    val porcentajeIsc = decimal("porcentaje_isc", 10, 2).nullable()
    val importeIsc = decimal("importe_isc", 20, 2).nullable()
    val idOti = integer("id_oti").nullable()
    val importeOti = decimal("importe_oti", 20, 2).nullable()

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

/**
 * Tabla de correlativos para el número de documento fiscal.
 * Se busca con campo = 'numeroDocumentoFiscal' y se incrementa `contador`.
 */
object FECorrelativosTable : Table("correlativos") {
    val id = integer("id")
    val campo = varchar("campo", 100)
    val contador = integer("contador")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Vista de lectura sobre formas de pago asociadas a la factura.
 * Se lee desde caja_nueva_detalle y se cruza con caja_forma_pago.
 */
object FECajaNuevaDetalleReadTable : Table("caja_nueva_detalle") {
    val cajaDetalleId = varchar("caja_detalle_id", 36)
    val cajaId = varchar("caja_id", 36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val monto = decimal("monto", 10, 2).nullable()

    override val primaryKey = PrimaryKey(cajaDetalleId)
}

/**
 * Vista de lectura sobre caja_nueva para vincular con la factura.
 */
object FECajaNuevaReadTable : Table("caja_nueva") {
    val cajaId = varchar("caja_id", 36)
    val idFactura = varchar("id_factura", 36)

    override val primaryKey = PrimaryKey(cajaId)
}

/**
 * Vista de lectura sobre la tabla de detalle de forma de pago de la factura
 * para obtener el cambio (vuelto) del efectivo.
 */
object FEFacturaDetalleFormaPagoReadTable : Table("factura_detalle_formapago") {
    val codFacturaDetalleFormaPago = varchar("cod_factura_detalle_formapago", 36)
    val idFactura = varchar("id_factura", 36)
    val codigoRetencion = integer("codigo_retencion").nullable()
    val totalizarMontoRetencion = decimal("totalizar_monto_retencion", 10, 2).nullable()
    val totalizarMontoCancelar = decimal("totalizar_monto_cancelar", 10, 2).nullable()
    val totalizarCambio = decimal("totalizar_cambio", 10, 2)

    override val primaryKey = PrimaryKey(codFacturaDetalleFormaPago)
}
