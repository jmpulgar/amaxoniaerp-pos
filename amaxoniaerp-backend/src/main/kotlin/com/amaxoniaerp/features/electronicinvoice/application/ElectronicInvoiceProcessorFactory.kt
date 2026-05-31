package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.domain.VenezuelaInvoiceStrategy

/**
 * Factory que selecciona la estrategia de facturación electrónica
 * según el código de país del tenant.
 *
 * Nuevos países se agregan aquí sin modificar las estrategias existentes.
 */
class ElectronicInvoiceProcessorFactory(
    private val panamaProcessor: PanamaInvoiceProcessor,
) {
    fun forCountry(countryCode: String): ElectronicInvoiceStrategy =
        when (countryCode.uppercase()) {
            "PA" -> panamaProcessor
            "VE" -> VenezuelaInvoiceStrategy()
            else -> VenezuelaInvoiceStrategy() // fallback seguro: no-op
        }
}
