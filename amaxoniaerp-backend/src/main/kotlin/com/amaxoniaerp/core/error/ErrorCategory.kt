package com.amaxoniaerp.core.error

/**
 * Categorías tipadas de errores de la API. `StatusPages` mapea cada categoría
 * a un código HTTP y a un mensaje público estable.
 *
 * Reglas:
 * - error interno -> log (no se filtra a la respuesta);
 * - respuesta pública -> mensaje estable;
 * - 500 nunca expone SQL/PAC/internal exception details.
 */
enum class ErrorCategory {
    Validation,
    Unauthorized,
    Forbidden,
    NotFound,
    Conflict,
    DomainRule,
    ExternalService,
    Unexpected,
}
