package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import kotlinx.serialization.Serializable

// ─── DTOs de respuesta específicos de The Factory HKA ────────────────────────
// Estos DTOs representan la estructura JSON exacta que devuelve el API de
// The Factory HKA. Se usan internamente para deserializar la respuesta
// y luego se transforman al PacResponse estandarizado.

/**
 * Respuesta del endpoint `POST /api/Autenticacion`.
 */
@Serializable
data class TheFactoryAuthResponse(
    val token: String? = null,
    val mensaje: String? = null,
)

/**
 * Respuesta del endpoint `POST /api/Enviar`.
 */
@Serializable
data class TheFactoryEnviarResponse(
    val codigo: String? = null,
    val resultado: String? = null,
    val mensaje: String? = null,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val nroProtocoloAutorizacion: String? = null,
    val fechaLimite: String? = null,
)

/**
 * Body del endpoint `POST /api/EnvioCorreo`.
 */
@Serializable
data class TheFactoryEnviarCorreoRequest(
    val cufe: String,
    val correos: List<String>,
)

/**
 * Respuesta del endpoint `POST /api/EnvioCorreo`.
 */
@Serializable
data class TheFactoryEnviarCorreoResponse(
    val codigo: String? = null,
    val resultado: String? = null,
    val mensaje: String? = null,
    val validaciones: List<String> = emptyList(),
    val cufe: String? = null,
)

/**
 * Body de autenticación para `POST /api/Autenticacion`.
 */
@Serializable
data class TheFactoryAuthRequest(
    val usuario: String,
    val clave: String,
)
