package com.amaxoniaerp.features.electronicinvoice.domain

import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClientException
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaUltimoDocumentoRequest
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaEmisionResponse

private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Estrategia concreta [ElectronicInvoiceStrategy] para Venezuela con The Factory
 * HKA Facturación Electrónica (FASE 1 - solo facturas tipoDocumento "01").
 *
 * Flujo (post transacción comercial; el `_UseCase_` ya hizo COMMIT):
 *
 *   1. Cargar contexto (lectura). Si la configuración FE VE falta o es inválida
 *      se retorna [ElectronicInvoiceResult.NotApplicable] sin propagar error.
 *   2. Recargar la factura (idempotencia): si ya tiene número fiscal o control
 *      persistidos, retornar [ElectronicInvoiceResult.AlreadyIssued] sin
 *      llamar a HKA.
 *   3. Verificar `tipoDocumento == "01"`. Otro tipo →
 *      [ElectronicInvoiceResult.UnsupportedDocumentType].
 *   4. Autenticarse con HKA. Si falla credenciales → Failure(AUTH_REJECTED).
 *   5. Consultar UltimoDocumento(serie, "01") para conocer el remoto.
 *   6. Reservar correlativo local ATÓMICAMENTE (transacción breve y cerrada).
 *   7. Construir el payload con número = max(local reservado, remoto+1).
 *   8. Enviar al PAC Emision_Procesar.
 *   9. Evaluar respuesta (HTTP, código, resultado, validaciones).
 *  10. En éxito EXACTO (codigo==200 + resultado.numeroDocumento no vacío):
 *      persistir `numeroDocumentoFiscal` + `numero_control_thka` (atómico).
 *  11. En timeout/red/incertidumbre: NO persistir, retornar Uncertain.
 *      NO reintentar.
 *
 * SELECCIÓN HKA20 vs DIGITAL (FASE 1.1)
 * --------------------------------------
 * La decisión entre la impresora fiscal HKA20 física (POS) y la facturación
 * digital Venezuela (PAC) NO se deduce aquí de `parametros_generales.tipo_facturacion`.
 * La fuente de verdad es el flag explícito del frontend (`ProcessSaleRequest.useHka20`),
 * evaluado en `ProcessSaleUseCase.execute(...)` ANTES de invocar la strategy. Si el
 * uso del HKA20 es `true`, esta strategy **no se llama**: el backend sólo persiste
 * la venta comercial y el POS continúa con su flujo HKA20 existente. Si el flujo
 * llega a `processElectronicInvoice` es porque.useHka20 != true.
 */
