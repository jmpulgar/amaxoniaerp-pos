package com.amaxonia.pos.domain.model.mesas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AreasResponse(
    val success: Boolean = true,
    @SerialName("sucursal_id")
    val sucursalId: Int = 0,
    val data: List<Area> = emptyList(),
    val error: String? = null,
)

/**
 * Dimensiones del lienzo lógico del plano (2000 x 1200 por convenio con el administrativo).
 *
 * Las coordenadas [Mesa.posicionX]/[Mesa.posicionY] son la esquina superior izquierda y la
 * rotación se aplica alrededor del centro. El POS escala el lienzo proporcionalmente al
 * viewport y nunca lo hardcodea: lo recibe del backend.
 */
@Serializable
data class Lienzo(
    @SerialName("ancho_lienzo")
    val ancho: Int = LIENZO_ANCHO_DEFAULT,
    @SerialName("alto_lienzo")
    val alto: Int = LIENZO_ALTO_DEFAULT,
) {
    /** Devuelve el ancho cubierto o el default si el backend envió 0/negativo. */
    val anchoEfectivo: Int get() = if (ancho > 0) ancho else LIENZO_ANCHO_DEFAULT

    /** Devuelve el alto cubierto o el default si el backend envió 0/negativo. */
    val altoEfectivo: Int get() = if (alto > 0) alto else LIENZO_ALTO_DEFAULT

    private companion object {
        // Default defensivo: si el backend omitiera los campos o enviara valores inválidos, se
        // sigue pintando sobre el espacio contractual 2000x1200 sin romper el plano.
        const val LIENZO_ANCHO_DEFAULT = 2000
        const val LIENZO_ALTO_DEFAULT = 1200
    }
}

@Serializable
data class MesasResponse(
    val success: Boolean = true,
    @SerialName("area_id")
    val areaId: Int = 0,
    val lienzo: Lienzo = Lienzo(),
    @SerialName("imagen_url")
    val imagenUrl: String? = null,
    val data: List<Mesa> = emptyList(),
    val error: String? = null,
)

/**
 * Áreas resueltas para una caja. [sucursalId] lo decide el backend a partir de la caja: el
 * cliente nunca lo envía, solo lo recibe para mostrarlo y para verificar coherencia de caché.
 */
data class AreasResult(
    val sucursalId: Int,
    val areas: List<Area>,
    val fromCache: Boolean = false,
)

/**
 * Plan completo de un área: dimensiones del lienzo, URL del dibujo de fondo (lo que el
 * administrativo registró en `plantas.imagen`) y la geometría de las mesas.
 *
 * [imagenUrl] se separa de `Area.imagen` porque puede llegar distinta (el endpoint de mesas
 * puede normalizar la URL pública). El composable la prioriza sobre la del área y, si falla o
 * está vacía, pinta solo las mesas sobre fondo neutro.
 */
data class MesasResult(
    val areaId: Int,
    val lienzo: Lienzo,
    val imagenUrl: String?,
    val mesas: List<Mesa>,
    val fromCache: Boolean = false,
)

/**
 * Mesa elegida por el usuario, viva solo en memoria durante la sesión de la app.
 *
 * Seleccionar una mesa en esta fase no abre venta, no crea pedido y no escribe nada en el
 * backend: es únicamente el contexto que consumirá la apertura operativa en la siguiente fase.
 */
data class SelectedTable(
    val sucursalId: Int,
    val area: Area,
    val mesa: Mesa,
)
