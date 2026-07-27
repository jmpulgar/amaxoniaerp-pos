package com.amaxoniaerp.features.mesas.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Área tal como la consume el POS. `planta_id` nunca se expone: la tabla física conserva su
 * nombre, pero el contrato público habla de áreas.
 *
 * Los campos obligatorios del contrato se declaran **sin valor por defecto** a propósito: la
 * aplicación serializa con `encodeDefaults = false`, así que un default haría que el campo
 * desapareciera del JSON justo cuando vale 0 o `true`.
 */
@Serializable
data class AreaResponse(
    val id: Int,
    val nombre: String,
    val descripcion: String? = null,
    val imagen: String? = null,
    val orden: Int,
    val activo: Boolean,
    @SerialName("cantidad_mesas_activas")
    val cantidadMesasActivas: Int,
)

@Serializable
data class MesaResponse(
    val id: Int,
    @SerialName("area_id")
    val areaId: Int,
    val codigo: String? = null,
    val nombre: String,
    val capacidad: Int,
    val forma: String? = null,
    @SerialName("posicion_x")
    val posicionX: Double,
    @SerialName("posicion_y")
    val posicionY: Double,
    val ancho: Double,
    val alto: Double,
    val rotacion: Double,
    val activo: Boolean,
)

/**
 * Dimensiones del lienzo lógico del plano de mesas.
 *
 * Coordenadas de cada mesa se interpretan en este espacio: el origen (0,0) es la esquina
 * superior izquierda, [anchoLienzo] crece hacia la derecha y [altoLienzo] hacia abajo. La
 * rotación de cada mesa se aplica alrededor de su propio centro.
 *
 * Convenio acordado con el administrativo: 2000 x 1200. Se publica en la respuesta para que
 * el POS no tenga que hardcodearlo y para que un cambio de resolución contractual no requiera
 * tocar la app.
 */
@Serializable
data class Lienzo(
    @SerialName("ancho_lienzo")
    val anchoLienzo: Int,
    @SerialName("alto_lienzo")
    val altoLienzo: Int,
)

@Serializable
data class AreasListResponse(
    val success: Boolean,
    @SerialName("sucursal_id")
    val sucursalId: Int,
    val data: List<AreaResponse>,
)

/**
 * Respuesta de `GET /api/pos/areas/{areaId}/mesas`.
 *
 * - [lienzo] describe el espacio lógico del plano (2000 x 1200 por convenio). Va como objeto
 *   anidado para añadirlo sin romper el `AreaResponse` existente y porque geométricamente
 *   pertenece al área, no a cada mesa.
 * - [imagenUrl] es la URL pública del dibujo de fondo del salón si el administrativo la
 *   configuró; `null` si no existe o es blank. El POS la muestra como fondo del plano y, si
 *   falla la carga, pinta únicamente las mesas sobre un fondo neutro.
 * - [data] es la geometría completa de cada mesa activa, ya con posicion/tamaño/rotación.
 */
@Serializable
data class MesasListResponse(
    val success: Boolean,
    @SerialName("area_id")
    val areaId: Int,
    val lienzo: Lienzo,
    @SerialName("imagen_url")
    val imagenUrl: String? = null,
    val data: List<MesaResponse>,
)

/** Sucursal derivada en servidor a partir de la caja; nunca se acepta desde el cliente. */
data class CajaSucursalScope(
    val idCaja: String,
    val sucursalId: Int,
)

/**
 * Plano completo de un área: dimensiones acordadas, imagen de fondo opcional (lo que el
 * administrativo registró en `plantas.imagen`) y la geometría de las mesas activas.
 *
 * Es `null` si el área no existe, está inactiva o no pertenece a la sucursal, igual que el
 * `List<MesaResponse>?` que devolvía antes `listMesas`: responde con 404 idéntico en los tres
 * casos para no filtrar por sondeo qué ids existen en otras sucursales.
 */
data class MesasPlan(
    val lienzo: Lienzo,
    val imagenUrl: String?,
    val mesas: List<MesaResponse>,
)

/**
 * Dimensiones acordadas con el administrativo para el plano de mesas. Se exponen al POS vía
 * [MesasListResponse.lienzo] en lugar de hardcodearlas en el cliente.
 */
object LienzoDefaults {
    const val ANCHO_LIENZO = 2000
    const val ALTO_LIENZO = 1200
}

/** Resultado de validar el acceso a una caja y derivar su sucursal. */
sealed interface CajaScopeResult {
    data class Allowed(val scope: CajaSucursalScope) : CajaScopeResult

    data object CajaNotFound : CajaScopeResult

    data object AccessDenied : CajaScopeResult

    data object SucursalNotAssigned : CajaScopeResult
}