class VenezuelaInvoiceStrategy(
    private val repository: VenezuelaElectronicInvoiceRepository,
    private val hkaClient: VenezuelaHkaClient,
    private val payloadBuilder: VenezuelaHkaPayloadBuilder,
    /** Serie por defecto para Consultar_Ultimo_Documento. Sobreescribible por tenant. */
    private val defaultSerie: String = "L001P001",
) : ElectronicInvoiceStrategy {
    override val countryCode: String = "VE"

    private val log = LoggerFactory.getLogger(VenezuelaInvoiceStrategy::class.java)

    override suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult =
        runCatching {
            emissionFlow(database, invoiceId)
        }.getOrElse { e ->
            stepFailure(e)
        }

    private suspend fun emissionFlow(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult = run {
        // 1. Contexto + idempotencia + tipo soportado (lecturas previas).
        val context =
            loadContextStep(database, invoiceId).getOrElse { e ->
                return@run stepFailure(e)
            }
        runCatching { checkAlreadyIssuedStep(database, invoiceId) }.getOrElse { e ->
            return@run stepFailure(e)
        }
        runCatching { checkTipoDocumentoStep(invoiceId, context) }.getOrElse { e ->
            return@run stepFailure(e)
        }

        // 5. Autenticación con HKA.
        val token =
            authenticateStep(invoiceId, context).getOrElse { e ->
                return@run stepFailure(e)
            }

        // 6. Último documento remoto para alinear el correlativo con el PAC.
        val serie = context.caja.serieSucursal?.ifBlank { null } ?: defaultSerie
        val ultimoRemoto =
            fetchLastDocumentStep(invoiceId, context, token, serie).getOrElse { e ->
                return@run stepFailure(e)
            }

        // 7. Reserva atómica del correlativo LOCAL (FASE 1.1 — Brief item 3:
        //    reserveAtLeast). El mínimo se calcula a partir del remoto del PAC
        //    y se pasa al repositorio; ya NO se hace max() en la Strategy ni
        //    en el Builder. La transacción SQL se abre, reserva y commit en un
        //    solo bloque autocontenido — nunca se mantiene abierta durante HTTP.
        val minimumNextNumber = (ultimoRemoto ?: 0) + 1
        val reservado =
            runCatching {
                repository.reserveAtLeast(database, minimumNextNumber = minimumNextNumber)
            }.getOrElse { e ->
                if (e is Error) throw e
                if (e is FEConfigurationException) {
                    log.error("[VE-FE] no se pudo reservar correlativo factura {}", invoiceId, e)
                    throw VeStepFailure(
                        ElectronicInvoiceResult.Failure(
                            "CORRELATIVO_CONFIG",
                            e.message ?: "Configuración correlativo inválida",
                        ),
                    )
                }
                log.error("[VE-FE] fallo inesperado reservando correlativo factura {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure(
                        "CORRELATIVO_LOCK",
                        e.message ?: "No se pudo reservar correlativo",
                    ),
                )
            }

        // 8. Número efectivo final = número reservado (YA respeta max(local, remoto+1)).
        val numeroFinal = reservado.numeroFormateado()
        log.info(
            "[VE-FE] numero a enviar: minimumNextNumber={} reservado={} final={} factura={}",
            minimumNextNumber,
            reservado.numero,
            numeroFinal,
            invoiceId,
        )

        // 9. Construcción del payload.
        val payload =
            runCatching {
                payloadBuilder.build(
                    context = context,
                    serie = serie,
                    numeroDocumentoFiscalFinal = numeroFinal,
                )
            }.getOrElse { e ->
                if (e is Error) throw e
                log.error("[VE-FE] fallo construyendo payload factura {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure("BUILD_ERROR", e.message ?: "Error construyendo payload"),
                )
            }

        // 10. Enviar a Emision.
        val emission =
            runCatching {
                hkaClient.emitDocument(
                    baseUrl = context.config.baseUrl,
                    token = token,
                    payload = payload,
                )
            }.getOrElse { e ->
                if (e !is VenezuelaHkaClientException) throw e
                logEmisionUncertain(invoiceId, numeroFinal, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Uncertain(
                        country = countryCode,
                        codigo = if (e is VenezuelaHkaClientException.Timeout) "EMISION_TIMEOUT" else "EMISION_NET_ERROR",
                        mensaje = (e.message ?: "Respuesta incierta del PAC VE"),
                        transaccionId = payload.documento.datosTransaccion.transaccionId,
                    ),
                )
            }

        // 11-12. Evaluar respuesta y, si es éxito exacto, persistir.
        runCatching { evaluateEmission(database, invoiceId, payload, emission) }.getOrElse { e ->
            return@run stepFailure(e)
        }

        // 13. Success construido EXCLUSIVAMENTE con lo persistido en BD.
        buildSuccessFromPersisted(database, invoiceId)
    }

    /** Paso 1: carga el contexto de la factura. */
    private suspend fun loadContextStep(
        database: Database,
        invoiceId: String,
    ): Result<InvoiceVEContext> =
        runCatching {
            try {
                repository.loadInvoiceContext(database, invoiceId)
            } catch (e: FEInvoiceNotFoundException) {
                log.error("[VE-FE] factura no encontrada: {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure("INVOICE_NOT_FOUND", e.message ?: "Factura no encontrada"),
                )
            } catch (e: FEConfigurationException) {
                // Sin config FE VE → no aplica HKA. No lanzar excepción al caller.
                log.warn("[VE-FE] configuración FE VE incompleta para factura {}: {}", invoiceId, e.message)
                throw VeStepFailure(ElectronicInvoiceResult.NotApplicable(countryCode))
            }
        }

    /**
     * Paso 3: idempotencia con semántica OR (FASE 1.1 — Brief item 1).
     * Cualquiera de los dos campos fiscales presente implica "ya procesada":
     * NO se debe llamar al PAC. La condición correcta es OR, no AND.
     *   - Complete → AlreadyIssued (éxito idempotente).
     *   - Partial  → Failure(PARTIAL_FISCAL_DATA): no se puede reemitir
     *                a ciegas porque generaría duplicado; requiere
     *                reconciliación manual.
     *   - None     → continuar con el flujo de emisión.
     */
    private suspend fun checkAlreadyIssuedStep(
        database: Database,
        invoiceId: String,
    ) {
        val alreadyIssued = repository.loadAlreadyIssued(database, invoiceId)
        when (alreadyIssued) {
            is AlreadyIssuedResult.Complete -> {
                log.info(
                    "[VE-FE] factura {} ya emitida (Complete) numeroDocumentoFiscal={} numero_control_thka={}. " +
                        "No se llama HKA.",
                    invoiceId,
                    alreadyIssued.numeroDocumentoFiscal,
                    alreadyIssued.numeroControl,
                )
                throw VeStepFailure(
                    ElectronicInvoiceResult.AlreadyIssued(
                        country = countryCode,
                        numeroDocumentoFiscal = alreadyIssued.numeroDocumentoFiscal,
                        numeroControl = alreadyIssued.numeroControl,
                    ),
                )
            }
            is AlreadyIssuedResult.Partial -> {
                // OR semántico: aun con un sólo campo presente NO se debe reemitir.
                log.warn(
                    "[VE-FE] factura {} con datos fiscales parciales numeroDocumentoFiscal={} " +
                        "numero_control_thka={}. " +
                        "Reemisión Bloqueada: requiere reconciliación manual.",
                    invoiceId,
                    alreadyIssued.numeroDocumentoFiscal,
                    alreadyIssued.numeroControl,
                )
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure(
                        codigo = "PARTIAL_FISCAL_DATA",
                        mensaje =
                            "Factura $invoiceId ya posee un campo fiscal parcial " +
                                "(numeroDocumentoFiscal=${alreadyIssued.numeroDocumentoFiscal}, " +
                                "numero_control_thka=${alreadyIssued.numeroControl}). " +
                                "Reemisión bloqueada para evitar duplicados: reconciliar manualmente.",
                    ),
                )
            }
            AlreadyIssuedResult.None -> Unit // Continuar con el flujo.
        }
    }

    /** Paso 4: FASE 1 solo soporta tipoDocumento == "01". */
    private fun checkTipoDocumentoStep(
        invoiceId: String,
        context: InvoiceVEContext,
    ) {
        val tipoDoc = context.factura.tipoDocumento.trim()
        if (tipoDoc != SUPPORTED_TIPO_DOCUMENTO) {
            log.info(
                "[VE-FE] tipoDocumento='{}' no soportado en FASE 1 (solo '01'). factura={}.",
                tipoDoc,
                invoiceId,
            )
            throw VeStepFailure(ElectronicInvoiceResult.UnsupportedDocumentType(countryCode, tipoDoc))
        }
    }

    /** Paso 5: autenticación con HKA. */
    private suspend fun authenticateStep(
        invoiceId: String,
        context: InvoiceVEContext,
    ): Result<PacAuthToken> =
        runCatching {
            try {
                hkaClient.authenticate(
                    PacCredentials(
                        usuario = context.config.tokenEmpresa,
                        clave = context.config.tokenPassword,
                        baseUrl = context.config.baseUrl,
                    ),
                )
            } catch (e: VenezuelaHkaClientException) {
                // Timeout o red en auth: incertidumbre total.
                log.error("[VE-FE] fallo de red/timeout en Autenticacion factura {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Uncertain(
                        country = countryCode,
                        codigo = "AUTH_NET_ERROR",
                        mensaje = (e.message ?: "Error de red en autenticación HKA VE"),
                    ),
                )
            }
        }.map { auth ->
            if (!auth.httpOk || auth.resultado?.token.isNullOrBlank()) {
                log.warn(
                    "[VE-FE] Autenticacion rechazada factura={} http={} codigo={} mensaje={}",
                    invoiceId,
                    auth.httpStatus,
                    auth.codigo,
                    auth.mensaje,
                )
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure(
                        codigo = if (auth.httpStatus == 401) "AUTH_REJECTED" else auth.codigo,
                        mensaje = "Autenticación rechazada por HKA VE: ${auth.mensaje}",
                    ),
                )
            }
            PacAuthToken(token = auth.resultado!!.token!!)
        }.also {
            log.info("[VE-FE] Autenticacion OK factura={} entorno={}", invoiceId, context.config.tipoEntornoVe)
        }

    /**
     * Paso 6: consulta UltimoDocumento para alinear el correlativo con el PAC.
     * Si el PAC respondió 200 con resultado válido se extrae el último número;
     * cualquier otro caso (404, codigo != 200) se interpreta como "sin remoto".
     */
    private suspend fun fetchLastDocumentStep(
        invoiceId: String,
        context: InvoiceVEContext,
        token: PacAuthToken,
        serie: String,
    ): Result<Int?> =
        runCatching {
            try {
                hkaClient.fetchLastDocument(
                    baseUrl = context.config.baseUrl,
                    token = token,
                    request =
                        VenezuelaHkaUltimoDocumentoRequest(
                            serie = serie,
                            tipoDocumento = SUPPORTED_TIPO_DOCUMENTO,
                        ),
                )
            } catch (e: VenezuelaHkaClientException) {
                log.error("[VE-FE] fallo de red/timeout en UltimoDocumento factura {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Uncertain(
                        country = countryCode,
                        codigo = "ULTIMODOC_NET_ERROR",
                        mensaje = (e.message ?: "Error de red consultando último documento HKA VE"),
                    ),
                )
            }
        }.map { ultimoDoc ->
            if (ultimoDoc.fullyOk) {
                ultimoDoc.resultado
                    ?.resultado
                    ?.ultimoNumero
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.toIntOrNull()
                    .also { log.info("[VE-FE] UltimoDocumento remoto={} factura={}", it ?: "null", invoiceId) }
            } else {
                log.warn(
                    "[VE-FE] UltimoDocumento no concluyente http={} codigo={} mensaje={} factura={}",
                    ultimoDoc.httpStatus,
                    ultimoDoc.codigo,
                    ultimoDoc.mensaje,
                    invoiceId,
                )
                null
            }
        }

    /**
     * Paso 11: evalúa la respuesta de Emision con separación estricta de capas.
     * Exito exacto: HTTP 2xx + codigo == "200" + resultado.numeroDocumento no vacío.
     * Si NO es éxito exacto: lanza [VeStepFailure] con Uncertain/Failure según
     * la naturaleza de la respuesta.
     */
    private suspend fun evaluateEmission(
        database: Database,
        invoiceId: String,
        payload: VenezuelaHkaDocumentoWrapper,
        emission: VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>,
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

        log.warn(
            "[VE-FE] Emision NO exitosa factura={} http={} codigo={} mensaje={} validaciones={} resultado={}",
            invoiceId,
            emission.httpStatus,
            emission.codigo,
            emission.mensaje,
            emission.validaciones,
            emission.resultado,
        )
        // Incertidumbre: si HTTP fue 5xx o la respuesta es ilegible.
        val esIncierto =
            emission.httpStatus >= HTTP_SERVER_ERROR_MIN ||
                emission.businessOk &&
                emission.resultado
                    ?.resultado
                    ?.numeroDocumento
                    .isNullOrBlank()
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

    /**
     * Paso 12: persistencia atómica de los tres campos fiscales. Best-effort
     * tras aceptación del PAC: el documento ya fue creado; un fallo aquí NO se
     * reintenta ciegamente (duplicaría el número) y queda para conciliación.
     */
    private suspend fun persistEmissionResult(
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
            repository.updateInvoiceWithVEResult(
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
            log.error(
                "[VE-FE] Emision OK pero fallo al persistir factura={} numDoc={} numCtrl={}",
                invoiceId,
                numDoc,
                numCtrl,
                e,
            )
        }
        log.info(
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
    private suspend fun buildSuccessFromPersisted(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult {
        val persisted = repository.loadFiscalDataForResponse(database, invoiceId)
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

    /** Falla interna de un paso del flujo VE; envuelve el resultado final. */
    private class VeStepFailure(val result: ElectronicInvoiceResult) : RuntimeException()

    /** Desenvuelve la falla de un paso; si no es esperada, la re-lanza. */
    private fun stepFailure(e: Throwable): ElectronicInvoiceResult =
        (e as? VeStepFailure)?.result ?: throw e

    // Timeout/incertidumbre: NO persistir, NO marcar exitoso, NO reintentar.
    private fun logEmisionUncertain(
        invoiceId: String,
        numeroFinal: String,
        e: VenezuelaHkaClientException,
    ) {
        log.error(
            "[VE-FE] TIMEOUT/RED en Emision factura={} numeroFinal={}. NO se persiste nada.",
            invoiceId,
            numeroFinal,
            e,
        )
    }

    companion object {
        /** Único tipo de documento soportado en FASE 1. */
        const val SUPPORTED_TIPO_DOCUMENTO = "01"
    }
}
