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
    val totalImpuesto: String,
    val total: String,
    val pagos: List<PagoPrintResponse>,
    val cambio: String? = null,
    val qrUrl: String? = null,
    val cufe: String? = null,
    val fechaRecepcionDgi: String? = null,
    val proveedorAutorizado: String? = null,
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
)

@Serializable
data class PagoPrintResponse(
    val metodo: String,
    val monto: String,
)
