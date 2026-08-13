package com.amaxoniaerp.features.electronicinvoice.domain

/**
 * Datos inmutables necesarios para construir una NC electrónica de Panamá.
 *
 * El número fiscal de la NC se recibe ya resuelto por el orquestador. No se
 * deriva del código interno de la devolución.
 */
data class PanamaCreditNotePayloadContext(
    val invoice: InvoiceFEContext,
    val originalInvoiceCufe: String,
    val originalInvoiceDate: String,
    val originalInvoiceFiscalNumber: String,
)
