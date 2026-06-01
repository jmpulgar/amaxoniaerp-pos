package com.amaxonia.pos.domain.model.sales

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
    val pagos: List<PagoPrintDto>,
    val cambio: String? = null,
    val qrUrl: String? = null,
    val cufe: String? = null,
    val fechaRecepcionDgi: String? = null,
    val proveedorAutorizado: String? = null,
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
)

@Serializable
data class PagoPrintDto(
    val metodo: String,
    val monto: String,
)
