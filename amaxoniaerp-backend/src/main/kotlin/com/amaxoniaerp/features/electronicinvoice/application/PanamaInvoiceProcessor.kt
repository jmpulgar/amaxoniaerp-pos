package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryEnviarCorreoResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private const val PAC_FISCAL_TYPE_THRESHOLD = 3
private const val LOG_CREDENTIAL_PREFIX_LENGTH = 8
private const val CUFE_LOG_PREFIX_LENGTH = 20
private const val ITEM_DESCRIPTION_LOG_LENGTH = 80

/**
 * Orquestador del flujo de Facturación Electrónica para Panamá.
 *
 * Implementa [ElectronicInvoiceStrategy] y coordina:
 * 1. Extracción de datos de la DB (vía Repository)
 * 2. Autenticación con el PAC (vía PanamaElectronicInvoiceClient)
 * 3. Construcción del payload (vía PayloadBuilder)
 * 4. Envío al PAC
 * 5. Actualización de la DB con CUFE/QR si es exitoso
 * 6. Incremento del correlativo fiscal
 *
 * Cada dependencia se inyecta por constructor, facilitando testing y
 * permitiendo cambiar el PAC sin modificar este orquestador.
 */
class PanamaInvoiceProcessor(
    private val repository: ElectronicInvoiceRepository,
    private val pacClient: PanamaElectronicInvoiceClient,
    private val payloadBuilder: TheFactoryHkaPayloadBuilder,
) : ElectronicInvoiceStrategy {
    override val countryCode: String = "PA"

    private val logger = LoggerFactory.getLogger(PanamaInvoiceProcessor::class.java)

    override suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult {
        // ── 1. Obtener datos de la DB ────────────────────────────────────────
        val context =
            try {
                repository.loadInvoiceContext(database, invoiceId)
            } catch (e: FEInvoiceNotFoundException) {
                logger.error("Factura no encontrada para FE: {}", invoiceId, e)
                return ElectronicInvoiceResult.Failure("INVOICE_NOT_FOUND", e.message ?: "Factura no encontrada")
            } catch (e: FEConfigurationException) {
                logger.error("Configuración FE incompleta para factura: {}", invoiceId, e)
                return ElectronicInvoiceResult.Failure("CONFIG_ERROR", e.message ?: "Configuración FE incompleta")
            }

        // ── 1b. Verificar tipo_facturacion ───────────────────────────────────
        // tipo_facturacion: 0=PDF, 1=FISCAL, 2=FORMA LIBRE, 3=The Factory HKA (FE)
        val tipoFact = context.config.tipoFacturacion
        logger.info("[FE] tipo_facturacion=$tipoFact para factura $invoiceId")
        if (tipoFact < PAC_FISCAL_TYPE_THRESHOLD) {
            logger.info("[FE] tipo_facturacion=$tipoFact no requiere FE electrónica. Retornando NotApplicable.")
            return ElectronicInvoiceResult.NotApplicable(countryCode)
        }

        // ── 2. Autenticarse con el PAC ───────────────────────────────────────
        logger.info(
            "[FE] Autenticando con PAC: baseUrl=${context.config.apiTheFactoryHka} usuario=${context.config.tokenEmpresa.take(
                LOG_CREDENTIAL_PREFIX_LENGTH,
            )}...",
        )
        val credentials =
            PacCredentials(
                usuario = context.config.tokenEmpresa,
                clave = context.config.tokenPassword,
                baseUrl = context.config.apiTheFactoryHka,
            )

        val token =
            pacClient.authenticate(credentials).getOrElse { e ->
                logger.error("Error autenticando con PAC para factura {}", invoiceId, e)
                return ElectronicInvoiceResult.Failure(
                    "AUTH_ERROR",
                    "Error de autenticación con el PAC: ${e.message}",
                )
            }

        // ── 3. Construir el payload ──────────────────────────────────────────
        logger.info("[FE] Token PAC obtenido OK (longitud=${token.token.length}). Construyendo payload para factura $invoiceId...")
        val payload =
            try {
                payloadBuilder.build(context)
            } catch (e: Exception) {
                logger.error("Error construyendo payload FE para factura {}", invoiceId, e)
                return ElectronicInvoiceResult.Failure(
                    "BUILD_ERROR",
                    "Error construyendo documento electrónico: ${e.message}",
                )
            }
        logPayloadDiagnostics(invoiceId, context, payload)

        // ── 4. Enviar al PAC ─────────────────────────────────────────────────
        logger.info(
            "[FE] Enviando documento al PAC: sucursal=${context.codigoSucursalEmisor} punto=${context.puntoFacturacionFiscal} numDocFiscal=${context.factura.numeroDocumentoFiscal} items=${context.detalles.size} formasPago=${context.formasPago.size}",
        )
        val pacResponse =
            pacClient
                .sendDocument(
                    baseUrl = context.config.apiTheFactoryHka,
                    token = token,
                    payload = payload,
                ).getOrElse { e ->
                    logger.error("Error enviando documento al PAC para factura {}", invoiceId, e)
                    return ElectronicInvoiceResult.Failure(
                        "SEND_ERROR",
                        "Error de comunicación con el PAC: ${e.message}",
                    )
                }

        // ── 5. Evaluar respuesta ─────────────────────────────────────────────
        logger.info(
            "[FE] Respuesta PAC: exitoso=${pacResponse.exitoso} " +
                "codigo=${pacResponse.codigo} mensaje=${pacResponse.mensaje} " +
                "cufe=${pacResponse.cufe?.take(CUFE_LOG_PREFIX_LENGTH)}",
        )
        if (!pacResponse.exitoso || pacResponse.cufe.isNullOrBlank()) {
            logger.warn(
                "PAC rechazó factura {}: [{}] {}",
                invoiceId,
                pacResponse.codigo,
                pacResponse.mensaje,
            )
            return ElectronicInvoiceResult.Failure(
                pacResponse.codigo,
                pacResponse.mensaje,
            )
        }

        // ── 6. Actualizar DB con CUFE, QR, fecha DGI ────────────────────────
        try {
            repository.updateInvoiceWithFEResponse(
                database = database,
                invoiceId = invoiceId,
                numeroDocumentoFiscal = context.factura.numeroDocumentoFiscal,
                puntoFacturacionFiscal = context.puntoFacturacionFiscal,
                cufe = pacResponse.cufe,
                qr = pacResponse.qr,
                fechaRecepcionDGI = pacResponse.fechaRecepcionDGI,
                nroProtocolo = pacResponse.nroProtocoloAutorizacion,
                fechaLimite = pacResponse.fechaLimite,
            )
        } catch (e: Exception) {
            // El documento ya fue aceptado por la DGI, pero fallo al guardar.
            // Loggear como ERROR critico pero retornar Success con advertencia.
            logger.error(
                "CUFE={} aceptado por DGI pero error al guardar en DB para factura {}",
                pacResponse.cufe,
                invoiceId,
                e,
            )
        }

        // ── 7. Incrementar correlativo fiscal ────────────────────────────────
        try {
            repository.incrementNumeroDocumentoFiscal(database)
        } catch (e: Exception) {
            logger.error(
                "Error incrementando correlativo fiscal tras FE exitosa para factura {}",
                invoiceId,
                e,
            )
        }

        sendInvoiceEmailIfPossible(
            context = context,
            token = token,
            cufe = pacResponse.cufe,
        ).onFailure { e ->
            logger.warn(
                "FE exitosa para factura {}, pero no se pudo enviar el correo: {}",
                invoiceId,
                e.message,
            )
        }

        logger.info(
            "FE exitosa para factura {}. CUFE={}",
            invoiceId,
            pacResponse.cufe,
        )

        return ElectronicInvoiceResult.Success(
            cufe = pacResponse.cufe,
            qr = pacResponse.qr,
            fechaRecepcionDGI = pacResponse.fechaRecepcionDGI,
            nroProtocoloAutorizacion = pacResponse.nroProtocoloAutorizacion,
            fechaLimite = pacResponse.fechaLimite,
        )
    }

    suspend fun resendInvoiceEmail(
        database: Database,
        invoiceId: String,
    ): Result<TheFactoryEnviarCorreoResponse> =
        runCatching {
            val context = repository.loadInvoiceContext(database, invoiceId)
            if (context.config.tipoFacturacion < PAC_FISCAL_TYPE_THRESHOLD) {
                throw FEConfigurationException("La factura no usa FEL The Factory HKA")
            }

            val cufe =
                repository.getInvoiceCufe(database, invoiceId)
                    ?: throw FEConfigurationException("La factura no tiene CUFE generado")

            val credentials =
                PacCredentials(
                    usuario = context.config.tokenEmpresa,
                    clave = context.config.tokenPassword,
                    baseUrl = context.config.apiTheFactoryHka,
                )
            val token = pacClient.authenticate(credentials).getOrThrow()

            sendInvoiceEmailIfPossible(context, token, cufe).getOrThrow()
        }

    private suspend fun sendInvoiceEmailIfPossible(
        context: InvoiceFEContext,
        token: PacAuthToken,
        cufe: String,
    ): Result<TheFactoryEnviarCorreoResponse> {
        val email =
            context.cliente.correo
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return Result.failure(FEConfigurationException("El cliente no tiene correo configurado"))

        return pacClient.sendEmail(
            baseUrl = context.config.apiTheFactoryHka,
            token = token,
            cufe = cufe,
            emails = listOf(email),
        )
    }

    private fun logPayloadDiagnostics(
        invoiceId: String,
        context: InvoiceFEContext,
        payload: com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaDocumentoWrapper,
    ) {
        val totales = payload.documento.totalesSubTotales
        logger.info(
            "[FE][PAYLOAD] factura={} totalFactura={} totalValorRecibido={} vuelto={} totalPrecioNeto={} totalITBMS={} totalMontoGravado={} totalTodosItems={}",
            invoiceId,
            totales.totalFactura,
            totales.totalValorRecibido,
            totales.vuelto ?: "",
            totales.totalPrecioNeto,
            totales.totalITBMS,
            totales.totalMontoGravado ?: "",
            totales.totalTodosItems,
        )

        payload.documento.listaItems.forEachIndexed { index, item ->
            val raw = context.detalles.getOrNull(index)
            logger.info(
                "[FE][PAYLOAD][ITEM {}] desc='{}' codigo='{}' cantidad={} precioUnitario={} precioItem={} valorTotal={} tasaITBMS={} valorITBMS={} descuentoUnit={} CPBS={}/{} rawCantidad={} rawPrecioSinIva={} rawTotalSinIva={} rawTotalConIva={} rawDescuento={} rawPiva={}",
                index + 1,
                item.descripcion.take(ITEM_DESCRIPTION_LOG_LENGTH),
                item.codigo,
                item.cantidad,
                item.precioUnitario,
                item.precioItem,
                item.valorTotal,
                item.tasaITBMS,
                item.valorITBMS,
                item.precioUnitarioDescuento ?: "",
                item.codigoCPBS ?: "",
                item.codigoCPBSAbrev ?: "",
                raw?.cantidad ?: "",
                raw?.precioSinIva ?: "",
                raw?.totalSinIva ?: "",
                raw?.totalConIva ?: "",
                raw?.montoDescuento ?: "",
                raw?.piva ?: "",
            )
        }

        totales.listaFormaPago.forEachIndexed { index, formaPago ->
            logger.info(
                "[FE][PAYLOAD][PAGO {}] formaPagoFact={} desc='{}' valorCuotaPagada={}",
                index + 1,
                formaPago.formaPagoFact,
                formaPago.descFormaPago ?: "",
                formaPago.valorCuotaPagada,
            )
        }
    }
}
