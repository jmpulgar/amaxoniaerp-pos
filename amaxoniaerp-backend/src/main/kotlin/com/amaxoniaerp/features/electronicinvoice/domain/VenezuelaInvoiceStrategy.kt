package com.amaxoniaerp.features.electronicinvoice.domain

import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaClientException
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaDocumentoWrapper
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaEmisionResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaUltimoDocumentoRequest
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

/**
 * Estrategia concreta [ElectronicInvoiceStrategy] para Venezuela con The Factory
 * HKA FacturaciÃ³n ElectrÃ³nica (FASE 1 - solo facturas tipoDocumento "01").
 *
 * Flujo (post transacciÃ³n comercial; el `_UseCase_` ya hizo COMMIT):
 *
 *   1. Cargar contexto (lectura). Si la configuraciÃ³n FE VE falta o es invÃ¡lida
 *      se retorna [ElectronicInvoiceResult.NotApplicable] sin propagar error.
 *   2. Recargar la factura (idempotencia): si ya tiene nÃºmero fiscal o control
 *      persistidos, retornar [ElectronicInvoiceResult.AlreadyIssued] sin
 *      llamar a HKA.
 *   3. Verificar `tipoDocumento == "01"`. Otro tipo â†’
 *      [ElectronicInvoiceResult.UnsupportedDocumentType].
 *   4. Autenticarse con HKA. Si falla credenciales â†’ Failure(AUTH_REJECTED).
 *   5. Consultar UltimoDocumento(serie, "01") para conocer el remoto.
 *   6. Reservar correlativo local ATÃ“MICAMENTE (transacciÃ³n breve y cerrada).
 *   7. Construir el payload con nÃºmero = max(local reservado, remoto+1).
 *   8. Enviar al PAC Emision_Procesar.
 *   9. Evaluar respuesta (HTTP, cÃ³digo, resultado, validaciones).
 *  10. En Ã©xito EXACTO (codigo==200 + resultado.numeroDocumento no vacÃ­o):
 *      persistir `numeroDocumentoFiscal` + `numero_control_thka` (atÃ³mico).
 *  11. En timeout/red/incertidumbre: NO persistir, retornar Uncertain.
 *      NO reintentar.
 *
 * SELECCIÃ“N HKA20 vs DIGITAL (FASE 1.1)
 * --------------------------------------
 * La decisiÃ³n entre la impresora fiscal HKA20 fÃ­sica (POS) y la facturaciÃ³n
 * digital Venezuela (PAC) NO se deduce aquÃ­ de `parametros_generales.tipo_facturacion`.
 * La fuente de verdad es el flag explÃ­cito del frontend (`ProcessSaleRequest.useHka20`),
 * evaluado en `ProcessSaleUseCase.execute(...)` ANTES de invocar la strategy. Si el
 * uso del HKA20 es `true`, esta strategy **no se llama**: el backend sÃ³lo persiste
 * la venta comercial y el POS continÃºa con su flujo HKA20 existente. Si el flujo
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
    ): ElectronicInvoiceResult =
        run {
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

            // 5. AutenticaciÃ³n con HKA.
            val token =
                authenticateStep(invoiceId, context).getOrElse { e ->
                    return@run stepFailure(e)
                }

            // 6. Ãšltimo documento remoto para alinear el correlativo con el PAC.
            val serie = context.caja.serieSucursal?.ifBlank { null } ?: defaultSerie
            val ultimoRemoto =
                fetchLastDocumentStep(invoiceId, context, token, serie).getOrElse { e ->
                    return@run stepFailure(e)
                }

            // 7-8. Reserva atÃ³mica del correlativo LOCAL + nÃºmero final efectivo
            //      (FASE 1.1 â€” Brief item 3: reserveAtLeast respeta
            //      max(local, remoto+1) dentro de una transacciÃ³n autocontenida).
            val reservado = reserveCorrelativoStep(database, invoiceId, (ultimoRemoto ?: 0) + 1)
            val numeroFinal = reservado.numeroFormateado()
            log.info(
                "[VE-FE] numero a enviar: minimumNextNumber={} reservado={} final={} factura={}",
                (ultimoRemoto ?: 0) + 1,
                reservado.numero,
                numeroFinal,
                invoiceId,
            )

            // 9-10. ConstrucciÃ³n del payload y envÃ­o a Emision.
            val payload = buildPayloadStep(invoiceId, context, serie, numeroFinal)
            val emission = emitStep(invoiceId, context, token, payload, numeroFinal)

            // 11-12. Evaluar respuesta y, si es Ã©xito exacto, persistir.
            runCatching {
                repository.evaluateEmission(database, invoiceId, payload, emission, countryCode)
            }.getOrElse { e ->
                return@run stepFailure(e)
            }

            // 13. Success construido EXCLUSIVAMENTE con lo persistido en BD.
            repository.buildSuccessFromPersisted(database, invoiceId)
        }

    /**
     * Paso 7: reserva atÃ³mica del correlativo LOCAL (FASE 1.1 â€” Brief item 3:
     * reserveAtLeast). El mÃ­nimo se calcula a partir del remoto del PAC y se
     * pasa al repositorio; ya NO se hace max() en la Strategy ni en el
     * Builder. La transacciÃ³n SQL se abre, reserva y commit en un solo bloque
     * autocontenido â€” nunca se mantiene abierta durante HTTP.
     */
    private suspend fun reserveCorrelativoStep(
        database: Database,
        invoiceId: String,
        minimumNextNumber: Int,
    ): VECorrelativoReservado =
        runCatching {
            repository.reserveAtLeast(database, minimumNextNumber = minimumNextNumber)
        }.getOrElse { e ->
            if (e is Error) throw e
            if (e is FEConfigurationException) {
                log.error("[VE-FE] no se pudo reservar correlativo factura {}", invoiceId, e)
                throw VeStepFailure(
                    ElectronicInvoiceResult.Failure(
                        "CORRELATIVO_CONFIG",
                        e.message ?: "ConfiguraciÃ³n correlativo invÃ¡lida",
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

    /** Paso 9: construcciÃ³n del payload. */
    private fun buildPayloadStep(
        invoiceId: String,
        context: InvoiceVEContext,
        serie: String,
        numeroFinal: String,
    ): VenezuelaHkaDocumentoWrapper =
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

    /** Paso 10: envÃ­o a Emision; timeout/red â†’ Uncertain. */
    private suspend fun emitStep(
        invoiceId: String,
        context: InvoiceVEContext,
        token: PacAuthToken,
        payload: VenezuelaHkaDocumentoWrapper,
        numeroFinal: String,
    ): VenezuelaHkaResponse<VenezuelaHkaEmisionResponse> =
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
                    codigo =
                        if (e is VenezuelaHkaClientException.Timeout) {
                            "EMISION_TIMEOUT"
                        } else {
                            "EMISION_NET_ERROR"
                        },
                    mensaje = (e.message ?: "Respuesta incierta del PAC VE"),
                    transaccionId = payload.documento.datosTransaccion.transaccionId,
                ),
            )
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
                    e,
                )
            } catch (e: FEConfigurationException) {
                // Sin config FE VE â†’ no aplica HKA. No lanzar excepciÃ³n al caller.
                log.warn("[VE-FE] configuraciÃ³n FE VE incompleta para factura {}: {}", invoiceId, e.message)
                throw VeStepFailure(ElectronicInvoiceResult.NotApplicable(countryCode), e)
            }
        }

    /**
     * Paso 3: idempotencia con semÃ¡ntica OR (FASE 1.1 â€” Brief item 1).
     * Cualquiera de los dos campos fiscales presente implica "ya procesada":
     * NO se debe llamar al PAC. La condiciÃ³n correcta es OR, no AND.
     *   - Complete â†’ AlreadyIssued (Ã©xito idempotente).
     *   - Partial  â†’ Failure(PARTIAL_FISCAL_DATA): no se puede reemitir
     *                a ciegas porque generarÃ­a duplicado; requiere
     *                reconciliaciÃ³n manual.
     *   - None     â†’ continuar con el flujo de emisiÃ³n.
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
                // OR semÃ¡ntico: aun con un sÃ³lo campo presente NO se debe reemitir.
                log.warn(
                    "[VE-FE] factura {} con datos fiscales parciales numeroDocumentoFiscal={} " +
                        "numero_control_thka={}. " +
                        "ReemisiÃ³n Bloqueada: requiere reconciliaciÃ³n manual.",
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
                                "ReemisiÃ³n bloqueada para evitar duplicados: reconciliar manualmente.",
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

    /** Paso 5: autenticaciÃ³n con HKA. */
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
                        mensaje = (e.message ?: "Error de red en autenticaciÃ³n HKA VE"),
                    ),
                    e,
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
                        mensaje = "AutenticaciÃ³n rechazada por HKA VE: ${auth.mensaje}",
                    ),
                )
            }
            PacAuthToken(token = auth.resultado!!.token!!)
        }.also {
            log.info("[VE-FE] Autenticacion OK factura={} entorno={}", invoiceId, context.config.tipoEntornoVe)
        }

    /**
     * Paso 6: consulta UltimoDocumento para alinear el correlativo con el PAC.
     * Si el PAC respondiÃ³ 200 con resultado vÃ¡lido se extrae el Ãºltimo nÃºmero;
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
                        mensaje = (e.message ?: "Error de red consultando Ãºltimo documento HKA VE"),
                    ),
                    e,
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

    companion object {
        /** Ãšnico tipo de documento soportado en FASE 1. */
        const val SUPPORTED_TIPO_DOCUMENTO = "01"
    }
}
