package com.amaxoniaerp.features.sales.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

class ProcessSaleUseCase(
    private val repository: ProcessSaleTransactionalRepository,
    private val feFactory: ElectronicInvoiceProcessorFactory,
) {
    private val logger = LoggerFactory.getLogger(ProcessSaleUseCase::class.java)

    suspend fun execute(database: Database, countryCode: String, request: ProcessSaleRequest): ProcessSaleResponse {
        if (request.items.isEmpty()) {
            throw InvalidSaleRequestException("La factura debe contener al menos un item")
        }

        // Paso 1: Procesar la venta (transaccional, igual que antes)
        val saleResult = dbQuery(database) {
            repository.process(countryCode, request)
        }

        // Paso 2: Facturación Electrónica (post-transaccional, solo si la venta fue exitosa)
        // Solo aplica si la factura fue procesada (cod_estatus == 2) y no es cobro de crédito previo.
        val shouldProcessFE = saleResult.success
            && saleResult.codEstatus == 2
            && !request.esCobroCreditoPrevio

        logger.info("[FE] Evaluando FE: success=${saleResult.success} codEstatus=${saleResult.codEstatus} esCobroCreditoPrevio=${request.esCobroCreditoPrevio} countryCode=$countryCode shouldProcessFE=$shouldProcessFE")

        if (shouldProcessFE) {
            try {
                val processor = feFactory.forCountry(countryCode)
                val feResult = processor.processElectronicInvoice(database, saleResult.idFactura)

                when (feResult) {
                    is ElectronicInvoiceResult.Success -> {
                        logger.info(
                            "FE exitosa para factura {}. CUFE={}",
                            saleResult.idFactura,
                            feResult.cufe,
                        )
                        return saleResult.copy(
                            cufe = feResult.cufe,
                            qr = feResult.qr,
                            fechaRecepcionDGI = feResult.fechaRecepcionDGI,
                        )
                    }

                    is ElectronicInvoiceResult.Failure -> {
                        // La venta se procesó correctamente, pero FE falló.
                        // No revierte la venta: el usuario puede reintentar vía endpoint manual.
                        logger.warn(
                            "FE fallida para factura {}: [{}] {}",
                            saleResult.idFactura,
                            feResult.codigo,
                            feResult.mensaje,
                        )
                        return saleResult.copy(
                            feError = "FE: [${feResult.codigo}] ${feResult.mensaje}",
                        )
                    }

                    is ElectronicInvoiceResult.NotApplicable -> {
                        // País sin FE (ej. Venezuela). No hacer nada extra.
                    }
                }
            } catch (e: Exception) {
                // Error inesperado en FE. La venta ya está guardada, no se revierte.
                logger.error(
                    "Error inesperado en FE para factura {}. La venta fue procesada correctamente.",
                    saleResult.idFactura,
                    e,
                )
                return saleResult.copy(
                    feError = "Error inesperado en facturación electrónica: ${e.message}",
                )
            }
        }

        return saleResult
    }
}
