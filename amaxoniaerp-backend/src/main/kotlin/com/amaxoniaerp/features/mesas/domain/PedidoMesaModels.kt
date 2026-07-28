package com.amaxoniaerp.features.mesas.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estado operativo de una línea de pedido de mesa. Refleja el flujo de cocina/bar y el
 * control del cajero.
 *
 * - [PENDIENTE]   : línea agregada en el POS, todavía no enviada a preparación. Vive en el
 *   buffer pendiente del POS hasta que el operario presiona "Enviar comanda".
 * - [ENVIADA]     : enviada a cocina/bar, pendiente de que alguna estación la tome.
 * - [EN_PREPARACION]: cocina/bar está preparándola.
 * - [LISTA]       : lista para servir.
 * - [ENTREGADA]   : entregada en la mesa, cuenta para facturación.
 * - [CANCELADA]   : anulada por el cajero o cocina; no cuenta para facturación y permite
 *   reabrir capacidad sin pasar por caja.
 *
 * Una sesión cerrable (`tieneOperaciones == false`) requiere que todos sus pedidos estén en
 * `ENTREGADA` o `CANCELADA`. Cualquier otro estado impide cerrar/cancelar.
 */
enum class EstadoPedidoMesa(val codigo: String) {
    PENDIENTE("PENDIENTE"),
    ENVIADA("ENVIADA"),
    EN_PREPARACION("EN_PREPARACION"),
    LISTA("LISTA"),
    ENTREGADA("ENTREGADA"),
    CANCELADA("CANCELADA"),
    ;

    val esFinal: Boolean
        get() = this == ENTREGADA || this == CANCELADA

    companion object {
        fun fromCodigo(codigo: String): EstadoPedidoMesa? =
            entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * Vista pública de una línea de pedido de mesa, tal como viaja en el JSON del POS.
 *
 * El snapshot del producto se freezea al insertar (`descripcion`, `precio`, `iva`, etc.) para
 * que el ticket impreso de la comanda y la futura factura sean consistentes con lo que vio el
 * operario: aunque el administrativo cambie el precio del catálogo más tarde, este pedido se
 * cobra al precio pactado.
 */
@Serializable
data class PedidoMesaResponse(
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
    val estado: String = EstadoPedidoMesa.PENDIENTE.codigo,
    @SerialName("cantidad_facturada") val cantidadFacturada: Double = 0.0,
    @SerialName("fecha_creacion") val fechaCreacion: String = "",
    @SerialName("fecha_envio") val fechaEnvio: String? = null,
    @SerialName("fecha_entrega") val fechaEntrega: String? = null,
) {
    /** Cantidad pendiente de facturar a partir de esta línea. Nunca negativa. */
    val cantidadPendiente: Double
        get() = (itemCantidad - cantidadFacturada).coerceAtLeast(0.0)
}

/**
 * Resumen compacto de una comanda (líneas que comparten `comanda_secuencia`). Lo consume el
 * POS para mostrar "comanda #3 enviada a cocina".
 */
@Serializable
data class ComandaMesaResumen(
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    val secuencia: Int,
    val estado: String,
    @SerialName("fecha_envio") val fechaEnvio: String,
    @SerialName("cantidad_lineas") val cantidadLineas: Int,
    @SerialName("total_sin_iva") val totalSinIva: Double,
    @SerialName("total_con_iva") val totalConIva: Double,
)

@Serializable
data class PedidosMesaListResponse(
    val success: Boolean,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    val mesaId: Int,
    val data: List<PedidoMesaResponse>,
)

/** Cuerpo de `POST .../pedidos` para crear una línea de pedido PENDIENTE. */
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
    /** Si `true`, las líneas nuevas se marcan ENVIADA al instante con `comanda_secuencia`. */
    @SerialName("enviar_inmediato") val enviarInmediato: Boolean = false,
)

@Serializable
data class PedidoMesaCreadoResponse(
    val success: Boolean,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    @SerialName("comanda_secuencia") val comandaSecuencia: Int? = null,
    val data: List<PedidoMesaResponse>,
)

@Serializable
data class EnviarComandaRequest(
    /** Si viene vacío, se envían TODOS los pedidos PENDIENTE de la sesión. */
    @SerialName("pedido_ids") val pedidoIds: List<Int> = emptyList(),
)

@Serializable
data class EnviarComandaResponse(
    val success: Boolean,
    @SerialName("comanda_secuencia") val comandaSecuencia: Int,
    @SerialName("cantidad_lineas") val cantidadLineas: Int,
    val data: List<PedidoMesaResponse>,
)

@Serializable
data class CambiarEstadoPedidoRequest(
    val estado: String,
)

@Serializable
data class PedidoMesaActualizadoResponse(
    val success: Boolean,
    val data: PedidoMesaResponse,
)

/** Resultado interno de las operaciones de pedido que pueden fallar por reglas de negocio. */
sealed interface PedidoMesaResult {
    data class Creado(
        val sesionMesaId: Int,
        val comandaSecuencia: Int?,
        val pedidos: List<PedidoMesaResponse>,
    ) : PedidoMesaResult

    data class Enviada(
        val comandaSecuencia: Int,
        val pedidos: List<PedidoMesaResponse>,
    ) : PedidoMesaResult

    data class EstadoActualizado(val pedido: PedidoMesaResponse) : PedidoMesaResult

    data class Listado(val pedidos: List<PedidoMesaResponse>) : PedidoMesaResult

    data object SesionNoActiva : PedidoMesaResult

    data object SesionNoPerteneceMesa : PedidoMesaResult

    data object PedidoNoEncontrado : PedidoMesaResult

    data object EstadoInvalido : PedidoMesaResult

    data object SinPedidosPendientes : PedidoMesaResult

    data object SinItemsParaCrear : PedidoMesaResult
}
