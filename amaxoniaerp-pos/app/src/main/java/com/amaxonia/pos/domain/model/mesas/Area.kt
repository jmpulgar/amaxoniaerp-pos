package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Área de una sucursal (en la base administrativa la tabla se sigue llamando `plantas`).
 *
 * En toda la interfaz el término visible es "Área"; "Planta" no aparece nunca ante el usuario.
 */
@Serializable
data class Area(
    val id: Int,
    val nombre: String = "",
    val descripcion: String? = null,
    val imagen: String? = null,
    val orden: Int = 0,
    val activo: Boolean = true,
    @SerialName("cantidad_mesas_activas")
    val cantidadMesasActivas: Int = 0,
) {
    val displayName: String get() = nombre.trim().ifBlank { "Área $id" }
}
