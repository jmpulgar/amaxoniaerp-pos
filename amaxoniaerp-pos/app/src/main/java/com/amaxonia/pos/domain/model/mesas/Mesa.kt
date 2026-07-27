package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mesa configurada dentro de un área.
 *
 * La geometría ([posicionX], [posicionY], [ancho], [alto], [rotacion]) se transporta para no
 * volver a tocar el contrato cuando llegue el plano gráfico, pero **en esta fase no se usa para
 * dibujar nada**: la interfaz de teléfono muestra una cuadrícula de tarjetas.
 *
 * [activo] refleja únicamente si la mesa está configurada y habilitada. No es un estado
 * operativo: "disponible/ocupada/reservada" vendrán de una entidad de sesión de mesa aparte y
 * jamás deben deducirse de este campo.
 */
@Serializable
data class Mesa(
    val id: Int,
    @SerialName("area_id")
    val areaId: Int,
    val codigo: String? = null,
    val nombre: String = "",
    val capacidad: Int = 0,
    val forma: String? = null,
    @SerialName("posicion_x")
    val posicionX: Double = 0.0,
    @SerialName("posicion_y")
    val posicionY: Double = 0.0,
    val ancho: Double = 0.0,
    val alto: Double = 0.0,
    val rotacion: Double = 0.0,
    val activo: Boolean = true,
) {
    val displayName: String
        get() = nombre.trim().ifBlank { codigo?.trim()?.ifBlank { null } ?: "Mesa $id" }

    val displayCode: String? get() = codigo?.trim()?.takeIf { it.isNotBlank() && it != displayName }
}
