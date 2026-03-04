package com.amaxonia.pos.domain.model.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FormaPagoDetalle(
    @SerialName("id_forma_pago")
    val idFormaPago: Int,
    val sigla: String,
    val monto: Double,
    @SerialName("id_caja_tp_concepto")
    val idCajaTpConcepto: Int? = null,
    @SerialName("id_banco_cuenta")
    val idBancoCuenta: Int? = null,
    @SerialName("id_banco_operacion")
    val idBancoOperacion: Int? = null
)

@Serializable
data class FormapagoDetallePayload(
    @SerialName("totalizar_monto_efectivo")
    val totalizarMontoEfectivo: Double,
    @SerialName("totalizar_monto_credito")
    val totalizarMontoCredito: Double,
    @SerialName("totalizar_monto_otros")
    val totalizarMontoOtros: Double,
    val detalle: List<FormaPagoDetalle>,
    @SerialName("detalle_TDC")
    val detalleTdc: List<Map<String, String>> = emptyList(),
    @SerialName("detalle_NEQ")
    val detalleNeq: List<Map<String, String>> = emptyList(),
    @SerialName("detalle_GC")
    val detalleGc: List<Map<String, String>> = emptyList(),
    @SerialName("detalle_ABONO")
    val detalleAbono: List<Map<String, String>> = emptyList(),
    @SerialName("detalle_PT")
    val detallePt: List<Map<String, String>> = emptyList()
)
