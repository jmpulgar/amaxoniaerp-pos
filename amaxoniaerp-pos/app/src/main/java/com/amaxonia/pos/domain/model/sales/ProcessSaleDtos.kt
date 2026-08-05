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
    @SerialName("cuenta_mesa")
    val cuentaMesa: CuentaMesaVentaDto? = null,
    /**
     * Indica explícitamente al backend que esta venta debe facturarse con la impresora
     * fiscal HKA20 física del POS (Venezuela) y, por tanto, el backend NO debe ejecutar
     * la facturación digital Venezuela vía PAC The Factory HKA.
     *
     * La **única** fuente de verdad es la configuración de impresora seleccionada por el
     * usuario en Settings: `PrinterType.THE_FACTORY_HKA`. El campo se calcula en
     * [com.amaxonia.pos.domain.usecase.payment.BuildSaleRequestUseCase] como:
     *
     * ```
     * useHka20 = selectedPrinterType == PrinterType.THE_FACTORY_HKA
     * ```
     *
     * No debe poder setearse desde otro estado ni deducirse en el backend desde la DB.
     * Para otros países el campo se ignora completamente.
     */
    val useHka20: Boolean = false,
)

@Serializable
data class CuentaMesaVentaDto(
    @SerialName("area_id") val areaId: Int,
    @SerialName("mesa_id") val mesaId: Int,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    @SerialName("cuenta_mesa_id") val cuentaMesaId: Int,
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
    val clienteSucursalId: Int? = null,
    val codFacturaFiscal: String = "",
    val nroz: String = "0000",
    val impresoraSerial: String = "",
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
    @SerialName("_posee_configuracion_lote")
    val poseeConfiguracionLote: String = "no",
    @SerialName("_codigos_lote")
    val codigosLote: List<SaleLotDto> = emptyList(),
    val promocionTipo: String = "",
    val promocionId: String = "",
    val promocionCantidad: Double = 0.0,
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
    val totalizarMontoIva2: Double,
)

@Serializable
data class SalePaymentSummaryDto(
    val totalizarMontoCancelar: Double,
    val totalizarMontoEfectivo: Double,
    val totalizarCambio: Double,
    val totalizarSaldoPendiente: Double,
    val montosPorTipo: Map<String, Double>,
)

@Serializable
data class SalePaymentDto(
    val idFormaPago: Int,
    val tipoMovimiento: String,
    val monto: Double,
    val montoRecibido: Double,
    val efectivoCambio: Double = 0.0,
)

@Serializable
data class ProcessSaleResponseDto(
    val success: Boolean,
    val idFactura: String,
    val codFactura: String,
    val codEstatus: Int,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val feError: String? = null,
    /**
     * FASE 2.2/2.3 — Venezuela digital.
     *
     * Número de documento fiscal (correlativo VE) retornado por el PAC
     * The Factory HKA y persistido en `factura`. Se propaga tanto en la
     * emisión exitosa como en reintentos (`AlreadyIssued`).
     *
     * **Importante:** solo se propaga cuando el flujo FE digital termina
     * sano. En HKA-20 (impresora fiscal física del POS) o en fallas se
     * mantiene `null` — jamás se inventan valores.
     */
    val numeroDocumentoFiscal: String? = null,
    /** FASE 2.2/2.3 — Venezuela digital: número de control HKA persistido. */
    val numeroControlThka: String? = null,
    @SerialName("sesion_mesa_cerrada")
    val sesionMesaCerrada: Boolean = false,
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

@Serializable
data class EnviarCorreoFacturaResponseDto(
    val codigo: String? = null,
    val resultado: String? = null,
    val mensaje: String? = null,
    val validaciones: List<String> = emptyList(),
    val cufe: String? = null,
)
