package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceStrategy

/**
 * Puerto (interface) para el obtensor deestrategias FE.
 *
 * **FASE 1.1 — Brief item 8:** en lugar de abrir clases de producción sólo para
 * inyectar fakes en tests (anti-patrón), se extrae este puerto. La
 * implementación productiva [ElectronicInvoiceProcessorFactory] queda `final`.
 * Los tests inyectan sus propias implementaciones de [ProcessorFactory] sin
 * heredar de la factory real.
 */
interface ProcessorFactory {
    fun forCountry(countryCode: String): ElectronicInvoiceStrategy
}

/**
 * Factory que selecciona la estrategia de facturación electrónica
 * según el código de país del tenant.
 *
 * Nuevos países se agregan aquí sin modificar las estrategias existentes.
 *
 * **FASE 1.1:** la selección HKA20 (impresora fiscal física del POS) vs facturación
 * digital Venezuela **no** vive aquí: la decide el `ProcessSaleUseCase` a partir
 * del flag `ProcessSaleRequest.useHka20` enviado por el frontend, mucho antes de
 * invocar a `forCountry("VE")`. Si el flujo llega al factory es porque debe haber FE.
 *
 * @param panamaProcessor Panamá: PAC The Factory HKA FEL.
 * @param venezuelaProcessor Venezuela: PAC The Factory HKA Facturación Electrónica.
 */
class ElectronicInvoiceProcessorFactory(
    private val panamaProcessor: ElectronicInvoiceStrategy,
    private val venezuelaProcessor: ElectronicInvoiceStrategy,
) : ProcessorFactory {
    override fun forCountry(countryCode: String): ElectronicInvoiceStrategy =
        when (countryCode.uppercase()) {
            "PA" -> panamaProcessor
            "VE" -> venezuelaProcessor
            else -> NoOpElectronicInvoiceStrategy(countryCode)
        }
}

/** Estrategia no-op para países sin FE. Evita `null` y respeta el contrato. */
private class NoOpElectronicInvoiceStrategy(
    override val countryCode: String,
) : ElectronicInvoiceStrategy {
    override suspend fun processElectronicInvoice(
        database: org.jetbrains.exposed.sql.Database,
        invoiceId: String,
    ): com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult =
        com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
            .NotApplicable(countryCode)
}
