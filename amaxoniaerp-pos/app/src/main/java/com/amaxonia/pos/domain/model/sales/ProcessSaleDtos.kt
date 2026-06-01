package com.amaxonia.pos.domain.model.sales

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessSaleRequestDto(
    val idFactura: String? = null,
    val codFactura: String? = null,
    val procesar: Int = 1,
    val esCobroCreditoPrevio: Boolean = false,
    val factura: SaleInvoiceDto,
    val items: List<SaleItemDto>,
    val impuestos: List<SaleTaxDto> = emptyList(),
    val pagoResumen: SalePaymentSummaryDto,
    val pagos: List<SalePaymentDto>,
    val moneda: SaleCurrencyDto? = null,
)

@Serializable
data class SaleCurrencyDto(
    @SerialName("multi_moneda")
    val multiMoneda: String,
    val tasa: Double,
    @SerialName("id_tasa")
    val idTasa: Int,
    @SerialName("moneda_base")
    val monedaBase: Int,
    @SerialName("abr_moneda_base")
    val abrMonedaBase: String,
    @SerialName("moneda_secundaria")
    val monedaSecundaria: Int,
    @SerialName("abr_moneda_secundaria")
    val abrMonedaSecundaria: String,
    @SerialName("total_ref")
    val totalRef: Double,
)

@Serializable
data class SaleInvoiceDto(
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
    val codEstatus: Int,
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
    val facturarA: String,
    val facturarARuc: String,
    val facturarADireccion: String,
    val facturarATelefono: String,
    val codFacturaFiscal: String = "",
    val nroz: String = "0000",
    val impresoraSerial: String = ""
)

@Serializable
data class SaleItemDto(
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
    val codigosLote: List<SaleLotDto> = emptyList(),
    val promocionTipo: String = "",
    val promocionCodigo: String = "",
    val promocionNombre: String = "",
    val promocionGrupo: String = "",
    val promocionDetalleId: String = "",
)

@Serializable
data class SaleLotDto(
    @SerialName("id_lote_item")
    val idLoteItem: Int,
    @SerialName("codigo_lote_item")
    val codigoLoteItem: String,
    val cantidad: Int,
    @SerialName("id_almacen")
    val idAlmacen: Int = 1,
)

@Serializable
data class SaleTaxDto(
    val totalizarBaseRetencion: Double,
    val codImpuestoIva: Int,
    val totalizarMontoIva2: Double
)

@Serializable
data class SalePaymentSummaryDto(
    val totalizarMontoCancelar: Double,
    val totalizarMontoEfectivo: Double,
    val totalizarCambio: Double,
    val totalizarSaldoPendiente: Double,
    val montosPorTipo: Map<String, Double>
)

@Serializable
data class SalePaymentDto(
    val idFormaPago: Int,
    val tipoMovimiento: String,
    val monto: Double,
    val montoRecibido: Double,
    val efectivoCambio: Double = 0.0
)

@Serializable
data class ProcessSaleResponseDto(
    val success: Boolean,
    val idFactura: String,
    val codFactura: String,
    val codEstatus: Int
)

@Serializable
data class ConfirmFacturaFiscalRequestDto(
    val numeroDocumentoFiscal: String = "",
    val codFacturaFiscal: String = "",
    val impresoraSerial: String = "",
)

@Serializable
data class ConfirmFacturaFiscalResponseDto(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val numeroDocumentoFiscal: String,
    val codFacturaFiscal: String,
    val impresoraSerial: String,
)
