package com.amaxoniaerp.features.mesas.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estados de una cuenta de mesa. Reflejan el ciclo de vida:
 *
 * - [ACTIVA]: creada, pendiente de pago. Cancelable (revierte a la sesión y libera el saldo).
 * - [PAGADA]: facturada con éxito (`id_factura != null`). Los saldos consumidos ya están
 *   restados de `pedido_mesa.cantidad_facturada`.
 * - [CANCELADA]: descartada sin facturación; los saldos reservados regresan al pool pendiente.
 */
enum class EstadoCuentaMesa(
    val codigo: String,
) {
    ACTIVA("ACTIVA"),
    PAGADA("PAGADA"),
    CANCELADA("CANCELADA"),
    ;

    val esFinal: Boolean
        get() = this == PAGADA || this == CANCELADA

    companion object {
        fun fromCodigo(codigo: String): EstadoCuentaMesa? = entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * Estados del registro de idempotencia de cobro asociado a una cuenta.
 *
 * - [SENDING]: intento en curso (POS disparó `procesar venta` pero todavía no llamó
 *   `marcar-facturada`). Un re-intento del mismo key cuando está en SENDING se rechaza con
 *   [CuentaMesaResult.IdempotenciaDuplicada] para evitar doble procesamiento.
 * - [CONFIRMED]: el POS ya confirmó la factura (200 OK en `marcar-facturada`).
 * - [FAILED]: el POS reportó fallo de facturación (404/500/etc); este intento se puede
 *   descartar y se puede iniciar uno nuevo con otra `idempotencyKey`.
 */
enum class EstadoCuentaIdempotencia(
    val codigo: String,
) {
    SENDING("SENDING"),
    CONFIRMED("CONFIRMED"),
    FAILED("FAILED"),
    ;

    companion object {
        fun fromCodigo(codigo: String): EstadoCuentaIdempotencia? = entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * Vista pública de una línea facturable incluida en una cuenta (o división) de mesa.
 *
 * Se construye a partir de un `pedido_mesa` seleccionado: copia precio/iva/descuento del
 * snapshot freezado en la línea original y la `cantidad` que esta cuenta cubre. La
 * diferencia `pedido_mesa.item_cantidad - SUM(cuenta_detalle.cantidad)` representa el saldo
 * pendiente de la línea y nunca debe ser negativa (se valida en `crear`/`marcarFacturada`).
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
    /** 1 cuando el backend aplicó la facturación contra esta línea. */
    val facturado: Boolean = false,
    @SerialName("fecha_creacion") val fechaCreacion: String = "",
)

/**
 * Vista pública de la cuenta/división de una mesa. Incluye los totales agregados y el saldo
 * restante (= total - pagos confirmados previos). El POS lo usa tanto para la "cuenta
 * completa" como para una división por producto/cantidad.
 */
@Serializable
data class CuentaMesaResponse(
    val id: Int = 0,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int = 0,
    @SerialName("numero_cuenta") val numeroCuenta: Int = 1,
    val estado: String = EstadoCuentaMesa.ACTIVA.codigo,
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

@Serializable
data class CuentasMesaListResponse(
    val success: Boolean,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    val data: List<CuentaMesaResponse>,
)

/**
 * Item de la petición `POST .../cuenta` para crear una cuenta nueva.
 *
 * El caller puede enviar `cantidad` (sub-división por cantidad) o dejarlo en null para que el
 * backend asignar el saldo completo de ese pedido. SUPERAR `item_cantidad -
 * cantidad_facturada` ⇒ [CuentaMesaResult.CantidadSuperaSaldo].
 */
@Serializable
data class CrearCuentaItemRequest(
    @SerialName("pedido_mesa_id") val pedidoMesaId: Int,
    val cantidad: Double? = null,
)

@Serializable
data class CrearCuentaRequest(
    val items: List<CrearCuentaItemRequest> = emptyList(),
    /**
     * Si `true`, el backend ignora [items] y crea la cuenta con TODOS los pedidos
     * ENTREGADOS/no CANCELADOS que tengan saldo pendiente. Es la "cuenta completa".
     */
    @SerialName("incluir_todo_pendiente") val incluirTodoPendiente: Boolean = true,
)

@Serializable
data class CuentaCreadaResponse(
    val success: Boolean,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    val data: CuentaMesaResponse,
)

/**
 * Petición idempotente para confirmar que una cuenta ya fue facturada exitosamente vía el
 * pipeline estándar de ventas (`ventas/procesar`).
 *
 * Flujo del POS:
 * 1. `POST /api/pos/ventas/procesar` con `id_factura = idempotencyKey` → persiste la factura.
 * 2. En caso de éxito, llama a este endpoint para atar la factura a la cuenta, marcar las
 *    líneas como facturadas y decretar el saldo de cada `pedido_mesa.cantidad_facturada`.
 *
 * Si se llama más de una vez con la misma `idempotencyKey`, la segunda+ llamada no aplica
 * ningún efecto: simplemente devuelve el estado final almacenado (`IdempotenciaDuplicada`).
 */
@Serializable
data class MarcarCuentaFacturadaRequest(
    @SerialName("id_factura") val idFactura: String,
    @SerialName("cod_factura") val codFactura: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class MarcarCuentaFacturadaResponse(
    val success: Boolean,
    @SerialName("sesion_mesa_id") val sesionMesaId: Int,
    @SerialName("cuenta_mesa_id") val cuentaMesaId: Int,
    val data: CuentaMesaResponse,
    /** `true` cuando este pago liquidó por completo el saldo de la sesión → sesión CERRADA_PAGADA. */
    @SerialName("sesion_cerrada") val sesionCerrada: Boolean,
    /** Sólo se llena cuando el intento ya estaba confirmado (idempotencia duplicada). */
    val error: String? = null,
)

/** Request opcional para cancelar una cuenta ACTIVA sin facturar (revierte saldos). */
@Serializable
data class CancelarCuentaRequest(
    @SerialName("cancelar_sesion") val cancelarSesion: Boolean = false,
)

/** Resultado interno del repositorio de cuenta. Lo traduce el routing a HTTP status. */
sealed interface CuentaMesaResult {
    data class Creada(
        val cuenta: CuentaMesaResponse,
    ) : CuentaMesaResult

    data class Listada(
        val cuentas: List<CuentaMesaResponse>,
    ) : CuentaMesaResult

    data class SolicitudRegistrada(
        val sesion: SesionMesaResponse,
    ) : CuentaMesaResult

    data class Facturada(
        val cuenta: CuentaMesaResponse,
        val sesionCerrada: Boolean,
    ) : CuentaMesaResult

    data object SesionNoActiva : CuentaMesaResult

    data object SesionNoPerteneceMesa : CuentaMesaResult

    data object CuentaNoEncontrada : CuentaMesaResult

    data object CuentaNoActiva : CuentaMesaResult

    data object PedidoNoEncontrado : CuentaMesaResult

    data object CantidadSuperaSaldo : CuentaMesaResult

    data object PedidosPendientesImpidenPago : CuentaMesaResult

    data object IdempotenciaDuplicada : CuentaMesaResult

    data object IdempotenciaFallidaPrevia : CuentaMesaResult

    data object SinItemsParaCrear : CuentaMesaResult

    data object SaldosPendientesEnSesion : CuentaMesaResult
}
