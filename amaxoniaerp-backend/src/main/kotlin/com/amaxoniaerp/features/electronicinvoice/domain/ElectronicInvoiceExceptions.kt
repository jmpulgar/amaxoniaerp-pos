package com.amaxoniaerp.features.electronicinvoice.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory

/** Error de comunicación con el PAC (timeout, conexión rechazada, etc.) */
class PacCommunicationException(
    message: String,
    cause: Throwable? = null,
) : ApiException(ErrorCategory.ExternalService, message, cause)

/** Error de autenticación contra el PAC (credenciales inválidas, token expirado). */
class PacAuthenticationException(
    message: String,
    cause: Throwable? = null,
) : ApiException(ErrorCategory.ExternalService, message, cause)

/** El PAC rechazó el documento (validación de datos, formato inválido, etc.) */
class PacRejectionException(
    val codigo: String,
    message: String,
    cause: Throwable? = null,
) : ApiException(ErrorCategory.ExternalService, message, cause)

/** Configuración FE faltante o inválida en parametros_generales. */
class FEConfigurationException(
    message: String,
) : ApiException(ErrorCategory.DomainRule, message)

/** La factura no existe o no está en estado válido para envío FE. */
class FEInvoiceNotFoundException(
    message: String,
) : ApiException(ErrorCategory.NotFound, message)

/**
 * Respuesta del PAC Venezuela cuyo resultado NO es concluyente (timeout,
 * JSON inválido, HTTP 5xx pre-emisión). El número fiscal PUEDE haberse creado
 * en el PAC pero no tenemos prueba local. No se persiste nada inventado.
 */
class VEUncertainResponseException(
    val codigo: String,
    message: String,
    cause: Throwable? = null,
) : ApiException(ErrorCategory.ExternalService, message, cause)
