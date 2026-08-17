package com.amaxoniaerp.features.electronicinvoice.domain

import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClientException
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaEmisionResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaResponse
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private const val HTTP_SERVER_ERROR_MIN = 500

private val evaluationLog = LoggerFactory.getLogger("VenezuelaEmissionEvaluation")

/** Falla interna de un paso del flujo VE; envuelve el resultado final. */
internal class VeStepFailure(
    val result: ElectronicInvoiceResult,
    cause: Throwable? = null,
) : RuntimeException(cause)

/** Desenvuelve la falla de un paso; si no es esperada, la re-lanza. */
internal fun stepFailure(e: Throwable): ElectronicInvoiceResult = (e as? VeStepFailure)?.result ?: throw e

// Timeout/incertidumbre: NO persistir, NO marcar exitoso, NO reintentar.
internal fun logEmisionUncertain(
    invoiceId: String,
    numeroFinal: String,
    e: VenezuelaHkaClientException,
) {
    evaluationLog.error(
        "[VE-FE] TIMEOUT/RED en Emision factura={} numeroFinal={}. NO se persiste nada.",
        invoiceId,
        numeroFinal,
        e,
    )
}

/**
 * Paso 11: evalúa la respuesta de Emision con separación estricta de capas.
 * Exito exacto: HTTP 2xx + codigo == "200" + resultado.numeroDocumento no vacío.
 * Si NO es éxito exacto: lanza [VeStepFailure] con Uncertain/Failure según
 * la naturaleza de la respuesta.
 */
internal suspend fun VenezuelaElectronicInvoiceRepository.evaluateEmission(
    database: Database,
    invoiceId: String,
    payload: VenezuelaHkaDocumentoWrapper,
    emission: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>,
    countryCode: String,
) {
    val exitoExacto =
        emission.httpOk &&
            emission.businessOk &&
            !emission.resultado
                ?.resultado
                ?.numeroDocumento
                .isNullOrBlank()
    if (exitoExacto) {
        persistEmissionResult(database, invoiceId, emission)
        return
    }

    logEmissionRejected(invoiceId, emission)
    val esIncierto = esRespuestaIncierta(emission)
    throw VeStepFailure(
        if (esIncierto) {
            ElectronicInvoiceResult.Uncertain(
                country = countryCode,
                codigo = emission.codigo,
                mensaje = emission.mensaje,
                transaccionId = payload.documento.datosTransaccion.transaccionId,
            )
        } else {
            ElectronicInvoiceResult.Failure(
                codigo = emission.codigo,
                mensaje = emission.mensaje.ifBlank { "Emisión rechazada por HKA VE" },
            )
        },
    )
}

private fun logEmissionRejected(
    invoiceId: String,
    emission: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>,
) {
    evaluationLog.warn(
        "[VE-FE] Emision NO exitosa factura={} http={} codigo={} mensaje={} validaciones={} resultado={}",
        invoiceId,
        emission.httpStatus,
        emission.codigo,
        emission.mensaje,
        emission.validaciones,
        emission.resultado,
    )
}

// Incertidumbre: HTTP 5xx o respuesta ilegible con businessOk.
private fun esRespuestaIncierta(emission: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>): Boolean =
    emission.httpStatus >= HTTP_SERVER_ERROR_MIN ||
        emission.businessOk &&
        emission.resultado
            ?.resultado
            ?.numeroDocumento
            .isNullOrBlank()

/**
 * Paso 12: persistencia atómica de los tres campos fiscales. Best-effort
 * tras aceptación del PAC: el documento ya fue creado; un fallo aquí NO se
 * reintenta ciegamente (duplicaría el número) y queda para conciliación.
 */
private suspend fun VenezuelaElectronicInvoiceRepository.persistEmissionResult(
    database: Database,
    invoiceId: String,
    emission: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>,
) {
    val numDoc =
        emission.resultado!!
            .resultado!!
            .numeroDocumento!!
            .trim()
    val numCtrl =
        emission.resultado.resultado
            ?.numeroControl
            ?.trim()
            .orEmpty()
    runCatching {
        updateInvoiceWithVEResult(
            database = database,
            invoiceId = invoiceId,
            numeroDocumento = numDoc,
            numeroControl = numCtrl,
        )
    }.onFailure { e ->
        if (e is Error) throw e
        // Documento creado en el PAC pero no persistido: no se puede reintentar
        // ciegamente porque duplicaría el número. Devolvemos Success con log
        // crítico; el operador debe reconciliar manualmente.
        evaluationLog.error(
            "[VE-FE] Emision OK pero fallo al persistir factura={} numDoc={} numCtrl={}",
            invoiceId,
            numDoc,
            numCtrl,
            e,
        )
    }
    evaluationLog.info(
        "[VE-FE] emisión exitosa factura={} numDoc={} numCtrl={}",
        invoiceId,
        numDoc,
        numCtrl,
    )
}

/**
 * Paso 13: recarga la factura persistida y construye el Success
 * EXCLUSIVAMENTE con lo persistido en BD (nunca con el objeto inmediato
 * retornado por HKA).
 */
internal suspend fun VenezuelaElectronicInvoiceRepository.buildSuccessFromPersisted(
    database: Database,
    invoiceId: String,
): ElectronicInvoiceResult {
    val persisted = loadFiscalDataForResponse(database, invoiceId)
    // FASE 2 (Punto 1): la Strategy VE devuelve campos PROPIOS (numeroDocumentoFiscal,
    // numeroControlThka). NO reutiliza cufe/qr/nroProtocoloAutorizacion/fechaRecepcionDGI
    // de Panamá (que se conservan en null y nunca se persisten ni imprimen en VE).
    return ElectronicInvoiceResult.Success(
        // Panamá: todos null en VE.
        cufe = null,
        qr = null,
        fechaRecepcionDGI = null,
        nroProtocoloAutorizacion = null,
        fechaLimite = null,
        // Venezuela digital: valores efectivamente persistidos en factura.
        numeroDocumentoFiscal = persisted.numeroDocumentoFiscal,
        numeroControlThka = persisted.numeroControlThka,
    )
}
