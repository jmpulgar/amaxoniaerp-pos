package com.amaxoniaerp.features.sales.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object SalesFacturaTable : Table("factura") {
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", 10)
    val nroz = varchar("nroz", 4)
    val impresoraSerial = varchar("impresora_serial", 50)
    val idCliente = varchar("id_cliente", 36)
    val codVendedor = integer("cod_vendedor")
    val fechaFactura = date("fechaFactura").nullable()
    val subtotal = decimal("subtotal", 20, 2)
    val descuentosItemFactura = decimal("descuentosItemFactura", 20, 2)
    val montoItemsFactura = decimal("montoItemsFactura", 20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", 20, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", 20, 2)
    val cantidadItems = integer("cantidad_items")
    val totalizarSubTotal = decimal("totalizar_sub_total", 20, 2)
    val totalizarDescuentoParcial = decimal("totalizar_descuento_parcial", 20, 2)
    val totalizarTotalOperacion = decimal("totalizar_total_operacion", 20, 2)
    val totalizarPDescuentoGlobal = decimal("totalizar_pdescuento_global", 20, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", 20, 2)
    val totalizarBaseImponible = decimal("totalizar_base_imponible", 20, 2)
    val totalizarMontoIva = decimal("totalizar_monto_iva", 20, 2)
    val totalizarTotalGeneral = decimal("totalizar_total_general", 20, 2)
    val totalizarTotalRetencion = decimal("totalizar_total_retencion", 20, 2)
    val formaPago = varchar("formapago", 20)
    val codEstatus = integer("cod_estatus").nullable()
    val totalBultos = decimal("total_bultos", 10, 2).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val usuarioCreacion = varchar("usuario_creacion", 40)
    val tipoFactura = varchar("tipo_factura", 50)
    val modeloFactura = varchar("modelo_factura", 100).nullable()
    val terminoPagoId = integer("termino_pago_id").nullable()
    val facturarA = varchar("facturar_a", 80)
    val facturarARuc = varchar("facturar_a_ruc", 50)
    val facturarADireccion = varchar("facturar_a_direccion", 250)
    val facturarATelefono = varchar("facturar_a_telefono", 50)
    val validarStock = varchar("validar_stock", 2)
    val idShop = integer("id_shop")
    val servicioPeriodo = varchar("servicio_periodo", 50)
    val servicioOrden = varchar("servicio_orden", 50)
    val observacion = varchar("observacion", 300)
    val fechaVencimiento = date("fecha_vencimiento").nullable()
    val servicioAnio = integer("servicio_anio")
    val servicioMes = varchar("servicio_mes", 2)
    val idCajaSecuencia = varchar("id_caja_secuencia", 36)
    val numcomContabilizado = integer("numcom_contabilizado")
    val fechaContabilizado = date("fecha_contabilizado")
    val serieSucursal = varchar("serie_sucursal", 10)
    val cajaSecuencia = varchar("caja_secuencia", 10)
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", 36)
    val codigoCaja = varchar("codigo_caja", 50)
    val codCliente = varchar("cod_cliente", 80)
    val multiMoneda = varchar("multi_moneda", 2)
    val tasa = float("tasa")
    val idTasa = integer("id_tasa")
    val monedaBase = integer("moneda_base")
    val abrMonedaBase = varchar("abr_moneda_base", 10)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", 10)
    val totalRef = float("total_ref")

    override val primaryKey = PrimaryKey(idFactura)
}

object SalesFacturaDetalleTable : Table("factura_detalle") {
    val idDetalleFactura = varchar("id_detalle_factura", 36)
    val idFactura = varchar("id_factura", 36)
    val idItem = integer("id_item")
    val itemAlmacen = integer("_item_almacen")
    val itemDescripcion = varchar("_item_descripcion", 500)
    val itemCantidad = decimal("_item_cantidad", 32, 3)
    val itemPrecioSinIva = decimal("_item_preciosiniva", 20, 2)
    val itemPiva = decimal("_item_piva", 10, 2)
    val itemTotalSinIva = decimal("_item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("_item_totalconiva", 20, 2)
    val cantidadBulto = integer("_cantidad_bulto").nullable()
    val gananciaItemIndividual = decimal("_ganancia_item_individual", 20, 2).nullable()
    val porcentajeGanancia = decimal("_porcentaje_ganancia", 20, 2).nullable()
    val poseeSerial = varchar("_posee_serial", 2)
    val serialesSeleccionados = varchar("seriales_seleccionados", 80)
    val usuarioCreacion = varchar("usuario_creacion", 32)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val itemListaPrecio = varchar("_item_lista_precio", 10)
    val itemUnidadEmpaque = varchar("_item_unidad_empaque", 15)
    val itemCantidadTotal = decimal("_item_cantidad_total", 32, 0)
    val promocionTipo = varchar("promocion_tipo", 20)
    val promocionCodigo = varchar("promocion_codigo", 15)
    val promocionNombre = varchar("promocion_nombre", 200)
    val promocionGrupo = varchar("promocion_grupo", 36)
    val promocionDetalleId = varchar("promocion_detalle_id", 36)
    val grupo = integer("grupo")
    val descuentoAutorizacion = varchar("descuento_autorizacion", 36)
    val codVendedor = integer("cod_vendedor")
    val itemCodigo = varchar("_item_codigo", 50)
    val itemReferencia = varchar("_item_referencia", 50)

    override val primaryKey = PrimaryKey(idDetalleFactura)
}

object SalesFacturaImpuestosTable : Table("factura_impuestos") {
    val idFacturaImpuestos = varchar("id_factura_impuestos", 36)
    val idFactura = varchar("id_factura", 36)
    val totalizarBaseRetencion = decimal("totalizar_base_retencion", 10, 2)
    val codImpuestoIva = integer("cod_impuesto_iva")
    val totalizarMontoIva2 = decimal("totalizar_monto_iva2", 10, 2)
    val usuarioCreacion = varchar("usuario_creacion", 50)
    val fechaCreacion = datetime("fecha_creacion").nullable()

    override val primaryKey = PrimaryKey(idFacturaImpuestos)
}

object SalesFacturaDetalleFormaPagoTable : Table("factura_detalle_formapago") {
    val codFacturaDetalleFormaPago = varchar("cod_factura_detalle_formapago", 36)
    val idFactura = varchar("id_factura", 36)
    val totalizarMontoCancelar = decimal("totalizar_monto_cancelar", 10, 2)
    val totalizarSaldoPendiente = decimal("totalizar_saldo_pendiente", 10, 2)
    val totalizarCambio = decimal("totalizar_cambio", 10, 2)
    val totalizarMontoEfectivo = decimal("totalizar_monto_efectivo", 10, 2)
    val optCheque = integer("opt_cheque")
    val totalizarMontoCheque = decimal("totalizar_monto_cheque", 10, 2)
    val totalizarNroCheque = decimal("totalizar_nro_cheque", 10, 2)
    val totalizarNombreBanco = integer("totalizar_nombre_banco")
    val optTarjeta = integer("opt_tarjeta")
    val totalizarMontoTarjeta = decimal("totalizar_monto_tarjeta", 10, 2)
    val totalizarNroTarjeta = decimal("totalizar_nro_tarjeta", 10, 2)
    val totalizarTipoTarjeta = integer("totalizar_tipo_tarjeta")
    val optDeposito = integer("opt_deposito")
    val totalizarMontoDeposito = decimal("totalizar_monto_deposito", 10, 2)
    val totalizarNroDeposito = decimal("totalizar_nro_deposito", 10, 2)
    val totalizarBancoDeposito = integer("totalizar_banco_deposito")
    val fechaVencimiento = date("fecha_vencimiento").nullable()
    val observacion = varchar("observacion", 600)
    val personaContacto = varchar("persona_contacto", 100)
    val telefono = varchar("telefono", 100)
    val optOtroDocumento = integer("opt_otrodocumento")
    val totalizarTipoOtroDocumento = integer("totalizar_tipo_otrodocumento")
    val totalizarMontoOtroDocumento = decimal("totalizar_monto_otrodocumento", 10, 2)
    val totalizarNroOtroDocumento = integer("totalizar_nro_otrodocumento")
    val totalizarBancoOtroDocumento = integer("totalizar_banco_otrodocumento")
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val usuarioCreacion = varchar("usuario_creacion", 60)
    val totalizarMontoCredito = decimal("totalizar_monto_credito", 10, 2)
    val totalizarMontoDebito = decimal("totalizar_monto_debito", 10, 2)
    val totalizarMontoTransferencia = decimal("totalizar_monto_transferencia", 10, 2)
    val totalizarMontoCertificado = decimal("totalizar_monto_certificado", 10, 2)
    val totalizarMontoCxc = decimal("totalizar_monto_cxc", 10, 2)
    val totalizarMontoOtros = decimal("totalizar_monto_otros", 10, 2)
    val totalizarMontoDivisa = decimal("totalizar_monto_divisa", 10, 2).nullable()

    override val primaryKey = PrimaryKey(codFacturaDetalleFormaPago)
}

object SalesStockTable : Table("item_existencia_almacen") {
    val codAlmacen = integer("cod_almacen")
    val idItem = integer("id_item")
    val cantidad = float("cantidad")
}

object SalesCajaTable : Table("caja") {
    val id = varchar("id", 36)
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val codigo = varchar("codigo", 50).nullable()
    val facturaCorrelativo = integer("factura_correlativo")

    override val primaryKey = PrimaryKey(id)
}

object SalesSucursalTable : Table("sucursal") {
    val id = integer("id")
    val serie = varchar("serie", 10).nullable()

    override val primaryKey = PrimaryKey(id)
}

object SalesSucursalAlmacenTable : Table("sucursal_almacen") {
    val idSucursal = integer("id_sucursal")
    val idAlmacen = integer("id_almacen")
    val defaultVentas = integer("default_ventas").nullable()

    override val primaryKey = PrimaryKey(idSucursal, idAlmacen)
}

object SalesCajaSecuenciaTable : Table("caja_secuencia") {
    val id = varchar("id", 36)
    val secuencia = varchar("secuencia", 10).nullable()

    override val primaryKey = PrimaryKey(id)
}

object SalesKardexTable : Table("kardex_almacen") {
    val idTransaccion = varchar("id_transaccion", 36)
    val tipoMovimientoAlmacen = integer("tipo_movimiento_almacen")
    val autorizadoPor = varchar("autorizado_por", 100)
    val observacion = varchar("observacion", 200)
    val fecha = date("fecha")
    val usuarioCreacion = varchar("usuario_creacion", 100)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val estado = varchar("estado", 20)
    val idDocumento = varchar("id_documento", 36)
    val codProveedor = integer("cod_proveedor")
    val comprobante = varchar("comprobante", 30)
    val anio = integer("anio")
    val tipoCosto = varchar("tipo_costo", 5)
    val estatus = integer("estatus")
    val entregadoACodigo = varchar("entregado_a_codigo", 10)
    val entregadoANombre = varchar("entregado_a_nombre", 30)
    val codDocumento = varchar("cod_documento", 50)
    val subtipoMovimientoAlmacen = integer("subtipo_movimiento_almacen")
    val contabilizado = integer("contabilizado")
    val fechaContabilizacion = date("fecha_contabilizacion")
    val usuarioContabilizacion = varchar("usuario_contabilizacion", 50)
    val idAlmacenSalida = integer("id_almacen_salida").nullable()
    val idSucursal = integer("id_sucursal")
    val validadoFecha = date("validado_fecha")
    val validadoUsuario = varchar("validado_usuario", 20)
    val validadoObservacion = varchar("validado_observacion", 200)

    override val primaryKey = PrimaryKey(idTransaccion)
}

object SalesKardexDetalleTable : Table("kardex_almacen_detalle") {
    val idTransaccionDetalle = varchar("id_transaccion_detalle", 36)
    val idTransaccion = varchar("id_transaccion", 36)
    val idAlmacenEntrada = integer("id_almacen_entrada")
    val idAlmacenSalida = integer("id_almacen_salida")
    val idItem = integer("id_item")
    val cantidad = float("cantidad")
    val cantidadDistribuida = integer("cantidad_distribuida")
    val precio = decimal("precio", 9, 2)
    val cantidadMuestra = integer("cantidad_muestra")
    val unidadBulto = varchar("unidad_bulto", 80)
    val cantidadBulto = decimal("cantidad_bulto", 10, 2)
    val unidadEmpaque = varchar("unidad_empaque", 15)
    val cantidadTotal = decimal("cantidad_total", 32, 2)
    val costo = decimal("costo", 10, 2)

    override val primaryKey = PrimaryKey(idTransaccionDetalle)
}

object SalesCajaNuevaTable : Table("caja_nueva") {
    val cajaId = varchar("caja_id", 36)
    val idTransaccion = varchar("id_transaccion", 36)
    val fecha = date("fecha").nullable()
    val ingEg = enumerationByName("ing_eg", 1, CajaIngresoEgreso::class).nullable()
    val monto = decimal("monto", 10, 2).nullable()
    val comprobante = varchar("comprobante", 20)
    val comprobanteNumero = varchar("comprobante_numero", 50)
    val idFactura = varchar("id_factura", 36)
    val idCliente = varchar("id_cliente", 36)
    val status = enumerationByName("status", 10, CajaStatus::class)
    val sucursalId = integer("sucursal_id").nullable()
    val usuarioCreacion = varchar("usuario_creacion", 20).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val idCompra = varchar("id_compra", 36)
    val idProveedor = varchar("id_proveedor", 36)
    val concepto = varchar("concepto", 300).nullable()
    val idOrdenPago = varchar("id_ordenpago", 36)
    val serieSucursal = varchar("serie_sucursal", 10)
    val idCajaSecuencia = varchar("id_caja_secuencia", 36)
    val idPedido = varchar("id_pedido", 36)
    val idAbono = varchar("id_abono", 36)
    val idNotaCredito = varchar("id_notacredito", 36)

    override val primaryKey = PrimaryKey(cajaId)
}

object SalesCajaNuevaDetalleTable : Table("caja_nueva_detalle") {
    val cajaDetalleId = varchar("caja_detalle_id", 36)
    val cajaId = varchar("caja_id", 36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val idTransaccion = varchar("id_transaccion", 36)
    val cajaReciboId = varchar("caja_recibo_id", 36)
    val monto = decimal("monto", 10, 2).nullable()
    val montoOriginal = decimal("monto_original", 10, 2)
    val concepto = varchar("concepto", 300).nullable()
    val usuarioCreacion = varchar("usuario_creacion", 20).nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val retencionTipo = varchar("retencion_tipo", 10)
    val retencionPorcentaje = varchar("retencion_porcentaje", 300)
    val numero = varchar("numero", 300)
    val observacion = varchar("observacion", 300)
    val retencionBaseCalculo = varchar("retencion_base_calculo", 300)
    val serieSucursal = varchar("serie_sucursal", 10)
    val cajaSecuencia = varchar("caja_secuencia", 36)
    val numeroControl = varchar("numero_control", 300)
    val numeroComprobante = varchar("numero_comprobante", 300)
    val retencionMonto = varchar("retencion_monto", 300)
    val retencionDetalleJson = text("retencion_detalle_json")
    val montoRecibido = decimal("monto_recibido", 10, 2)
    val montoMonedaPrincipal = decimal("monto_moneda_principal", 10, 2)

    override val primaryKey = PrimaryKey(cajaDetalleId)
}

object SalesCajaNuevaDetalleFormaPagoTable : Table("caja_nueva_detalle_forma_pago") {
    val cajaDetalleFormaPagoId = varchar("caja_detalle_forma_pago_id", 36)
    val cajaId = varchar("caja_id", 36)
    val cajaDetalleId = varchar("caja_detalle_id", 36)
    val tipoMovimiento = varchar("tipo_movimiento", 5).nullable()
    val idFormaPago = integer("id_forma_pago").nullable()
    val comprobante = varchar("comprobante", 50)
    val concepto = varchar("concepto", 300)
    val monto = decimal("monto", 10, 2).nullable()
    val montoOriginal = decimal("monto_original", 10, 2)
    val tdcProveedor = varchar("tdc_proveedor", 50)
    val tdcNumero = varchar("tdc_numero", 50)
    val tdcTitular = varchar("tdc_titular", 50)
    val tdcVencimiento = varchar("tdc_vencimiento", 10)
    val tdcCvv = varchar("tdc_cvv", 5)
    val codigoVerificacion = varchar("codigo_verificacion", 50)
    val idAbonoDetalle = varchar("id_abono_detalle", 36)
    val efectivoCambio = decimal("efectivo_cambio", 10, 2).nullable()

    override val primaryKey = PrimaryKey(cajaDetalleFormaPagoId)
}

object SalesCajaNuevaReciboTable : Table("caja_nueva_recibo") {
    val cajaReciboId = varchar("caja_recibo_id", 36)
    val tipoRecibo = varchar("tipo_recibo", 3)
    val nroRecibo = varchar("nro_recibo", 32)
    val fecha = date("fecha")
    val monto = decimal("monto", 10, 2)
    val observacion = text("observacion").nullable()
    val codVendedor = integer("cod_vendedor").nullable()
    val idCliente = varchar("id_cliente", 36)
    val idProveedor = varchar("id_proveedor", 36)
    val usuarioCreacion = varchar("usuario_creacion", 20)
    val fechaCreacion = datetime("fecha_creacion").nullable()
    val status = varchar("status", 2)
    val contabilizado = integer("contabilizado")
    val numcomContabilizado = integer("numcom_contabilizado")
    val fechaContabilizado = date("fecha_contabilizado")
    val idFactura = varchar("id_factura", 36)
    val idPedido = varchar("id_pedido", 36)
    val idAbono = varchar("id_abono", 36)
    val idTransaccion = varchar("id_transaccion", 36)
    val nroReferencia = varchar("nro_referencia", 20)
    val tipoPagoSubtipo = integer("tipo_pago_subtipo")

    override val primaryKey = PrimaryKey(cajaReciboId)
}

object SalesTasasCambioTable : Table("tasas_cambio") {
    val id = long("id")
    val facturado = varchar("facturado", 1)

    override val primaryKey = PrimaryKey(id)
}

enum class CajaIngresoEgreso { I, E }

enum class CajaStatus { Pendiente, Pagada, Anulada }
