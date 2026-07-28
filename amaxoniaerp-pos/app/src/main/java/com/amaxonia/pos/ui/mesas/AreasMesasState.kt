package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.EstadoMesaOperativo
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.SalonForma
import com.amaxonia.pos.domain.model.mesas.SesionMesa

/**
 *Estado de la pantalla de Áreas y mesas.
 *
 * El plano visual solo se ofrece cuando el área seleccionada tiene una distribución válida
 * (al menos una mesa con geometría real y no toda desbordada o apilada en 0,0). En caso
 * contrario se cae automáticamente a la vista de cuadrícula/lista, y el usuario puede seguir
 * alternando con [SalonViewMode].
 */
data class AreasMesasState(
    /** Nombre de la sucursal derivada de la caja activa, solo informativo. */
    val sucursalNombre: String = "",
    val sucursalId: Int? = null,
    /** `true` cuando no hay caja seleccionada: sin caja no hay sucursal y por tanto no hay áreas. */
    val requiresCaja: Boolean = false,
    val areas: List<Area> = emptyList(),
    val selectedAreaId: Int? = null,
    val mesas: List<Mesa> = emptyList(),
    val selectedMesaId: Int? = null,
    val isLoadingAreas: Boolean = false,
    val isLoadingMesas: Boolean = false,
    val areasError: String? = null,
    val mesasError: String? = null,
    val isOffline: Boolean = false,
    /** Los datos mostrados provienen del último snapshot descargado, no de la red. */
    val showingCachedData: Boolean = false,
    /** Lienzo lógico del área seleccionada (2000x1200 por convenio). Lo trae el endpoint. */
    val lienzo: Lienzo = Lienzo(),
    /** URL del dibujo de fondo del plano del área, o `null` si no se configuró. */
    val imagenUrl: String? = null,
    /** Modo actual de visualización de las mesas del área. */
    val viewMode: SalonViewMode = SalonViewMode.PLANO,
    /**
     * `true` si el área tiene una distribución utilizable para pintar el plano. Si no, el modo
     * [SalonViewMode.PLANO] no se ofrece y se cae automáticamente a [SalonViewMode.LISTA].
     */
    val hasDistribucionValida: Boolean = false,
    /**
     * Mapa de `mesaId` -> estado operativo (uno de [EstadoMesaOperativo]).
     *
     * Se rellena al cargar/refrescar las mesas del área consultando `sesiones/estados`. Si está
     * vacío significa "no hidratado todavía": la UI debe pintar las mesas como disponibles por
     * defecto y nunca inferir ocupación de otros campos.
     */
    val estadosMesas: Map<Int, String> = emptyMap(),
    /**
     * Sesión activa recuperada al pulsar una mesa ocupada, viva solo en memoria de la pantalla.
     * No se persiste: tras navegar fuera se pierde y se recomienda recuperarla de nuevo al volver.
     */
    val activeSesion: SesionMesa? = null,
    /** Carga/mutación de sesión en curso (apertura, recuperación, cierre, cancelación). */
    val isLoadingSesion: Boolean = false,
    /** Mensaje de error operación de sesión a mostrar como snackbar/dialog. Se limpia al reintentar. */
    val sesionError: String? = null,
    /** `true` mientras pide `estados` (lo muestra como estado de carga en plano y lista). */
    val isLoadingEstados: Boolean = false,
) {
    val selectedArea: Area? get() = areas.firstOrNull { it.id == selectedAreaId }

    val selectedMesa: Mesa? get() = mesas.firstOrNull { it.id == selectedMesaId }

    val isAreasEmpty: Boolean
        get() = areas.isEmpty() && !isLoadingAreas && areasError == null && !requiresCaja

    val isMesasEmpty: Boolean
        get() = selectedAreaId != null && mesas.isEmpty() && !isLoadingMesas && mesasError == null

    /** Mientras haya cualquier consulta en vuelo los chips no aceptan pulsaciones. */
    val areAreaChipsEnabled: Boolean get() = !isLoadingAreas && !isLoadingMesas

    /**
     * `true` cuando el plano es pintable: hay mesas, distribución válida y el usuario eligió
     * [SalonViewMode.PLANO]. Encapsula la regla para que la UI solo decida cuál compositor
     * invocar.
     */
    val canShowPlano: Boolean
        get() = mesas.isNotEmpty() && hasDistribucionValida && viewMode == SalonViewMode.PLANO

    /** Etiqueta corta de cada forma conocida, para mostrar junto a cada mesa. */
    fun formaLabel(mesa: Mesa): String = SalonForma.labelOf(mesa.forma)

    /**
     * Estado operativo de una mesa, o [EstadoMesaOperativo.DISPONIBLE] cuando no haya datos
     * hidratados (serve como "disponible fresco" en UI).
     *
     * La UI debe distinguir "no hidratado" (`estadosMesas.isEmpty()`) de "todas disponibles":
     * en el primer caso no debería mostrar baliza de estado todavía.
     */
    fun estadoOperativo(mesaId: Int): String = estadosMesas[mesaId] ?: EstadoMesaOperativo.DISPONIBLE

    /** `true` si la mesa está ocupada por una sesión activa (requiere `estadosMesas` cargados). */
    fun isOcupada(mesaId: Int): Boolean = estadosMesas[mesaId] == EstadoMesaOperativo.OCUPADA

    /** `true` cuando `estadosMesas` ya trajo datos para el área actual. */
    val hasEstadosHidratados: Boolean get() = estadosMesas.isNotEmpty()
}

/** Acción opcional de un estado informativo, agrupada para no alargar la firma del composable. */
data class InfoAction(
    val label: String,
    val onClick: () -> Unit,
)
