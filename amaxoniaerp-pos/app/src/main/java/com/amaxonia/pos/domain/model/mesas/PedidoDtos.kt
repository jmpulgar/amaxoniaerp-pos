package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estados de una línea de pedido. Reflejan el enum del backend (`EstadoPedidoMesa`).
 *
 * - `PENDIENTE`: agregada en el POS, todavía no enviada.
 * - `ENVIADA` y siguientes: estados que toman cocina/bar o el cajero al anular.
 */
object EstadoPedidoMesa {
    const val PENDIENTE = "PENDIENTE"
    const val ENVIADA = "ENVIADA"
    const val EN_PREPARACION = "EN_PREPARACION"
    const val LISTA = "LISTA"
    const val ENTREGADA = "ENTREGADA"
    const val CANCELADA = "CANCELADA"

    /** Estados a partir de los cuales la línea ya no permite más movimientos. */
    val FINALES = setOf(ENTREGADA, CANCELADA)
}

/**
 * Snapshot de una línea de pedido de mesa. Refleja `PedidoMesaResponse` del backend.
 *
 * El snapshot incluye los montos calculados para que la pantalla de comanda pueda pintar el
 * total sin recalcular y para que el futuro flujo de factura use los valores pactados.
 */
@Serializable
data class PedidoMesa(
    val id: Int = 0,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    @SerialName("mesa_id") val mesaId: Int = 0,
    @SerialName("comanda_secuencia") val comandaSecuencia: Int? = null,
    val productoId: Int = 0,
    @SerialName("_item_almacen") val itemAlmacen: Int = 1,
    @SerialName("item_codigo") val itemCodigo: String = "",
    @SerialName("_item_descripcion") val itemDescripcion: String = "",
    @SerialName("_item_cantidad") val itemCantidad: Double = 0.0,
    @SerialName("_item_preciosiniva") val itemPrecioSinIva: Double = 0.0,
    @SerialName("_item_descuento") val itemDescuento: Double = 0.0,
    @SerialName("_item_montodescuento") val itemMontoDescuento: Double = 0.0,
    @SerialName("_item_piva") val itemPIva: Double = 0.0,
    @SerialName("_item_totalsiniva") val itemTotalSinIva: Double = 0.0,
    @SerialName("_item_totalconiva") val itemTotalConIva: Double = 0.0,
    @SerialName("_cantidad_bulto") val cantidadBulto: Int = 1,
    @SerialName("unidad_empaque") val unidadEmpaque: String = "UNIDAD",
    val notas: String? = null,
    @SerialName("promocion_id") val promocionId: String? = null,
    @SerialName("promocion_tipo") val promocionTipo: String? = null,
    @SerialName("promocion_detalle_id") val promocionDetalleId: String? = null,
    val estado: String = EstadoPedidoMesa.PENDIENTE,
    @SerialName("cantidad_facturada") val cantidadFacturada: Double = 0.0,
    @SerialName("fecha_creacion") val fechaCreacion: String = "",
    @SerialName("fecha_envio") val fechaEnvio: String? = null,
    @SerialName("fecha_entrega") val fechaEntrega: String? = null,
) {
    val cantidadPendiente: Double
        get() = (itemCantidad - cantidadFacturada).coerceAtLeast(0.0)
}

@Serializable
data class PedidosMesaListResponse(
    val success: Boolean = true,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    val mesaId: Int = 0,
    val data: List<PedidoMesa> = emptyList(),
    val error: String? = null,
)

/**
 * Cuerpo de `POST .../pedidos` para crear líneas. El campo `enviarInmediato` sirve para
 * crear y enviar a cocina en un solo paso (las líneas quedan ENVIADA con comanda_secuencia).
 */
@Serializable
data class CrearPedidoMesaItemRequest(
    @SerialName("producto_id") val productoId: Int,
    @SerialName("_item_almacen") val itemAlmacen: Int = 1,
    @SerialName("item_codigo") val itemCodigo: String = "",
    @SerialName("_item_descripcion") val itemDescripcion: String = "",
    @SerialName("_item_cantidad") val itemCantidad: Double,
    @SerialName("_item_preciosiniva") val itemPrecioSinIva: Double,
    @SerialName("_item_descuento") val itemDescuento: Double = 0.0,
    @SerialName("_item_montodescuento") val itemMontoDescuento: Double = 0.0,
    @SerialName("_item_piva") val itemPIva: Double = 0.0,
    @SerialName("_item_totalsiniva") val itemTotalSinIva: Double,
    @SerialName("_item_totalconiva") val itemTotalConIva: Double,
    @SerialName("_cantidad_bulto") val cantidadBulto: Int = 1,
    @SerialName("unidad_empaque") val unidadEmpaque: String = "UNIDAD",
    val notas: String? = null,
    @SerialName("promocion_id") val promocionId: String? = null,
    @SerialName("promocion_tipo") val promocionTipo: String? = null,
    @SerialName("promocion_detalle_id") val promocionDetalleId: String? = null,
)

@Serializable
data class CrearPedidoMesaRequest(
    val items: List<CrearPedidoMesaItemRequest>,
    @SerialName("enviar_inmediato") val enviarInmediato: Boolean = false,
)

@Serializable
data class PedidoMesaCreadoResponse(
    val success: Boolean = true,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    @SerialName("comanda_secuencia") val comandaSecuencia: Int? = null,
    val data: List<PedidoMesa> = emptyList(),
    val error: String? = null,
)

@Serializable
data class EnviarComandaRequest(
    @SerialName("pedido_ids") val pedidoIds: List<Int> = emptyList(),
)

@Serializable
data class EnviarComandaResponse(
    val success: Boolean = true,
    @SerialName("comanda_secuencia") val comandaSecuencia: Int = 0,
    @SerialName("cantidad_lineas") val cantidadLineas: Int = 0,
    val data: List<PedidoMesa> = emptyList(),
    val error: String? = null,
)

@Serializable
data class CambiarEstadoPedidoRequest(
    val estado: String,
)

@Serializable
data class PedidoMesaActualizadoResponse(
    val success: Boolean = true,
    val data: PedidoMesa = PedidoMesa(),
    val error: String? = null,
)
