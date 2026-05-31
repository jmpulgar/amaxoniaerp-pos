package com.amaxoniaerp.features.electronicinvoice.domain

import org.jetbrains.exposed.sql.Database

/**
 * Implementación No-Op de [ElectronicInvoiceStrategy] para Venezuela.
 *
 * Venezuela utiliza impresoras fiscales locales para la emisión de documentos.
 * La confirmación fiscal se maneja a través del endpoint existente
 * `PATCH /facturas/{id}/confirmacion-fiscal` y NO se modifica.
 *
 * Esta clase existe únicamente para que el Factory Pattern funcione de forma
 * limpia sin condicionales dispersos en el código.
 */
class VenezuelaInvoiceStrategy : ElectronicInvoiceStrategy {

    override val countryCode: String = "VE"

    override suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult {
        // Venezuela no envía documentos a un PAC remoto.
        // Retorna inmediatamente indicando que FE no aplica.
        return ElectronicInvoiceResult.NotApplicable(countryCode)
    }
}
