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

    suspend fun execute(
        database: Database,
        countryCode: String,
        request: ProcessSaleRequest,
    ): ProcessSaleResponse {
        if (request.items.isEmpty()) {
            throw InvalidSaleRequestException("La factura debe contener al menos un item")
        }

        // Paso 1: Procesar la venta (transaccional, igual que antes)
        val saleResult =
            dbQuery(database) {
                repository.process(countryCode, request)
            }

        // Paso 2: Facturación Electrónica (post-transaccional, solo si la venta fue exitosa)
        // Solo aplica si la factura fue procesada (cod_estatus == 2) y no es cobro de crédito previo.
        val shouldProcessFE =
            saleResult.success &&
                saleResult.codEstatus == 2 &&
                !request.esCobroCreditoPrevio

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
                "hka20Selected=$isHka20Selected",
        )

        return when {
            !shouldProcessFE -> saleResult
            isHka20Selected -> {
                logger.info(
                    "[FE] factura {}: Venezuela con useHka20=true. Se omite la facturación digital; " +
                        "el POS continúa con el flujo HKA20 existente.",
                    saleResult.idFactura,
                )
                // No se llama al PAC digital; no se reserva correlativo digital; no se emite
                // documento digital. La venta comercial ya está confirmada en el paso 1.
                saleResult
            }
            else -> processElectronicInvoiceSafely(database, countryCode, saleResult)
        }
    }

    private suspend fun processElectronicInvoiceSafely(
        database: Database,
        countryCode: String,
        saleResult: ProcessSaleResponse,
    ): ProcessSaleResponse =
        runCatching {
            val processor = feFactory.forCountry(countryCode)
            val feResult = processor.processElectronicInvoice(database, saleResult.idFactura)
            applyFeResult(countryCode, saleResult, feResult)
        }.getOrElse { e ->
            if (e is Error) throw e
            // Error inesperado en FE. La venta ya está guardada, no se revierte.
            logger.error(
                "Error inesperado en FE para factura {}. La venta fue procesada correctamente.",
                saleResult.idFactura,
                e,
            )
            saleResult.copy(
                feError = "Error inesperado en facturación electrónica: ${e.message}",
            )
        }

    private fun applyFeResult(
        countryCode: String,
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult,
    ): ProcessSaleResponse =
        when (feResult) {
            is ElectronicInvoiceResult.Success -> applyFeSuccess(saleResult, feResult)
            is ElectronicInvoiceResult.Failure -> applyFeFailure(saleResult, feResult)
            is ElectronicInvoiceResult.NotApplicable -> saleResult
            is ElectronicInvoiceResult.UnsupportedDocumentType -> applyFeUnsupported(saleResult, feResult)
            is ElectronicInvoiceResult.AlreadyIssued -> applyFeAlreadyIssued(countryCode, saleResult, feResult)
            is ElectronicInvoiceResult.Uncertain -> applyFeUncertain(saleResult, feResult)
        }

    private fun applyFeSuccess(
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult.Success,
    ): ProcessSaleResponse {
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

    // La venta se procesó correctamente, pero FE falló.
    // No revierte la venta: el usuario puede reintentar vía endpoint manual.
    private fun applyFeFailure(
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult.Failure,
    ): ProcessSaleResponse {
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

    private fun applyFeUnsupported(
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult.UnsupportedDocumentType,
    ): ProcessSaleResponse {
        // FASE 1 VE solo soporta '01'. Otro tipo no se envía a HKA.
        logger.info(
            "FEVE tipoDoc '{}' no soportado en FASE 1 factura {}",
            feResult.tipoDocumento,
            saleResult.idFactura,
        )
        return saleResult
    }

    private fun applyFeAlreadyIssued(
        countryCode: String,
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult.AlreadyIssued,
    ): ProcessSaleResponse {
        // Idempotencia: la factura ya tiene numeración fiscal persistida.
        logger.info(
            "FE factura {} ya emitida previamente numDoc={}",
            saleResult.idFactura,
            feResult.numeroDocumentoFiscal,
        )
        // FASE 2 (Punto 1): los valores persistidos (Strategy VE los
        // había guardado en factura.numeroDocumentoFiscal /
        // factura.numero_control_thka) se devuelven sin llamar al PAC.
        return if (countryCode.equals("VE", ignoreCase = true)) {
            saleResult.copy(
                numeroDocumentoFiscal = feResult.numeroDocumentoFiscal,
                numeroControlThka = feResult.numeroControl,
            )
        } else {
            saleResult
        }
    }

    // timeout / respuesta incierta: NO revertir, NO duplicar,
    // NO marcar como fallo claro. Se reporta al caller para
    // conciliación manual con código y transaccionId.
    private fun applyFeUncertain(
        saleResult: ProcessSaleResponse,
        feResult: ElectronicInvoiceResult.Uncertain,
    ): ProcessSaleResponse {
        logger.warn(
            "FE incierta para factura {}: [{}] {} transaccionId={}",
            saleResult.idFactura,
            feResult.codigo,
            feResult.mensaje,
            feResult.transaccionId,
        )
        return saleResult.copy(
            feError =
                "FE INCIERTA [${feResult.codigo}] ${feResult.mensaje}" +
                    (feResult.transaccionId?.let { " transaccionId=$it" } ?: ""),
        )
    }
}
