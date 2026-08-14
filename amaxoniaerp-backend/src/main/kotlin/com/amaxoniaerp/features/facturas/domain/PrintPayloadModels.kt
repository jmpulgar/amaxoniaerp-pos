package com.amaxoniaerp.features.facturas.domain

import kotlinx.serialization.Serializable

@Serializable
data class FacturaPrintPayloadResponse(
    val facturaId: String,
    val numeroFactura: String,
    val fecha: String,
    val empresa: EmpresaPrintResponse,
    val cliente: ClientePrintResponse?,
    val vendedor: String? = null,
    val productos: List<ProductoPrintResponse>,
    val subtotal: String,
    val montoExento: String? = null,
    /**
     * Total line-item discount aggregated from `factura_detalle._item_montodescuento` and
     * serialized as a money string with 2 decimal places. The arithmetic is done in
     * [BigDecimal] in [FacturasRepository.getPrintPayload] so no IEEE-754 residue leaks into the
     * printed total. Android clients consume this as `FacturaPrintPayloadDto.descuento`.
     *
     * Nullable for backward compatibility with old clients: when the field is absent the printer
     * formatter falls back to "0.00" on the receipt.
     */
    val descuento: String? = null,
    val totalImpuesto: String,
    val total: String,
    val pagos: List<PagoPrintResponse>,
    val cambio: String? = null,
    val qrUrl: String? = null,
    val cufe: String? = null,
    val fechaRecepcionDgi: String? = null,
    val proveedorAutorizado: String? = null,
    val numeroDocumentoFiscal: String? = null,
    val puntoFacturacionFiscal: String? = null,
    val codigoSucursal: String? = null,
    val protocoloAutorizacion: String? = null,
    /**
     * FASE 2.3b — Venezuela digital: número de control persistido por el PAC
     * The Factory HKA en `factura.numero_control_thka`. El emisor se sirve
     * **exclusivamente** de lo persistido; nunca se inventa.
     */
    val numeroControlThka: String? = null,
    /** FASE 2.3b — Venezuela IGTF y multimoneda (opcional, sólo cuando aplica). */
    val igtfMonto: String? = null,
    val igtfBaseImponible: String? = null,
    val igtfTasa: String? = null,
    val tasaCambioBs: String? = null,
    val abrMonedaBase: String? = null,
    val abrMonedaSecundaria: String? = null,
    val totalDivisa: String? = null,
)

@Serializable
data class EmpresaPrintResponse(
    val nombre: String,
    val ruc: String? = null,
    val direccion: String? = null,
    val telefono: String? = null,
    val tienda: String? = null,
    val caja: String? = null,
)

@Serializable
data class ClientePrintResponse(
    val nombre: String,
    val documento: String? = null,
    val sucursal: String? = null,
    val sucursalDireccion: String? = null,
    val digitoVerificador: String? = null,
    val tipoReceptor: String? = null,
)

@Serializable
data class ProductoPrintResponse(
    val nombre: String,
    val cantidad: String,
    val unidad: String? = null,
    val precioUnitario: String,
    val descuento: String,
    val impuesto: String,
    val total: String,
    val codigo: String? = null,
    val tasaImpuesto: String? = null,
)

@Serializable
data class PagoPrintResponse(
    val metodo: String,
    val monto: String,
)
