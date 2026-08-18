package com.amaxoniaerp.core.error

/**
 * Excepción base con categoría tipada para errores de la API. El mensaje es el
 * mensaje público estable que `StatusPages` expone; el cause (SQL/PAC/internos)
 * solo se loguea y nunca se devuelve al cliente.
 */
open class ApiException(
    val category: ErrorCategory,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
