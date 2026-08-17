package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext

private val PHONE_PATTERN = Regex("^\\d{4}-\\d{4}$")
private val EMAIL_PATTERN = Regex("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")
private const val DEFAULT_PHONE = "9999-9999"
private const val DEFAULT_EMAIL = "email@correo.com"
private const val DEFAULT_RUC = "00000"
private const val MIN_IDENTIFICATION_LENGTH = 5
private const val EMAIL_MIN_LENGTH = 7
private const val ADDRESS_MIN_LENGTH = 4

/**
 * Construye el bloque cliente del payload The Factory HKA:
 * normalización de tipo de contribuyente, RUC, teléfono, correo y
 * manejo de clientes extranjeros (tipo 04).
 */
internal fun buildCliente(ctx: InvoiceFEContext): TheFactoryHkaCliente {
    val cliente = ctx.cliente

    // TipoClienteFE: si viene "0" o vacío, forzar a "02" (Consumidor Final)
    val tipoClienteFE = normalizeTipoClienteFE(cliente.tipoClienteFE)
    val esExtranjero = tipoClienteFE == "04"
    val correoBase = cliente.correo?.takeIf { it.isNotBlank() } ?: DEFAULT_EMAIL

    return TheFactoryHkaCliente(
        tipoClienteFE = tipoClienteFE,
        tipoContribuyente = tipoContribuyenteFor(esExtranjero, tipoClienteFE, cliente.tipoContribuyente),
        numeroRUC = rucFor(esExtranjero, cliente.identificacion),
        digitoVerificadorRUC = if (esExtranjero) null else cliente.dv,
        razonSocial = cliente.nombre,
        direccion = (cliente.direccion?.takeIf { it.isNotBlank() } ?: " ").padStart(ADDRESS_MIN_LENGTH, '-'),
        codigoUbicacion = cliente.codigoUbicacion,
        telefono1 = telefonoFor(cliente.telefono),
        correoElectronico1 = correoFor(ctx.factura.tipoFactura, correoBase),
        tipoIdentificacion = if (esExtranjero) "01" else null,
        nroIdentificacionExtranjero = if (esExtranjero) cliente.identificacion else null,
        pais = if (esExtranjero) null else cliente.paisIso,
        paisExtranjero = if (esExtranjero) cliente.paisExtranjeroIso else null,
    )
}

private fun tipoContribuyenteFor(
    esExtranjero: Boolean,
    tipoClienteFE: String,
    tipoContribuyente: String?,
): String? =
    when {
        esExtranjero -> null
        tipoClienteFE == "02" && tipoContribuyente in setOf("", "0", "2") -> "1"
        tipoClienteFE == "02" -> "1"
        else -> tipoContribuyente
    }

// RUC: si es extranjero, vaciar. Si tiene menos de 5 chars, enviar "00000"
private fun rucFor(
    esExtranjero: Boolean,
    identificacion: String,
): String? =
    when {
        esExtranjero -> null
        identificacion.length < MIN_IDENTIFICATION_LENGTH -> DEFAULT_RUC
        else -> identificacion
    }

// Teléfono: validar formato "9999-9999"
private fun telefonoFor(telefono: String?): String =
    telefono?.let {
        if (it.matches(PHONE_PATTERN)) it else DEFAULT_PHONE
    } ?: DEFAULT_PHONE

private fun correoFor(
    tipoFactura: String?,
    correoBase: String,
): String? =
    if (tipoFactura == "factura_pos") {
        null
    } else {
        val validated = if (correoBase.matches(EMAIL_PATTERN)) correoBase else DEFAULT_EMAIL
        validated.padStart(EMAIL_MIN_LENGTH, '0')
    }
