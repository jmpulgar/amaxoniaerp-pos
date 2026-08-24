package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
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

            // 7-8. Reserva atómica del correlativo LOCAL + número final efectivo
            //      (FASE 1.1 - Brief item 3: reserveAtLeast respeta
            //      max(local, remoto+1) dentro de una transacción autocontenida).
            val reservado = reserveCorrelativoStep(database, invoiceId, (ultimoRemoto ?: 0) + 1)
            val numeroFinal = reservado.numeroFormateado()
            log.info(
                "[VE-FE] numero a enviar: minimumNextNumber={} reservado={} final={} factura={}",
                (ultimoRemoto ?: 0) + 1,
                reservado.numero,
                numeroFinal,
                invoiceId,
            )

            // 9-10. Construcción del payload y envío a Emision.
            val payload = buildPayloadStep(invoiceId, context, serie, numeroFinal)
            val emission = emitStep(invoiceId, context, token, payload, numeroFinal)

            // 11-12. Evaluar respuesta y, si es éxito exacto, persistir.
            runCatching {
                repository.evaluateEmission(database, invoiceId, payload, emission, countryCode)
            }.getOrElse { e ->
                return@run stepFailure(e)
            }

            // 13. Success construido EXCLUSIVAMENTE con lo persistido en BD.
            repository.buildSuccessFromPersisted(database, invoiceId)
        }

    /**
     * Paso 7: reserva atómica del correlativo LOCAL (FASE 1.1 — Brief item 3:
     * reserveAtLeast). El mínimo se calcula a partir del remoto del PAC y se
     * pasa al repositorio; ya NO se hace max() en la Strategy ni en el
     * Builder. La transacción SQL se abre, reserva y commit en un solo bloque
     * autocontenido - nunca se mantiene abierta durante HTTP.
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

    /** Paso 9: construcción del payload. */
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

    /** Paso 10: envío a Emision; timeout/red → Uncertain. */
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
                // Sin config FE VE → no aplica HKA. No lanzar excepción al caller.
                log.warn("[VE-FE] configuración FE VE incompleta para factura {}: {}", invoiceId, e.message)
                throw VeStepFailure(ElectronicInvoiceResult.NotApplicable(countryCode), e)
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
        /** Único tipo de documento soportado en FASE 1. */
        const val SUPPORTED_TIPO_DOCUMENTO = "01"
    }
}
