package com.amaxonia.pos.ui.mesas

/**
 * Modo de visualización de las mesas de un área.
 *
 * El plano solo se ofrece cuando el área tiene una distribución válida
 * ([AreasMesasState.hasDistribucionValida]); en caso contrario se cae automáticamente a
 * [LISTA] y el selector de modo no ofrece cambiar a [PLANO]. El usuario siempre puede volver
 * a [LISTA] desde [PLANO] aunque exista plano, para ESCAPAR de un fondo mal configurado o un
 * zoom incómodo.
 */
enum class SalonViewMode {
    /** Cuadrícula/lista de tarjetas. Funciona con cualquier configuración de áreas. */
    LISTA,

    /** Plano gráfico con zoom/pan y fondo opcional del área. */
    PLANO,
}
