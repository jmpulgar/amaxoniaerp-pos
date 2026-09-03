package com.amaxonia.pos.domain.model.electronicinvoice

import kotlinx.serialization.Serializable

@Serializable
data class ElectronicInvoiceResultDto(
    val success: Boolean = false,
    val cufe: String? = null,
    val qr: String? = null,
    val message: String? = null,
    val alreadyIssued: Boolean = false,
    // El backend responde esta clave en camelCase (respondAlreadyIssued).
    val numeroDocumentoFiscal: String? = null,
)
