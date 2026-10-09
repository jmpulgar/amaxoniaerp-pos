package com.amaxonia.erp.data.remote.dto

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
    val pagos: List<SalePaymentDto> = emptyList(),
    val moneda: SaleCurrencyDto? = null,
    val useHka20: Boolean = false,
)

@Serializable
data class SaleCurrencyDto(
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
data class SaleInvoiceDto(
    val idCliente: String,
    val codCliente: String,
    val codVendedor: Int = 1,
    val idShop: Int = 1,
    val idSucursal: Int,
    val idCaja: String,
    val codigoCaja: String,
    val idCajaSecuencia: String = "0",
    val serieSucursal: String = "001",
    val formaPago: String = "EFECTIVO",
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
    val clienteSucursalId: Int? = null,
    val codFacturaFiscal: String = "",
    val nroz: String = "0000",
    val impresoraSerial: String = "",
    val observacion: String = "",
)

@Serializable
data class SaleItemDto(
    val idItem: Int,
    @SerialName("cod_vendedor")
    val codVendedor: Int? = null,
    @SerialName("_item_almacen")
    val itemAlmacen: Int = 1,
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
    @SerialName("_cantidad_bulto")
    val cantidadBulto: Int = 1,
    @SerialName("_unidad_empaque")
    val unidadEmpaque: String = "UNIDAD",
    @SerialName("_item_unidad_empaque")
    val itemUnidadEmpaque: String = "UNIDAD",
    val esProductoFisico: Boolean = true,
    val itemCodigo: String = "",
    val itemReferencia: String = "",
    @SerialName("id_segmento")
    val idSegmento: Int? = null,
    @SerialName("id_familia")
    val idFamilia: Int? = null,
    val promocionTipo: String = "",
    val promocionId: String = "",
    val promocionCantidad: Double = 0.0,
    val promocionCodigo: String = "",
    val promocionNombre: String = "",
)

@Serializable
data class SaleTaxDto(
    val totalizarBaseRetencion: Double,
    val codImpuestoIva: Int = 1,
    val totalizarMontoIva2: Double,
)

@Serializable
data class SalePaymentSummaryDto(
    val totalizarMontoCancelar: Double,
    val totalizarMontoEfectivo: Double,
    val totalizarCambio: Double,
    val totalizarSaldoPendiente: Double = 0.0,
    val montosPorTipo: Map<String, Double> = emptyMap(),
)

@Serializable
data class SalePaymentDto(
    val idFormaPago: Int,
    val tipoMovimiento: String = "INGRESO",
    val monto: Double,
    val montoRecibido: Double,
    val efectivoCambio: Double = 0.0,
    val siglas: String? = null,
)

@Serializable
data class ProcessSaleResponseDto(
    val success: Boolean = true,
    val idFactura: String = "",
    val codFactura: String = "",
    val codEstatus: Int = 2,
    val cufe: String? = null,
    val qr: String? = null,
    val numeroDocumentoFiscal: String? = null,
    val error: String? = null,
)

@Serializable
data class FormasPagoResponseDto(
    val success: Boolean = true,
    val data: List<FormaPagoDto> = emptyList(),
)

@Serializable
data class FormaPagoDto(
    @SerialName("id_forma_pago")
    val idFormaPago: Int,
    val siglas: String? = null,
    val codigo: String? = null,
    val descripcion: String? = null,
    val activo: Int = 1,
    val pos: Int = 1,
    @SerialName("tipo_moneda")
    val tipoMoneda: String = "",
)

@Serializable
data class FacturaSummaryDto(
    val id: String,
    val codigo: String,
    val codigoFiscal: String = "",
    val numeroDocumentoFiscal: String = "",
    val fecha: String = "",
    val fechaCreacion: String = "",
    val fechaDgi: String = "",
    val clienteNombre: String = "",
    val clienteIdentificacion: String = "",
    val total: Double = 0.0,
    val estatus: String = "",
    val formaPago: String = "",
    val moneda: String = "USD",
    val items: Int = 0,
    val totalRef: Double? = null,
    val tasa: Float? = null,
    val abrMonedaSecundaria: String? = null,
)

@Serializable
data class FacturasListResponseDto(
    val data: List<FacturaSummaryDto> = emptyList(),
    val total: Long = 0L,
)

@Serializable
data class FacturasResumenDto(
    val ventasBrutas: Double = 0.0,
    val ventasNetas: Double = 0.0,
    val descuentos: Double = 0.0,
    val cancelaciones: Double = 0.0,
    val totalFacturas: Int = 0,
    val totalFacturasPagadas: Int = 0,
    val totalFacturasAnuladas: Int = 0,
    val ticketPromedio: Double = 0.0,
    val moneda: String = "USD",
    val ventasBrutasRef: Double? = null,
    val ventasNetasRef: Double? = null,
    val cancelacionesRef: Double? = null,
    val ticketPromedioRef: Double? = null,
    val abrMonedaSecundaria: String? = null,
)

@Serializable
data class FacturaDetalleItemDto(
    val id: String = "",
    val descripcion: String = "",
    val cantidad: Double = 1.0,
    val precioUnitario: Double = 0.0,
    val totalConIva: Double = 0.0,
    val codigo: String = "",
    val referencia: String = "",
)

@Serializable
data class FacturaDetalleResponseDto(
    val idFactura: String,
    val codFactura: String,
    val items: List<FacturaDetalleItemDto> = emptyList(),
)

@Serializable
data class ElectronicInvoiceResultDto(
    val success: Boolean = false,
    val cufe: String? = null,
    val qr: String? = null,
    val message: String? = null,
    val alreadyIssued: Boolean = false,
    val numeroDocumentoFiscal: String? = null,
)

