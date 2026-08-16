package com.amaxoniaerp.features.sales.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

private const val UUID_LENGTH = 36
private const val LEGACY_IDENTIFIER_LENGTH = 32
private const val SHORT_CODE_LENGTH = 10
private const val TYPE_CODE_LENGTH = 20
private const val SHORT_TOKEN_LENGTH = 5
private const val VERY_SHORT_CODE_LENGTH = 4
private const val RECEIPT_TYPE_LENGTH = 3
private const val COMPACT_CODE_LENGTH = 15
private const val REFERENCE_LENGTH = 30
private const val USER_LENGTH = 40
private const val STANDARD_CODE_LENGTH = 50
private const val AUDIT_USER_LENGTH = 60
private const val CONTACT_REFERENCE_LENGTH = 80
private const val STANDARD_TEXT_LENGTH = 100
private const val DISPLAY_NAME_LENGTH = 200
private const val ADDRESS_LENGTH = 250
private const val LONG_TEXT_LENGTH = 300
private const val ITEM_DESCRIPTION_LENGTH = 500
private const val OBSERVATION_LENGTH = 600
private const val MONEY_PRECISION = 20
private const val LEGACY_AMOUNT_PRECISION = 10
private const val QUANTITY_PRECISION = 32
private const val STOCK_PRECISION = 18
private const val KARDEX_PRICE_PRECISION = 9
private const val QUANTITY_SCALE = 3
private const val STOCK_SCALE = 4

/**
 * Columnas comunes de la tabla `factura` presentes en VE y PA con tipos idénticos.
 * Las columnas exclusivas de VE (nroz, impresoraSerial, multiMoneda, tasa, idTasa,
 * monedaBase, abrMonedaBase, monedaSecundaria, abrMonedaSecundaria, totalRef) van
 * únicamente en [SalesFacturaTableVE].
 */
abstract class BaseSalesFacturaTable(
    name: String = "factura",
) : Table(name) {
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val codFactura = varchar("cod_factura", LEGACY_IDENTIFIER_LENGTH)
    val codFacturaFiscal = varchar("cod_factura_fiscal", SHORT_CODE_LENGTH)
    val idCliente = varchar("id_cliente", UUID_LENGTH)
    val codVendedor = integer("cod_vendedor")
    val fechaFactura = date("fechaFactura").nullable()
    val subtotal = decimal("subtotal", MONEY_PRECISION, 2)
    val descuentosItemFactura = decimal("descuentosItemFactura", MONEY_PRECISION, 2)
    val montoItemsFactura = decimal("montoItemsFactura", MONEY_PRECISION, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", MONEY_PRECISION, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", MONEY_PRECISION, 2)
    val cantidadItems = integer("cantidad_items")
    val totalizarSubTotal = decimal("totalizar_sub_total", MONEY_PRECISION, 2)
    val totalizarDescuentoParcial = decimal("totalizar_descuento_parcial", MONEY_PRECISION, 2)
    val totalizarTotalOperacion = decimal("totalizar_total_operacion", MONEY_PRECISION, 2)
    val totalizarPDescuentoGlobal = decimal("totalizar_pdescuento_global", MONEY_PRECISION, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", MONEY_PRECISION, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", MONEY_PRECISION, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", MONEY_PRECISION, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", MONEY_PRECISION, 2)
    val totalizarTotalRetencion = decimal("totalizar_total_retencion", MONEY_PRECISION, 2)
    val formaPago = varchar("formapago", TYPE_CODE_LENGTH)
    val codEstatus = integer("cod_estatus").nullable()
    val totalBultos = decimal("total_bultos", LEGACY_AMOUNT_PRECISION, 2).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val usuarioCreacion = varchar("usuario_creacion", USER_LENGTH)
    val tipoFactura = varchar("tipo_factura", STANDARD_CODE_LENGTH)
    val modeloFactura = varchar("modelo_factura", STANDARD_TEXT_LENGTH).nullable()
    val terminoPagoId = integer("termino_pago_id").nullable()
    val facturarA = varchar("facturar_a", CONTACT_REFERENCE_LENGTH)
    val facturarARuc = varchar("facturar_a_ruc", STANDARD_CODE_LENGTH)
    val facturarADireccion = varchar("facturar_a_direccion", ADDRESS_LENGTH)
    val facturarATelefono = varchar("facturar_a_telefono", STANDARD_CODE_LENGTH)
    val validarStock = varchar("validar_stock", 2)
    val idShop = integer("id_shop")
    val servicioPeriodo = varchar("servicio_periodo", STANDARD_CODE_LENGTH)
    val servicioOrden = varchar("servicio_orden", STANDARD_CODE_LENGTH)
    val observacion = varchar("observacion", LONG_TEXT_LENGTH)
    val fechaVencimiento = date("fecha_vencimiento").nullable()
    val servicioAnio = integer("servicio_anio")
    val servicioMes = varchar("servicio_mes", 2)
    val idCajaSecuencia = varchar("id_caja_secuencia", UUID_LENGTH)
    val numcomContabilizado = integer("numcom_contabilizado")
    val fechaContabilizado = date("fecha_contabilizado")
    val serieSucursal = varchar("serie_sucursal", SHORT_CODE_LENGTH)
    val cajaSecuencia = varchar("caja_secuencia", SHORT_CODE_LENGTH)
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", UUID_LENGTH)
    val codigoCaja = varchar("codigo_caja", STANDARD_CODE_LENGTH)
    val codCliente = varchar("cod_cliente", CONTACT_REFERENCE_LENGTH)

    override val primaryKey = PrimaryKey(idFactura)
}

