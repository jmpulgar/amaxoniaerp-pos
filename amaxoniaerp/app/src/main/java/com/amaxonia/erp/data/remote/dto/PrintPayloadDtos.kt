package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FacturaPrintPayloadDto(
    val facturaId: String,
    val numeroFactura: String,
    val fecha: String,
    val empresa: EmpresaPrintDto,
    val cliente: ClientePrintDto? = null,
    val vendedor: String? = null,
    val productos: List<ProductoPrintDto>,
    val subtotal: String,
    val montoExento: String? = null,
    val totalImpuesto: String,
    val total: String,
    val descuento: String? = null,
    val pagos: List<PagoPrintDto>,
    val cambio: String? = null,
    val qrUrl: String? = null,
    val cufe: String? = null,
    val fechaRecepcionDgi: String? = null,
    val proveedorAutorizado: String? = null,
    val numeroDocumentoFiscal: String? = null,
    val puntoFacturacionFiscal: String? = null,
    val codigoSucursal: String? = null,
    val protocoloAutorizacion: String? = null,
    val numeroControlThka: String? = null,
    val igtfMonto: String? = null,
    val igtfBaseImponible: String? = null,
    val igtfTasa: String? = null,
    val tasaCambioBs: String? = null,
    val abrMonedaBase: String? = null,
    val abrMonedaSecundaria: String? = null,
    val totalDivisa: String? = null,
)

@Serializable
data class EmpresaPrintDto(
    val nombre: String,
    val ruc: String? = null,
    val direccion: String? = null,
    val telefono: String? = null,
    val tienda: String? = null,
    val caja: String? = null,
)

@Serializable
data class ClientePrintDto(
    val nombre: String,
    val documento: String? = null,
    val sucursal: String? = null,
    val sucursalDireccion: String? = null,
    val digitoVerificador: String? = null,
    val tipoReceptor: String? = null,
)

@Serializable
data class ProductoPrintDto(
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
data class PagoPrintDto(
    val metodo: String,
    val monto: String,
)
