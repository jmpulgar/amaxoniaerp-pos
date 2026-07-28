package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sesión operativa de una mesa. Refleja el contrato del backend
 * `amaxoniaerp/features/mesas/domain/MesasModels.kt#SesionMesaResponse`.
 *
 * - [estado] es el código de [EstadoSesionMesa] (`ABIERTA`/`CERRADA`/`CANCELADA`), NO el estado
 *   derivado de la mesa (`DISPONIBLE`/`OCUPADA`).
 * - [activo] indica si la sesión está viva (`true`) o ya terminó (`false`). Una mesa está
 *   ocupada exactamente cuando existe una sesión asociada con `activo = true` y
 *   `estado == ABIERTA`.
 */
@Serializable
data class SesionMesa(
    val id: Int = 0,
    @SerialName("sucursal_id") val sucursalId: Int = 0,
    @SerialName("caja_id") val cajaId: String = "",
    @SerialName("area_id") val areaId: Int = 0,
    @SerialName("mesa_id") val mesaId: Int = 0,
    @SerialName("usuario_id") val usuarioId: Int = 0,
    val usuario: String? = null,
    @SerialName("cantidad_personas") val cantidadPersonas: Int = 1,
    val estado: String = EstadoSesionMesa.ABIERTA,
    @SerialName("fecha_apertura") val fechaApertura: String = "",
    @SerialName("fecha_cierre") val fechaCierre: String? = null,
    val activo: Boolean = true,
)

/**
 * Estado operativo de una mesa según la sesión viva. [estado] es uno de los valores de
 * [EstadoMesaOperativo] en el backend (nombre del enum, ej. `DISPONIBLE`, `OCUPADA`).
 * Si hay sesión activa, [sesion] != null.
 */
@Serializable
data class EstadoMesaResponse(
    @SerialName("mesa_id") val mesaId: Int = 0,
    val estado: String = EstadoMesaOperativo.DISPONIBLE,
    val sesion: SesionMesa? = null,
)

@Serializable
data class EstadosMesasResponse(
    val success: Boolean = true,
    @SerialName("area_id") val areaId: Int = 0,
    val data: List<EstadoMesaResponse> = emptyList(),
    val error: String? = null,
)

/**
 * Solicitud de apertura. El backend valida `cantidad_personas >= 1`.
 */
@Serializable
data class AbrirSesionRequest(
    @SerialName("cantidad_personas") val cantidadPersonas: Int = 1,
)

@Serializable
data class AbrirSesionResponse(
    val success: Boolean = true,
    val sesion: SesionMesa = SesionMesa(),
    val error: String? = null,
)

@Serializable
data class SesionActivaResponse(
    val success: Boolean = true,
    val sesion: SesionMesa? = null,
    val error: String? = null,
)

@Serializable
data class SesionMutacionResponse(
    val success: Boolean = true,
    val sesion: SesionMesa = SesionMesa(),
    val error: String? = null,
)

/** Códigos de estado de sesión (reflejan el enum del backend). */
object EstadoSesionMesa {
    const val ABIERTA = "ABIERTA"
    const val CERRADA = "CERRADA"
    const val CANCELADA = "CANCELADA"
}

/** Estados operativos derivados de la mesa (reflejan el enum del backend). */
object EstadoMesaOperativo {
    const val DISPONIBLE = "DISPONIBLE"
    const val OCUPADA = "OCUPADA"
}
