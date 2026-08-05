package com.amaxoniaerp.features.sales.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.electronicinvoice.application.ProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

class ProcessSaleUseCase(
    private val repository: ProcessSaleTransactionalRepository,
    private val feFactory: ProcessorFactory,
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

        // FASE 1.1 — Selección explícita del mecanismo fiscal para Venezuela.
        //
        // La fuente de verdad NO es la base de datos (`parametros_generales.tipo_facturacion`)
        // sino el flag que envía el frontend en cada venta (`ProcessSaleRequest.useHka20`),
        // que el POS calcula a partir de la configuración de impresora seleccionada por el
        // usuario en Settings (`PrinterType.THE_FACTORY_HKA`).
        //
        // Reglas (Venezuela):
        //   - useHka20 == true  → el cajero utilizará la impresora fiscal HKA20 física.
        //                         El backend NO ejecuta la facturación digital Venezuela:
        //                         no autentica contra el PAC, no consulta UltimoDocumento,
        //                         no reserva correlativo digital, no emite documento digital.
        //                         La venta comercial ya quedó persistida; el POS continúa con
        //                         su flujo HKA20 existente (impresión + confirmación fiscal).
        //   - useHka20 == false / null → ejecutar la facturación digital Venezuela.
        //
        // País distinto de VE: el campo se ignora y se conserva el comportamiento actual.
        // No se permite fallback entre mecanismos: si uno falla, NUNCA se invoca el otro.
        val isHka20Selected = countryCode.equals("VE", ignoreCase = true) && request.useHka20 == true

        logger.info(
            "[FE] Evaluando FE: success=${saleResult.success} codEstatus=${saleResult.codEstatus} " +
                "esCobroCreditoPrevio=${request.esCobroCreditoPrevio} countryCode=$countryCode " +
                "useHka20=${request.useHka20} shouldProcessFE=$shouldProcessFE " +
                "hka20Selected=$isHka20Selected"
        )

        if (shouldProcessFE && isHka20Selected) {
            logger.info(
                "[FE] factura {}: Venezuela con useHka20=true. Se omite la facturación digital; " +
                    "el POS continúa con el flujo HKA20 existente.",
                saleResult.idFactura,
            )
            // No se llama al PAC digital; no se reserva correlativo digital; no se emite
            // documento digital. La venta comercial ya está confirmada en el paso 1.
            return saleResult
        }

        if (shouldProcessFE) {
            try {
                val processor = feFactory.forCountry(countryCode)
                val feResult = processor.processElectronicInvoice(database, saleResult.idFactura)

                when (feResult) {
                    is ElectronicInvoiceResult.Success -> {
                        logger.info(
                            "FE exitosa para factura {}. CUFE={} numDoc={} numCtrl={}",
                            saleResult.idFactura,
                            feResult.cufe,
                            feResult.numeroDocumentoFiscal,
                            feResult.numeroControlThka,
                        )
                        // FASE 2 (Punto 1): campos propios por país, sin reutilización.
                        // - Panamá: propaga cufe/qr/fechaRecepcionDGI (intactos).
                        // - Venezuela: propaga numeroDocumentoFiscal/numeroControlThka
                        //   (extras de Success) que ya están persistidos por la Strategy.
                        return saleResult.copy(
                            cufe = feResult.cufe,
                            qr = feResult.qr,
                            fechaRecepcionDGI = feResult.fechaRecepcionDGI,
                            numeroDocumentoFiscal = feResult.numeroDocumentoFiscal,
                            numeroControlThka = feResult.numeroControlThka,
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
                        // País sin FE (ej. HKA20 fiscal, o tipo_facturacion != 5).
                        // No hacer nada extra: la venta comercial ya está confirmada.
                    }

                    is ElectronicInvoiceResult.UnsupportedDocumentType -> {
                        // FASE 1 VE solo soporta '01'. Otro tipo no se envía a HKA.
                        logger.info(
                            "FEVE tipoDoc '{}' no soportado en FASE 1 factura {}",
                            feResult.tipoDocumento,
                            saleResult.idFactura,
                        )
                    }

                    is ElectronicInvoiceResult.AlreadyIssued -> {
                        // Idempotencia: la factura ya tiene numeración fiscal persistida.
                        logger.info(
                            "FE factura {} ya emitida previamente numDoc={}",
                            saleResult.idFactura,
                            feResult.numeroDocumentoFiscal,
                        )
                        // FASE 2 (Punto 1): los valores persistidos (Strategy VE los
                        // había guardado en factura.numeroDocumentoFiscal /
                        // factura.numero_control_thka) se devuelven sin llamar al PAC.
                        if (countryCode.equals("VE", ignoreCase = true)) {
                            return saleResult.copy(
                                numeroDocumentoFiscal = feResult.numeroDocumentoFiscal,
                                numeroControlThka = feResult.numeroControl,
                            )
                        }
                    }

                    is ElectronicInvoiceResult.Uncertain -> {
                        // timeout / respuesta incierta: NO revertir, NO duplicar,
                        // NO marcar como fallo claro. Se reporta al caller para
                        // conciliación manual con código y transaccionId.
                        logger.warn(
                            "FE incierta para factura {}: [{}] {} transaccionId={}",
                            saleResult.idFactura,
                            feResult.codigo,
                            feResult.mensaje,
                            feResult.transaccionId,
                        )
                        return saleResult.copy(
                            feError = "FE INCIERTA [${feResult.codigo}] ${feResult.mensaje}" +
                                (feResult.transaccionId?.let { " transaccionId=$it" } ?: ""),
                        )
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
