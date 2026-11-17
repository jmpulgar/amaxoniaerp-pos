package com.amaxonia.pos.domain.model.pedidos

import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import kotlinx.serialization.Serializable

/** Estados de `pedido.cod_estatus` del ERP. */
object PedidoEstatus {
    const val PENDIENTE = 1
    const val FACTURADO = 2
}

/**
 * Alta de pedido (`POST api/pos/pedidos`). Reutiliza cabecera e items de la venta POS para que
 * el pedido se calcule con la misma matemática que la factura.
 */
@Serializable
data class CreatePedidoRequestDto(
    val idPedido: String,
    val pedido: SaleInvoiceDto,
    val items: List<SaleItemDto>,
    val moneda: SaleCurrencyDto? = null,
)

@Serializable
data class CreatePedidoResponseDto(
    val success: Boolean = false,
    val idPedido: String = "",
    val codPedido: String = "",
    val codEstatus: Int = 0,
    val duplicado: Boolean = false,
)

@Serializable
data class PedidoSummaryDto(
    val idPedido: String,
    val codPedido: String = "",
    val fecha: String = "",
    val idCliente: String = "",
    val codCliente: String = "",
    val cliente: String = "",
    val codVendedor: Int = 0,
    val total: Double = 0.0,
    val cantidadItems: Double = 0.0,
    val codEstatus: Int = 0,
    val estatus: String = "",
    val observacion: String = "",
    val usuarioCreacion: String = "",
)

@Serializable
data class PedidoListResponseDto(
    val success: Boolean = false,
    val data: List<PedidoSummaryDto> = emptyList(),
)

@Serializable
data class PedidoLineDto(
    val idDetallePedido: String = "",
    val idItem: Int,
    val codItem: String = "",
    val descripcion: String = "",
    val idAlmacen: Int = 0,
    val cantidad: Double = 0.0,
    val cantidadPendiente: Double = 0.0,
    val precioSinIva: Double = 0.0,
    val descuentoPorcentaje: Double = 0.0,
    val montoDescuento: Double = 0.0,
    val porcentajeIva: Double = 0.0,
    val totalSinIva: Double = 0.0,
    val totalConIva: Double = 0.0,
    val unidadEmpaque: String = "",
    val itemUnidadEmpaque: String = "UNIDAD",
    val cantidadBulto: Int = 1,
)

@Serializable
data class PedidoDetailDto(
    val pedido: PedidoSummaryDto,
    val subtotal: Double = 0.0,
    val descuentos: Double = 0.0,
    val baseImponible: Double = 0.0,
    val impuesto: Double = 0.0,
    val items: List<PedidoLineDto> = emptyList(),
)

@Serializable
data class PedidoDetailResponseDto(
    val success: Boolean = false,
    val data: PedidoDetailDto,
)

/** Pedido que el carrito actual está facturando (se envía como `idPedido` en la venta). */
data class LinkedPedido(
    val idPedido: String,
    val codPedido: String,
)
