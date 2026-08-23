package com.amaxoniaerp.features.caja.domain

/**
 * Guarda de sesión de una secuencia de caja: estado de cierre y serie de
 * sucursal necesarias para validar y persistir un cierre. Puro (sin Ktor ni
 * Exposed).
 */
data class CajaSecuenciaGuard(
    val cerrada: Boolean,
    val serieSucursal: String,
)
