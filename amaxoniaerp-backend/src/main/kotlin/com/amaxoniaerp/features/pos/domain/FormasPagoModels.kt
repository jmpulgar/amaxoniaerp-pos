package com.amaxoniaerp.features.pos.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FormaPagoResponse(
    val success: Boolean,
    val data: List<FormaPagoItem>
)

@Serializable
data class FormaPagoItem(
    @SerialName("id_forma_pago")
    val idFormaPago: Int,
    val siglas: String? = null,
    val codigo: String? = null,
    val descripcion: String? = null,
    @SerialName("id_caja_tp_concepto")
    val idCajaTpConcepto: Int? = null,
    @SerialName("id_caja_tp_registro")
    val idCajaTpRegistro: Int? = null,
    @SerialName("cuenta_contable")
    val cuentaContable: String? = null,
    @SerialName("FormaPagoFact")
    val formaPagoFact: String? = null,
    val activo: Int,
    val pos: Int,
    val imagen: String? = null,
    val grupo: Int,
    val orden: Int,
    @SerialName("id_banco_cuenta")
    val idBancoCuenta: Int? = null,
    @SerialName("id_banco_operacion")
    val idBancoOperacion: Int? = null,
    @SerialName("tipo_moneda")
    val tipoMoneda: String,
    @SerialName("id_caja")
    val idCaja: String? = null
)