/** Venezuela: multimoneda, impresora fiscal y campos de tasa. */
object SalesFacturaTableVE : BaseSalesFacturaTable() {
    val nroz = varchar("nroz", VERY_SHORT_CODE_LENGTH)
    val impresoraSerial = varchar("impresora_serial", STANDARD_CODE_LENGTH)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val idTasa = integer("id_tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", SHORT_CODE_LENGTH)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", SHORT_CODE_LENGTH)
    val totalRef = float("total_ref")
}

/** Panamá: mismo esquema que VE pero sin multimoneda funcional. */
object SalesFacturaTablePA : BaseSalesFacturaTable() {
    val nroz = varchar("nroz", VERY_SHORT_CODE_LENGTH)
    val impresoraSerial = varchar("impresora_serial", STANDARD_CODE_LENGTH)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val idTasa = integer("id_tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", SHORT_CODE_LENGTH)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", SHORT_CODE_LENGTH)
    val totalRef = float("total_ref")
    val clienteSucursalId = integer("cliente_sucursal_id").nullable()
}

/** Devuelve la tabla correcta según el país. */
object SalesFacturaTableFactory {
    fun forCountry(countryCode: String): BaseSalesFacturaTable =
        when (countryCode.uppercase()) {
            "VE" -> SalesFacturaTableVE
            "PA" -> SalesFacturaTablePA
            else -> SalesFacturaTableVE
        }
}

object SalesFacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", UUID_LENGTH)
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemDescripcion = varchar("_item_descripcion", ITEM_DESCRIPTION_LENGTH)
    val itemCantidad = decimal("_item_cantidad", QUANTITY_PRECISION, QUANTITY_SCALE)
    val itemPrecioSinIva = decimal("_item_preciosiniva", MONEY_PRECISION, 2)
    val itemDescuento = decimal("_item_descuento", LEGACY_AMOUNT_PRECISION, 2)
    val itemMontoDescuento = decimal("_item_montodescuento", MONEY_PRECISION, 2)
    val itemPiva = decimal("_item_piva", LEGACY_AMOUNT_PRECISION, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", MONEY_PRECISION, 2)
    val itemTotalConIva = decimal("_item_totalconiva", MONEY_PRECISION, 2)
    val cantidadBulto = integer("_cantidad_bulto").nullable()
    val gananciaItemIndividual = decimal("_ganancia_item_individual", MONEY_PRECISION, 2).nullable()
    val porcentajeGanancia = decimal("_porcentaje_ganancia", MONEY_PRECISION, 2).nullable()
    val poseeSerial = varchar("_posee_serial", 2)
    val serialesSeleccionados = varchar("seriales_seleccionados", CONTACT_REFERENCE_LENGTH)
    val usuarioCreacion = varchar("usuario_creacion", LEGACY_IDENTIFIER_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val itemListaPrecio = varchar("_item_lista_precio", SHORT_CODE_LENGTH)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", COMPACT_CODE_LENGTH)
    val itemCantidadTotal = decimal("_item_cantidad_total", QUANTITY_PRECISION, 0)
    val promocionId = varchar("promocion_id", UUID_LENGTH)
    val promocionTipo = varchar("promocion_tipo", TYPE_CODE_LENGTH)
    val promocionCodigo = varchar("promocion_codigo", COMPACT_CODE_LENGTH)
    val promocionNombre = varchar("promocion_nombre", DISPLAY_NAME_LENGTH)
    val promocionGrupo = varchar("promocion_grupo", UUID_LENGTH)
    val promocionDetalleId = varchar("promocion_detalle_id", UUID_LENGTH)
    val promocionCantidad = decimal("promocion_cantidad", QUANTITY_PRECISION, QUANTITY_SCALE)
    val grupo = integer("grupo")
    val descuentoAutorizacion = varchar("descuento_autorizacion", UUID_LENGTH)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", STANDARD_CODE_LENGTH)
    val itemReferencia = varchar("_item_referencia", STANDARD_CODE_LENGTH)
    val idSegmento = integer("id_segmento").nullable()
    val idFamilia = integer("id_familia").nullable()

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

object SalesFacturaImpuestosTable : Table("factura_impuestos") {
    val idFacturaImpuestos = varchar("id_factura_impuestos", UUID_LENGTH)
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val totalizarBaseRetencion = decimal("totalizar_base_retencion", LEGACY_AMOUNT_PRECISION, 2)
    val codImpuestoIva = integer("cod_impuesto_iva")
    val totalizarMontoIva2 = decimal("totalizar_monto_iva2", LEGACY_AMOUNT_PRECISION, 2)
    val usuarioCreacion = varchar("usuario_creacion", STANDARD_CODE_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()

    override val primaryKey = PrimaryKey(idFacturaImpuestos)
}

abstract class BaseSalesFacturaDetalleFormaPagoTable(
    name: String = "factura_detalle_formapago",
) : Table(name) {
    val codFacturaDetalleFormaPago = varchar("cod_factura_detalle_formapago", UUID_LENGTH)
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val totalizarMontoCancelar = decimal("totalizar_monto_cancelar", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarSaldoPendiente = decimal("totalizar_saldo_pendiente", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarCambio = decimal("totalizar_cambio", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoEfectivo = decimal("totalizar_monto_efectivo", LEGACY_AMOUNT_PRECISION, 2)
    val optCheque = integer("opt_cheque")
    val totalizarMontoCheque = decimal("totalizar_monto_cheque", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarNroCheque = decimal("totalizar_nro_cheque", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarNombreBanco = integer("totalizar_nombre_banco")
    val optTarjeta = integer("opt_tarjeta")
    val totalizarMontoTarjeta = decimal("totalizar_monto_tarjeta", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarNroTarjeta = decimal("totalizar_nro_tarjeta", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarTipoTarjeta = integer("totalizar_tipo_tarjeta")
    val optDeposito = integer("opt_deposito")
    val totalizarMontoDeposito = decimal("totalizar_monto_deposito", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarNroDeposito = decimal("totalizar_nro_deposito", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarBancoDeposito = integer("totalizar_banco_deposito")
    val fechaVencimiento = date("fecha_vencimiento").nullable()
    val observacion = varchar("observacion", OBSERVATION_LENGTH)
    val personaContacto = varchar("persona_contacto", STANDARD_TEXT_LENGTH)
    val telefono = varchar("telefono", STANDARD_TEXT_LENGTH)
    val optOtroDocumento = integer("opt_otrodocumento")
    val totalizarTipoOtroDocumento = integer("totalizar_tipo_otrodocumento")
    val totalizarMontoOtroDocumento = decimal("totalizar_monto_otrodocumento", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarNroOtroDocumento = integer("totalizar_nro_otrodocumento")
    val totalizarBancoOtroDocumento = integer("totalizar_banco_otrodocumento")
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val usuarioCreacion = varchar("usuario_creacion", AUDIT_USER_LENGTH)
    val totalizarMontoCredito = decimal("totalizar_monto_credito", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoDebito = decimal("totalizar_monto_debito", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoTransferencia = decimal("totalizar_monto_transferencia", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoCertificado = decimal("totalizar_monto_certificado", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoCxc = decimal("totalizar_monto_cxc", LEGACY_AMOUNT_PRECISION, 2)
    val totalizarMontoOtros = decimal("totalizar_monto_otros", LEGACY_AMOUNT_PRECISION, 2)

    override val primaryKey = PrimaryKey(codFacturaDetalleFormaPago)
}

object SalesFacturaDetalleFormaPagoTableVE : BaseSalesFacturaDetalleFormaPagoTable() {
    val totalizarMontoDivisa = decimal("totalizar_monto_divisa", LEGACY_AMOUNT_PRECISION, 2).nullable()
}

object SalesFacturaDetalleFormaPagoTablePA : BaseSalesFacturaDetalleFormaPagoTable() {
    val codigoRetencion = varchar("codigo_retencion", STANDARD_CODE_LENGTH)
    val totalizarMontoRetencion = decimal("totalizar_monto_retencion", LEGACY_AMOUNT_PRECISION, 2)
}

object SalesFacturaDetalleFormaPagoTableFactory {
    fun forCountry(countryCode: String): BaseSalesFacturaDetalleFormaPagoTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesFacturaDetalleFormaPagoTablePA
            else -> SalesFacturaDetalleFormaPagoTableVE
        }
}

object SalesStockTable : Table("item_existencia_almacen") {
    val codAlmacen = integer("cod_almacen")
    val idItem = integer("id_item")
    val cantidad = float("cantidad")
    val cantidadMuestra = decimal("cantidad_muestra", STOCK_PRECISION, STOCK_SCALE)
    val minimo = long("minimo")
    val maximo = long("maximo")
}

object SalesCajaTable : Table("caja") {
    val id = varchar("id", UUID_LENGTH)
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val codigo = varchar("codigo", STANDARD_CODE_LENGTH).nullable()
    val facturaCorrelativo = integer("factura_correlativo")

    override val primaryKey = PrimaryKey(id)
}

object SalesSucursalTable : Table("sucursal") {
    val id = integer("id")
    val serie = varchar("serie", SHORT_CODE_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

object SalesSucursalAlmacenTable : Table("sucursal_almacen") {
    val idSucursal = integer("id_sucursal")
    val idAlmacen = integer("id_almacen")
    val defaultVentas = integer("default_ventas").nullable()

    override val primaryKey = PrimaryKey(idSucursal, idAlmacen)
}

object SalesCajaSecuenciaTable : Table("caja_secuencia") {
    val id = varchar("id", UUID_LENGTH)
    val secuencia = varchar("secuencia", SHORT_CODE_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

abstract class BaseSalesKardexTable(
    name: String = "kardex_almacen",
) : Table(name) {
    val idTransaccion = varchar("id_transaccion", UUID_LENGTH)
    val tipoMovimientoAlmacen = integer("tipo_movimiento_almacen")
    val autorizadoPor = varchar("autorizado_por", STANDARD_TEXT_LENGTH)
    val observacion = varchar("observacion", DISPLAY_NAME_LENGTH)
    val fecha = date("fecha")
    val usuarioCreacion = varchar("usuario_creacion", STANDARD_TEXT_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val estado = varchar("estado", TYPE_CODE_LENGTH)
    val idDocumento = varchar("id_documento", UUID_LENGTH)
    val codProveedor = integer("cod_proveedor")
    val comprobante = varchar("comprobante", REFERENCE_LENGTH)
    val anio = integer("anio")
    val tipoCosto = varchar("tipo_costo", SHORT_TOKEN_LENGTH)
    val estatus = integer("estatus")
    val entregadoACodigo = varchar("entregado_a_codigo", SHORT_CODE_LENGTH)
    val entregadoANombre = varchar("entregado_a_nombre", REFERENCE_LENGTH)
    val codDocumento = varchar("cod_documento", STANDARD_CODE_LENGTH)
    val subtipoMovimientoAlmacen = integer("subtipo_movimiento_almacen")
    val contabilizado = integer("contabilizado")
    val fechaContabilizacion = date("fecha_contabilizacion")
    val usuarioContabilizacion = varchar("usuario_contabilizacion", STANDARD_CODE_LENGTH)
    val idAlmacenSalida = integer("id_almacen_salida").nullable()
    val idSucursal = integer("id_sucursal")
    val validadoFecha = date("validado_fecha")
    val validadoUsuario = varchar("validado_usuario", TYPE_CODE_LENGTH)
    val validadoObservacion = varchar("validado_observacion", DISPLAY_NAME_LENGTH)

    override val primaryKey = PrimaryKey(idTransaccion)
}

object SalesKardexTableVE : BaseSalesKardexTable()

object SalesKardexTablePA : BaseSalesKardexTable() {
    val controlaStock = integer("controla_stock")
}

object SalesKardexTableFactory {
    fun forCountry(countryCode: String): BaseSalesKardexTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesKardexTablePA
            else -> SalesKardexTableVE
        }
}

abstract class BaseSalesKardexDetalleTable(
    name: String = "kardex_almacen_detalle",
) : Table(name) {
    val idTransaccionDetalle = varchar("id_transaccion_detalle", UUID_LENGTH)
    val idTransaccion = varchar("id_transaccion", UUID_LENGTH)
    val idAlmacenEntrada = integer("id_almacen_entrada")
    val idAlmacenSalida = integer("id_almacen_salida")
    val idItem = integer("id_item")
    val cantidad = float("cantidad")
    val cantidadDistribuida = integer("cantidad_distribuida")
    val precio = decimal("precio", KARDEX_PRICE_PRECISION, 2)
    val cantidadMuestra = integer("cantidad_muestra")
    val unidadBulto = varchar("unidad_bulto", CONTACT_REFERENCE_LENGTH)
    val cantidadBulto = decimal("cantidad_bulto", LEGACY_AMOUNT_PRECISION, 2)
    val unidadEmpaque = varchar("unidad_empaque", COMPACT_CODE_LENGTH)
    val cantidadTotal = decimal("cantidad_total", QUANTITY_PRECISION, 2)
    val costo = decimal("costo", LEGACY_AMOUNT_PRECISION, 2)

    override val primaryKey = PrimaryKey(idTransaccionDetalle)
}

object SalesKardexDetalleTableVE : BaseSalesKardexDetalleTable()

object SalesKardexDetalleTablePA : BaseSalesKardexDetalleTable() {
    val idCentroCosto = integer("id_centro_costo")
    val idLoteItem = integer("id_lote_item")
}

object SalesKardexDetalleTableFactory {
    fun forCountry(countryCode: String): BaseSalesKardexDetalleTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesKardexDetalleTablePA
            else -> SalesKardexDetalleTableVE
        }
}

// ─── CajaNueva ────────────────────────────────────────────────────────────────

/**
 * Columnas comunes de `caja_nueva` en VE y PA.
 * VE añade [SalesCajaNuevaTableVE]; PA no tiene columnas extra aquí.
 */
abstract class BaseSalesCajaNuevaTable(
    name: String = "caja_nueva",
) : Table(name) {
    val cajaId = varchar("caja_id", UUID_LENGTH)
    val idTransaccion = varchar("id_transaccion", UUID_LENGTH)
    val fecha = date("fecha").nullable()
    val ingEg = enumerationByName("ing_eg", 1, CajaIngresoEgreso::class).nullable()
    val monto = decimal("monto", LEGACY_AMOUNT_PRECISION, 2).nullable()
    val comprobante = varchar("comprobante", TYPE_CODE_LENGTH)
    val comprobanteNumero = varchar("comprobante_numero", STANDARD_CODE_LENGTH)
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val idCliente = varchar("id_cliente", UUID_LENGTH)
    val status = enumerationByName("status", SHORT_CODE_LENGTH, CajaStatus::class)
    val sucursalId = integer("sucursal_id").nullable()
    val usuarioCreacion = varchar("usuario_creacion", TYPE_CODE_LENGTH).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val idCompra = varchar("id_compra", UUID_LENGTH)
    val idProveedor = varchar("id_proveedor", UUID_LENGTH)
    val concepto = varchar("concepto", LONG_TEXT_LENGTH).nullable()
    val idOrdenPago = varchar("id_ordenpago", UUID_LENGTH)
    val serieSucursal = varchar("serie_sucursal", SHORT_CODE_LENGTH)
    val idCajaSecuencia = varchar("id_caja_secuencia", UUID_LENGTH)
    val idPedido = varchar("id_pedido", UUID_LENGTH)
    val idAbono = varchar("id_abono", UUID_LENGTH)
    val idNotaCredito = varchar("id_notacredito", UUID_LENGTH)

    override val primaryKey = PrimaryKey(cajaId)
}

/** Venezuela: sin columnas extra en caja_nueva vs la base. */
object SalesCajaNuevaTableVE : BaseSalesCajaNuevaTable()

/** Panamá: sin columnas extra en caja_nueva vs la base. */
object SalesCajaNuevaTablePA : BaseSalesCajaNuevaTable()

/** Devuelve la tabla correcta según el país. */
object SalesCajaNuevaTableFactory {
    fun forCountry(countryCode: String): BaseSalesCajaNuevaTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesCajaNuevaTablePA
            else -> SalesCajaNuevaTableVE
        }
}

// ─── CajaNuevaDetalle ─────────────────────────────────────────────────────────

/**
 * Columnas comunes de `caja_nueva_detalle` en VE y PA.
 * VE añade [SalesCajaNuevaDetalleTableVE] con monto_recibido y monto_moneda_principal
 * que no existen en el esquema de Panamá.
 */
abstract class BaseSalesCajaNuevaDetalleTable(
    name: String = "caja_nueva_detalle",
) : Table(name) {
    val cajaDetalleId = varchar("caja_detalle_id", UUID_LENGTH)
    val cajaId = varchar("caja_id", UUID_LENGTH)
    val idFormaPago = integer("id_forma_pago").nullable()
    val idTransaccion = varchar("id_transaccion", UUID_LENGTH)
    val cajaReciboId = varchar("caja_recibo_id", UUID_LENGTH)
    val monto = decimal("monto", LEGACY_AMOUNT_PRECISION, 2).nullable()
    val montoOriginal = decimal("monto_original", LEGACY_AMOUNT_PRECISION, 2)
    val concepto = varchar("concepto", LONG_TEXT_LENGTH).nullable()
    val usuarioCreacion = varchar("usuario_creacion", TYPE_CODE_LENGTH).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val retencionTipo = varchar("retencion_tipo", SHORT_CODE_LENGTH)
    val retencionPorcentaje = varchar("retencion_porcentaje", LONG_TEXT_LENGTH)
    val numero = varchar("numero", LONG_TEXT_LENGTH)
    val observacion = varchar("observacion", LONG_TEXT_LENGTH)
    val retencionBaseCalculo = varchar("retencion_base_calculo", LONG_TEXT_LENGTH)
    val serieSucursal = varchar("serie_sucursal", SHORT_CODE_LENGTH)
    val cajaSecuencia = varchar("caja_secuencia", UUID_LENGTH)
    val numeroControl = varchar("numero_control", LONG_TEXT_LENGTH)
    val numeroComprobante = varchar("numero_comprobante", LONG_TEXT_LENGTH)
    val retencionMonto = varchar("retencion_monto", LONG_TEXT_LENGTH)
    val retencionDetalleJson = text("retencion_detalle_json")

    override val primaryKey = PrimaryKey(cajaDetalleId)
}

/** Venezuela: campos de multimoneda en el detalle de caja. */
object SalesCajaNuevaDetalleTableVE : BaseSalesCajaNuevaDetalleTable() {
    val montoRecibido = decimal("monto_recibido", LEGACY_AMOUNT_PRECISION, 2)
    val montoMonedaPrincipal = decimal("monto_moneda_principal", LEGACY_AMOUNT_PRECISION, 2)
}

/** Panamá: sin campos de multimoneda en el detalle de caja. */
object SalesCajaNuevaDetalleTablePA : BaseSalesCajaNuevaDetalleTable()

/** Devuelve la tabla correcta según el país. */
object SalesCajaNuevaDetalleTableFactory {
    fun forCountry(countryCode: String): BaseSalesCajaNuevaDetalleTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesCajaNuevaDetalleTablePA
            else -> SalesCajaNuevaDetalleTableVE
        }
}

object SalesCajaNuevaDetalleFormaPagoTable : Table("caja_nueva_detalle_forma_pago") {
    val cajaDetalleFormaPagoId = varchar("caja_detalle_forma_pago_id", UUID_LENGTH)
    val cajaId = varchar("caja_id", UUID_LENGTH)
    val cajaDetalleId = varchar("caja_detalle_id", UUID_LENGTH)
    val tipoMovimiento = varchar("tipo_movimiento", SHORT_TOKEN_LENGTH).nullable()
    val idFormaPago = integer("id_forma_pago").nullable()
    val comprobante = varchar("comprobante", STANDARD_CODE_LENGTH)
    val concepto = varchar("concepto", LONG_TEXT_LENGTH)
    val monto = decimal("monto", LEGACY_AMOUNT_PRECISION, 2).nullable()
    val montoOriginal = decimal("monto_original", LEGACY_AMOUNT_PRECISION, 2)
    val tdcProveedor = varchar("tdc_proveedor", STANDARD_CODE_LENGTH)
    val tdcNumero = varchar("tdc_numero", STANDARD_CODE_LENGTH)
    val tdcTitular = varchar("tdc_titular", STANDARD_CODE_LENGTH)
    val tdcVencimiento = varchar("tdc_vencimiento", SHORT_CODE_LENGTH)
    val tdcCvv = varchar("tdc_cvv", SHORT_TOKEN_LENGTH)
    val codigoVerificacion = varchar("codigo_verificacion", STANDARD_CODE_LENGTH)
    val idAbonoDetalle = varchar("id_abono_detalle", UUID_LENGTH)
    val efectivoCambio = decimal("efectivo_cambio", LEGACY_AMOUNT_PRECISION, 2).nullable()

    override val primaryKey = PrimaryKey(cajaDetalleFormaPagoId)
}

abstract class BaseSalesCajaNuevaReciboTable(
    name: String = "caja_nueva_recibo",
) : Table(name) {
    val cajaReciboId = varchar("caja_recibo_id", UUID_LENGTH)
    val tipoRecibo = varchar("tipo_recibo", RECEIPT_TYPE_LENGTH)
    val nroRecibo = varchar("nro_recibo", LEGACY_IDENTIFIER_LENGTH)
    val fecha = date("fecha")
    val monto = decimal("monto", LEGACY_AMOUNT_PRECISION, 2)
    val observacion = text("observacion").nullable()
    val codVendedor = integer("cod_vendedor").nullable()
    val idCliente = varchar("id_cliente", UUID_LENGTH)
    val idProveedor = varchar("id_proveedor", UUID_LENGTH)
    val usuarioCreacion = varchar("usuario_creacion", TYPE_CODE_LENGTH)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val status = varchar("status", 2)
    val contabilizado = integer("contabilizado")
    val numcomContabilizado = integer("numcom_contabilizado")
    val fechaContabilizado = date("fecha_contabilizado")
    val idFactura = varchar("id_factura", UUID_LENGTH)
    val idPedido = varchar("id_pedido", UUID_LENGTH)
    val idAbono = varchar("id_abono", UUID_LENGTH)
    val idTransaccion = varchar("id_transaccion", UUID_LENGTH)
    val nroReferencia = varchar("nro_referencia", TYPE_CODE_LENGTH)
    val tipoPagoSubtipo = integer("tipo_pago_subtipo")

    override val primaryKey = PrimaryKey(cajaReciboId)
}

object SalesCajaNuevaReciboTableVE : BaseSalesCajaNuevaReciboTable() {
    val idConsignacion = varchar("id_consignacion", UUID_LENGTH)
}

object SalesCajaNuevaReciboTablePA : BaseSalesCajaNuevaReciboTable()

object SalesCajaNuevaReciboTableFactory {
    fun forCountry(countryCode: String): BaseSalesCajaNuevaReciboTable =
        when (countryCode.uppercase()) {
            "PA" -> SalesCajaNuevaReciboTablePA
            else -> SalesCajaNuevaReciboTableVE
        }
}

enum class CajaIngresoEgreso { I, E }

enum class CajaStatus { Pendiente, Pagada, Anulada }
