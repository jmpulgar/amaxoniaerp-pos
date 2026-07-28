package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estados de una cuenta de mesa. Reflejan el ciclo de vida:
 * - [ACTIVA]: creada, pendiente de pago. Si el operario "cancela la cuenta" antes de pagar,
 *   pasa a [CANCELADA] (sin factura) y los saldos se liberan.
 * - [PAGADA]: facturada con éxito (`id_factura != null`). Los saldos consumidos ya están
 *   restados del contador de `pedido_mesa.cantidad_facturada`.
 * - [CANCELADA]: descartada sin facturación.
 */
object EstadoCuentaMesa {
    const val ACTIVA = "ACTIVA"
    const val PAGADA = "PAGADA"
    const val CANCELADA = "CANCELADA"
}

/**
 * Vista pública de una línea facturable dentro de una cuenta/división. Refleja
 * `CuentaDetalleResponse` del backend.
 */
@Serializable
data class CuentaDetalleResponse(
    val id: Int = 0,
    @SerialName("cuenta_mesa_id") val cuentaMesaId: Int = 0,
    @SerialName("pedido_mesa_id") val pedidoMesaId: Int = 0,
    val productoId: Int = 0,
    @SerialName("_item_almacen") val itemAlmacen: Int = 1,
    @SerialName("item_codigo") val itemCodigo: String = "",
    @SerialName("_item_descripcion") val itemDescripcion: String = "",
    val cantidad: Double = 0.0,
    @SerialName("_item_preciosiniva") val itemPrecioSinIva: Double = 0.0,
    @SerialName("_item_descuento") val itemDescuento: Double = 0.0,
    @SerialName("_item_montodescuento") val itemMontoDescuento: Double = 0.0,
    @SerialName("_item_piva") val itemPIva: Double = 0.0,
    @SerialName("_item_totalsiniva") val itemTotalSinIva: Double = 0.0,
    @SerialName("_item_totalconiva") val itemTotalConIva: Double = 0.0,
    /** 1 cuando el backend ya ejecutó la facturación contra esta línea. */
    val facturado: Boolean = false,
    @SerialName("fecha_creacion") val fechaCreacion: String = "",
)

/** Resumen agregado de la cuenta. */
@Serializable
data class CuentaMesaResponse(
    val id: Int = 0,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    @SerialName("numero_cuenta") val numeroCuenta: Int = 1,
    val estado: String = EstadoCuentaMesa.ACTIVA,
    val subtotal: Double = 0.0,
    val descuento: Double = 0.0,
    val impuesto: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("saldo_restante") val saldoRestante: Double = 0.0,
    @SerialName("id_factura") val idFactura: String? = null,
    @SerialName("cod_factura") val codFactura: String? = null,
    @SerialName("fecha_factura") val fechaFactura: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String = "",
    val detalle: List<CuentaDetalleResponse> = emptyList(),
)

/** Request para crear una cuenta nueva (completa, por producto o por cantidad). */
@Serializable
data class CrearCuentaItemRequest(
    @SerialName("pedido_mesa_id") val pedidoMesaId: Int,
    /**
     * Cantidad que esta cuenta cubre del pedido. Si el caller omite el campo o envía el total,
     * el backend toma `max(pedido.item_cantidad - cantidad_facturada, 0)`. Nunca puede superar
     * el saldo pendiente del pedido.
     */
    val cantidad: Double? = null,
)

@Serializable
data class CrearCuentaRequest(
    val items: List<CrearCuentaItemRequest>,
    /**
     * `true` (default) crea la cuenta con TODOS los pedidos facturables ENTREGADADOS/NO-
     * CANCELADOS con saldo pendiente, ignorando [items]. Útil para la "Cuenta completa".
     */
    @SerialName("incluir_todo_pendiente") val incluirTodoPendiente: Boolean = true,
)

@Serializable
data class CrearCuentaResponse(
    val success: Boolean = true,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    val data: CuentaMesaResponse = CuentaMesaResponse(),
    val error: String? = null,
)

@Serializable
data class CuentasMesaListResponse(
    val success: Boolean = true,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    val data: List<CuentaMesaResponse> = emptyList(),
    val error: String? = null,
)

/**
 * Request para marcar una cuenta como facturada con éxito desde el POS.
 *
 * Contrato legado conservado para clientes anteriores. El POS actual incluye `cuenta_mesa`
 * directamente en `POST /api/pos/ventas/procesar`; factura, cantidades y cierre se confirman
 * atómicamente. Este request solo permite reconciliar integraciones antiguas.
 */
@Serializable
data class MarcarCuentaFacturadaRequest(
    @SerialName("id_factura") val idFactura: String,
    @SerialName("cod_factura") val codFactura: String? = null,
    /** Alias del `idFactura`: clave única del intento antiguo doble-toque. */
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class MarcarCuentaFacturadaResponse(
    val success: Boolean = true,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    @SerialName("cuenta_mesa_id") val cuentaMesaId: Int = 0,
    val data: CuentaMesaResponse = CuentaMesaResponse(),
    /** true cuando el último pago liquidó todo; la sesión quedó CERRADA_PAGADA. */
    @SerialName("sesion_cerrada") val sesionCerrada: Boolean = false,
    val error: String? = null,
)
