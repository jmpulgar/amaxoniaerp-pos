package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.PanamaCreditNotePayloadContext

/**
 * Construye el payload The Factory HKA para una nota de crédito panameña.
 *
 * Las reglas comunes de emisor, receptor, líneas, impuestos y pagos siguen
 * viviendo en [TheFactoryHkaPayloadBuilder]. Esta clase sólo aporta el tipo de
 * documento y la referencia fiscal a la factura original.
 */
class TheFactoryHkaCreditNotePayloadBuilder(
    private val invoicePayloadBuilder: TheFactoryHkaPayloadBuilder = TheFactoryHkaPayloadBuilder(),
) {
    companion object {
        private const val CUFE_LENGTH = 66
        private const val FISCAL_NUMBER_LENGTH = 10
        private const val BILLING_POINT_LENGTH = 3
    }

    fun build(context: PanamaCreditNotePayloadContext): TheFactoryHkaDocumentoWrapper {
        require(context.originalInvoiceCufe.isNotBlank()) {
            "La factura original electrónica debe tener CUFE"
        }
        require(context.originalInvoiceCufe.length == CUFE_LENGTH) {
            "El CUFE de la factura original debe tener 66 caracteres"
        }
        require(context.originalInvoiceDate.isNotBlank()) {
            "La factura original debe tener fecha de emisión"
        }
        require(context.originalInvoiceFiscalNumber.isNotBlank()) {
            "La factura original debe tener número fiscal"
        }
        val numeroDocumentoFiscal = normalizeFiscalNumber(context.invoice.factura.numeroDocumentoFiscal)
        val puntoFacturacionFiscal = normalizeBillingPoint(context.invoice.puntoFacturacionFiscal)

        val creditNoteContext =
            context.invoice.copy(
                factura =
                    context.invoice.factura.copy(
                        tipoDocumento = "04",
                        numeroDocumentoFiscal = numeroDocumentoFiscal,
                    ),
                puntoFacturacionFiscal = puntoFacturacionFiscal,
            )
        val referencedDocument =
            TheFactoryHkaDocFiscalRef(
                fechaEmisionDocFiscalReferenciado =
                    invoicePayloadBuilder
                        .formatFechaEmisionForPayload(context.originalInvoiceDate),
                cufeFEReferenciada = context.originalInvoiceCufe,
            )

        return invoicePayloadBuilder.build(
            context = creditNoteContext,
            documentosFiscalesReferenciados = listOf(referencedDocument),
        )
    }

    private fun normalizeFiscalNumber(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotBlank() && normalized.all(Char::isDigit) && normalized.length <= FISCAL_NUMBER_LENGTH) {
            "El número fiscal de la nota de crédito debe ser numérico de hasta 10 dígitos"
        }
        require(normalized.any { it != '0' }) {
            "El número fiscal de la nota de crédito no puede ser cero"
        }
        return normalized.padStart(FISCAL_NUMBER_LENGTH, '0')
    }

    private fun normalizeBillingPoint(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotBlank() && normalized.all(Char::isDigit) && normalized.length <= BILLING_POINT_LENGTH) {
            "El punto de facturación fiscal debe ser numérico de hasta 3 dígitos"
        }
        require(normalized.any { it != '0' }) {
            "El punto de facturación fiscal no puede ser cero"
        }
        return normalized.padStart(BILLING_POINT_LENGTH, '0')
    }
}
