package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import kotlinx.serialization.Serializable

// ─── DTOs de respuesta (Recepción) específicos de The Factory HKA Venezuela ──
// Estructura canonical observada en la documentación de FE Venezuela del PAC:
//
//   {
//     "codigo": "200",
//     "mensaje": "El documento fue procesado con éxito.",
//     "resultado": {
//       "numeroDocumento": "0000003500",
//       "numeroControl":   "L001P0010000003500",
//       "serial":          "...",
//       "codigoGeneracion": "..."
//     },
//     "validaciones": []
//   }
//
// Las rutas exactas (Autenticacion, Consultar_Ultimo_Documento, Emision_Procesar)
// viven en VenezuelaHkaRestClient. Aquí solo se modelan los payloads.

/**
 * Body de autenticación: `POST /api/Autenticacion`.
 *
 * Mismo contrato que Panamá (usuario + clave). La caducidad del JWT es decripción
 * del entorno (demo/producción) y se trata en el cliente.
 */
@Serializable
data class VenezuelaHkaAuthRequest(
    val usuario: String,
    val clave: String,
)

/**
 * Respuesta de autenticación. El PAC VE retorna `token` (JWT) + `mensaje`.
 */
@Serializable
data class VenezuelaHkaAuthResponse(
    val token: String? = null,
    val mensaje: String? = null,
)

/**
 * Body de `POST /api/Consultar_Ultimo_Documento` (o equivalente configurado).
 *
 * @param serie Serie fiscal provista por el tenant (ej. "L001P001").
 * @param tipoDocumentoAlways "01" en FASE 1 (factura). Se mantiene explícito
 *   por requerimiento del Swagger VE (el PAC exige indicar el tipo).
 */
@Serializable
data class VenezuelaHkaUltimoDocumentoRequest(
    val serie: String,
    val tipoDocumento: String,
)

/**
 * Respuesta de último documento. El PAC VE devuelve el último número fiscal
 * emitido para esa serie/tipo en `resultado.ultimoNumero`.
 */
@Serializable
data class VenezuelaHkaUltimoDocumentoResponse(
    val codigo: String? = null,
    val mensaje: String? = null,
    val resultado: VenezuelaHkaUltimoDocumentoResultado? = null,
    val validaciones: List<String> = emptyList(),
)

@Serializable
data class VenezuelaHkaUltimoDocumentoResultado(
    val ultimoNumero: String? = null,
)

/**
 * Respuesta de `POST /api/Emision_Procesar` (o equivalente configurado).
 *
 * La emisión se considera exitosa únicamente cuando:
 *   - codigo == "200"
 *   - resultado.numeroDocumento no está vacío
 */
@Serializable
data class VenezuelaHkaEmisionResponse(
    val codigo: String? = null,
    val mensaje: String? = null,
    val resultado: VenezuelaHkaEmisionResultado? = null,
    val validaciones: List<String> = emptyList(),
)

@Serializable
data class VenezuelaHkaEmisionResultado(
    val numeroDocumento: String? = null,
    val numeroControl: String? = null,
    val serial: String? = null,
    val codigoGeneracion: String? = null,
)
