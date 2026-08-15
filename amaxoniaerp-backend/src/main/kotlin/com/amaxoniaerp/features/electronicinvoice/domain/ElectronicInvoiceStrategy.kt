package com.amaxoniaerp.features.electronicinvoice.domain

import org.jetbrains.exposed.sql.Database

/**
 * Strategy Pattern: contrato para procesamiento de facturación electrónica por país.
 *
 * - Venezuela: no requiere envío a PAC (usa impresora fiscal local). Retorna [ElectronicInvoiceResult.NotApplicable].
 * - Panamá: envía documento al PAC y recibe CUFE/QR.
 *
 * Cada implementación es independiente y no modifica la lógica del otro país.
 */
interface ElectronicInvoiceStrategy {
    val countryCode: String

    /**
     * Procesa la facturación electrónica para una factura existente en la DB.
     *
     * @param database Conexión a la base de datos de la empresa.
     * @param invoiceId UUID de la factura (`id_factura`).
     * @return Resultado del procesamiento: éxito con CUFE, fallo con código/mensaje, o no aplicable.
     */
    suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult
}
