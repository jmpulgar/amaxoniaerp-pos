package com.amaxoniaerp.features.electronicinvoice.application

import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.domain.*
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

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
        val context = try {
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
        if (tipoFact < 3) {
            logger.info("[FE] tipo_facturacion=$tipoFact no requiere FE electrónica. Retornando NotApplicable.")
            return ElectronicInvoiceResult.NotApplicable(countryCode)
        }

        // ── 2. Autenticarse con el PAC ───────────────────────────────────────
        logger.info("[FE] Autenticando con PAC: baseUrl=${context.config.direccionEnvio} usuario=${context.config.tokenEmpresa.take(8)}...")
        val credentials = PacCredentials(
            usuario = context.config.tokenEmpresa,
            clave = context.config.tokenPassword,
            baseUrl = context.config.direccionEnvio,
        )

        val token = pacClient.authenticate(credentials).getOrElse { e ->
            logger.error("Error autenticando con PAC para factura {}", invoiceId, e)
            return ElectronicInvoiceResult.Failure(
                "AUTH_ERROR",
                "Error de autenticación con el PAC: ${e.message}",
            )
        }

        // ── 3. Construir el payload ──────────────────────────────────────────
        logger.info("[FE] Token PAC obtenido OK (longitud=${token.token.length}). Construyendo payload para factura $invoiceId...")
        val payload = try {
            payloadBuilder.build(context)
        } catch (e: Exception) {
            logger.error("Error construyendo payload FE para factura {}", invoiceId, e)
            return ElectronicInvoiceResult.Failure(
                "BUILD_ERROR",
                "Error construyendo documento electrónico: ${e.message}",
            )
        }

        // ── 4. Enviar al PAC ─────────────────────────────────────────────────
        logger.info("[FE] Enviando documento al PAC: sucursal=${context.codigoSucursalEmisor} punto=${context.puntoFacturacionFiscal} numDocFiscal=${context.factura.numeroDocumentoFiscal} items=${context.detalles.size} formasPago=${context.formasPago.size}")
        val pacResponse = pacClient.sendDocument(
            baseUrl = context.config.direccionEnvio,
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
        logger.info("[FE] Respuesta PAC: exitoso=${pacResponse.exitoso} codigo=${pacResponse.codigo} mensaje=${pacResponse.mensaje} cufe=${pacResponse.cufe?.take(20)}")
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
}
