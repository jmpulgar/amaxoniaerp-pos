package com.amaxoniaerp.features.facturas.domain

import kotlinx.serialization.Serializable

@Serializable
data class FacturaSummary(
    val id: String,
    val codigo: String,
    val codigoFiscal: String,
    val numeroDocumentoFiscal: String,
    val fecha: String,
    val fechaCreacion: String = "",
    val fechaDgi: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val total: Double,
    val estatus: String,
    val formaPago: String,
    val moneda: String = "USD",
    val items: Int = 0,
    val totalRef: Double? = null,
    val tasa: Float? = null,
    val abrMonedaSecundaria: String? = null,
)

@Serializable
data class FacturasListResponse(
    val data: List<FacturaSummary>,
    val total: Long,
)

@Serializable
data class FacturaDetalleItem(
    val id: String,
    val descripcion: String,
    val cantidad: Double,
    val precioUnitario: Double,
    val totalConIva: Double,
    val codigo: String = "",
    val referencia: String = "",
)

@Serializable
data class FacturaDetalleResponse(
    val idFactura: String,
    val codFactura: String,
    val items: List<FacturaDetalleItem>,
)

@Serializable
data class FacturasResumen(
    val ventasBrutas: Double,
    val ventasNetas: Double,
    val descuentos: Double,
    val cancelaciones: Double,
    val totalFacturas: Int,
    val totalFacturasPagadas: Int,
    val totalFacturasAnuladas: Int,
    val ticketPromedio: Double,
    val moneda: String = "USD",
    val ventasBrutasRef: Double? = null,
    val ventasNetasRef: Double? = null,
    val cancelacionesRef: Double? = null,
    val ticketPromedioRef: Double? = null,
    val abrMonedaSecundaria: String? = null,
)

@Serializable
data class ConfirmFacturaFiscalRequest(
    val numeroDocumentoFiscal: String = "",
    val codFacturaFiscal: String = "",
    val impresoraSerial: String = "",
)

@Serializable
data class ConfirmFacturaFiscalResponse(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val numeroDocumentoFiscal: String,
    val codFacturaFiscal: String,
    val impresoraSerial: String,
)
