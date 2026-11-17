package com.amaxoniaerp.features.pedidos.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import kotlinx.serialization.Serializable

/**
 * Estados de `pedido.cod_estatus` usados por el ERP web (tabla `estatus`):
 * 1 = pendiente/en proceso, 2 = facturado, 3 = anulado.
 */
object PedidoEstatus {
    const val PENDIENTE = 1
    const val FACTURADO = 2
    const val ANULADO = 3
}

/**
 * Alta de pedido desde el POS. Reutiliza el contrato de cabecera e items de la venta POS
 * (`SaleInvoiceInput` / `SaleItemInput`): el POS calcula líneas y totales con la misma
 * matemática que usa para facturar, igual que el web calcula el ITBMS en el cliente.
 *
 * [idPedido] es la clave de idempotencia generada por el dispositivo: un reenvío con el mismo
 * id devuelve el pedido ya creado en vez de duplicarlo.
 */
@Serializable
data class CreatePedidoRequest(
    val idPedido: String,
    val pedido: SaleInvoiceInput,
    val items: List<SaleItemInput>,
    val moneda: SaleCurrencyInput? = null,
)

@Serializable
data class CreatePedidoResponse(
    val success: Boolean,
    val idPedido: String,
    val codPedido: String,
    val codEstatus: Int,
    val duplicado: Boolean = false,
)

@Serializable
data class PedidoSummary(
    val idPedido: String,
    val codPedido: String,
    val fecha: String,
    val idCliente: String,
    val codCliente: String,
    val cliente: String,
    val codVendedor: Int,
    val total: Double,
    val cantidadItems: Double,
    val codEstatus: Int,
    val estatus: String,
    val observacion: String,
    val usuarioCreacion: String,
)

@Serializable
data class PedidoListResponse(
    val success: Boolean,
    val data: List<PedidoSummary>,
)

@Serializable
data class PedidoLine(
    val idDetallePedido: String,
    val idItem: Int,
    val codItem: String,
    val descripcion: String,
    val idAlmacen: Int,
    val cantidad: Double,
    val cantidadPendiente: Double,
    val precioSinIva: Double,
    val descuentoPorcentaje: Double,
    val montoDescuento: Double,
    val porcentajeIva: Double,
    val totalSinIva: Double,
    val totalConIva: Double,
    val unidadEmpaque: String,
    val itemUnidadEmpaque: String,
    val cantidadBulto: Int,
)

@Serializable
data class PedidoDetail(
    val pedido: PedidoSummary,
    val subtotal: Double,
    val descuentos: Double,
    val baseImponible: Double,
    val impuesto: Double,
    val items: List<PedidoLine>,
)

@Serializable
data class PedidoDetailResponse(
    val success: Boolean,
    val data: PedidoDetail,
)

class PedidoValidationException(
    message: String,
) : ApiException(ErrorCategory.Validation, message)

class PedidoNotFoundException(
    message: String,
) : ApiException(ErrorCategory.NotFound, message)

/** El pedido existe pero su estado no permite la operación (ya facturado, anulado...). */
class PedidoStateException(
    message: String,
) : ApiException(ErrorCategory.DomainRule, message)
