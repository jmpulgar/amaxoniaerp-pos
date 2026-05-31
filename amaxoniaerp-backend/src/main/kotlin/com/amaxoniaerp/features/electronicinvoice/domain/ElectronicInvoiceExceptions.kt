package com.amaxoniaerp.features.electronicinvoice.domain

/**
 * Excepciones específicas del módulo de facturación electrónica.
 */

/** Error de comunicación con el PAC (timeout, conexión rechazada, etc.) */
class PacCommunicationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Error de autenticación contra el PAC (credenciales inválidas, token expirado). */
class PacAuthenticationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** El PAC rechazó el documento (validación de datos, formato inválido, etc.) */
class PacRejectionException(
    val codigo: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Configuración FE faltante o inválida en parametros_generales. */
class FEConfigurationException(message: String) :
    RuntimeException(message)

/** La factura no existe o no está en estado válido para envío FE. */
class FEInvoiceNotFoundException(message: String) :
    RuntimeException(message)
