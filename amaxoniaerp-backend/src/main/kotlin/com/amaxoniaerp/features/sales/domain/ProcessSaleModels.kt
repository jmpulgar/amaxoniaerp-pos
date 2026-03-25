package com.amaxoniaerp.features.sales.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessSaleRequest(
    val idFactura: String? = null,
    val codFactura: String? = null,
    val procesar: Int = 1,
    val esCobroCreditoPrevio: Boolean = false,
    val factura: SaleInvoiceInput,
    val items: List<SaleItemInput>,
    val impuestos: List<SaleTaxInput> = emptyList(),
    val pagoResumen: SalePaymentSummaryInput,
    val pagos: List<SalePaymentInput> = emptyList(),
    val moneda: SaleCurrencyInput? = null,
)

@Serializable
data class SaleCurrencyInput(
    @SerialName("multi_moneda")
    val multiMoneda: String = "NO",
    val tasa: Double = 1.0,
    @SerialName("id_tasa")
    val idTasa: Int = 0,
    @SerialName("moneda_base")
    val monedaBase: Int = 1,
    @SerialName("abr_moneda_base")
    val abrMonedaBase: String = "USD",
    @SerialName("moneda_secundaria")
    val monedaSecundaria: Int = 1,
    @SerialName("abr_moneda_secundaria")
    val abrMonedaSecundaria: String = "USD",
    @SerialName("total_ref")
    val totalRef: Double = 0.0,
)

@Serializable
data class SaleInvoiceInput(
    val idCliente: String,
    val codCliente: String,
    val codVendedor: Int,
    val idShop: Int,
    val idSucursal: Int,
    val idCaja: String,
    val codigoCaja: String,
    val idCajaSecuencia: String,
    val serieSucursal: String,
    val formaPago: String,
    val codEstatus: Int = 2,
    val subtotal: Double,
    val descuentosItemFactura: Double = 0.0,
    val ivaTotalFactura: Double,
    val totalTotalFactura: Double,
    val montoItemsFactura: Double,
    val totalizarSubTotal: Double = subtotal,
    val totalizarDescuentoParcial: Double = 0.0,
    val totalizarTotalOperacion: Double = montoItemsFactura,
    val totalizarPDescuentoGlobal: Double = 0.0,
    val totalizarDescuentoGlobal: Double = 0.0,
    val totalizarBaseImponible: Double,
    val totalizarMontoIva: Double,
    val totalizarTotalGeneral: Double,
    val usuarioCreacion: String,
    val fechaFactura: String? = null,
    val facturarA: String = "CONSUMIDOR FINAL",
    val facturarARuc: String = "CF",
    val facturarADireccion: String = "",
    val facturarATelefono: String = "",
    val codFacturaFiscal: String = "",
    val nroz: String = "0000",
    val impresoraSerial: String = "",
)

@Serializable
data class SaleItemInput(
    val idItem: Int,
    @SerialName("cod_vendedor")
    val codVendedor: Int? = null,
    @SerialName("_item_almacen")
    val itemAlmacen: Int,
    @SerialName("_item_descripcion")
    val itemDescripcion: String,
    @SerialName("_item_cantidad")
    val itemCantidad: Double,
    @SerialName("_item_preciosiniva")
    val itemPrecioSinIva: Double,
    @SerialName("_item_descuento")
    val itemDescuento: Double = 0.0,
    @SerialName("_item_montodescuento")
    val itemMontoDescuento: Double = 0.0,
    @SerialName("_item_piva")
    val itemPIva: Double,
    @SerialName("_item_totalsiniva")
    val itemTotalSinIva: Double,
    @SerialName("_item_totalconiva")
    val itemTotalConIva: Double,
    @SerialName("_item_cantidad_total")
    val itemCantidadTotal: Double,
    val esProductoFisico: Boolean = true,
    val itemCodigo: String = "",
    val itemReferencia: String = "",
    @SerialName("_posee_configuracion_lote")
    val poseeConfiguracionLote: String = "no",
    @SerialName("_codigos_lote")
    val codigosLote: List<SaleLotInput> = emptyList(),
)

@Serializable
data class SaleLotInput(
    @SerialName("id_lote_item")
    val idLoteItem: Int,
    @SerialName("codigo_lote_item")
    val codigoLoteItem: String,
    val cantidad: Int,
    @SerialName("id_almacen")
    val idAlmacen: Int = 1,
)

@Serializable
data class SaleTaxInput(
    val totalizarBaseRetencion: Double,
    val codImpuestoIva: Int,
    val totalizarMontoIva2: Double,
)

@Serializable
data class SalePaymentSummaryInput(
    val totalizarMontoCancelar: Double,
    val totalizarMontoEfectivo: Double,
    val totalizarCambio: Double,
    val totalizarSaldoPendiente: Double,
    val montosPorTipo: Map<String, Double> = emptyMap(),
)

@Serializable
data class SalePaymentInput(
    val idFormaPago: Int,
    val tipoMovimiento: String,
    val monto: Double,
    val montoRecibido: Double,
    val efectivoCambio: Double = 0.0,
)

@Serializable
data class ProcessSaleResponse(
    val success: Boolean,
    val idFactura: String,
    val codFactura: String,
    val codEstatus: Int,
)
