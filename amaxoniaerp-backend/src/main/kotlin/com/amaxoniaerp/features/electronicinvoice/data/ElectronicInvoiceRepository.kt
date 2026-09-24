package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PanamaCreditNotePayloadContext
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.get
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private const val LOG_TOKEN_PREFIX_LENGTH = 8

/**
 * Repositorio de solo-lectura + actualización post-envío para Facturación Electrónica.
 *
 * Lee datos de la DB existente para construir el contexto de FE,
 * y escribe los resultados del PAC (CUFE, QR, fecha DGI) de vuelta.
 * Las lecturas de contexto viven en ElectronicInvoiceContextReaders.kt y las
 * de nota de crédito en PanamaCreditNoteLoader.kt.
 *
 * NO modifica la lógica de escritura de facturas existente (eso lo hace ProcessSaleTransactionalRepository).
 *
 * Abierta (clase y métodos usados por el orquestador) para permitir dobles de
 * prueba del workflow FE sin base de datos; misma semántica, sin wrappers.
 */
open class ElectronicInvoiceRepository {
    private val logger = LoggerFactory.getLogger(ElectronicInvoiceRepository::class.java)

    /**
     * Carga todos los datos necesarios para construir el payload de FE
     * a partir de una factura existente en la DB.
     */
    open suspend fun loadInvoiceContext(
        database: Database,
        invoiceId: String,
    ): InvoiceFEContext =
        dbQuery(database) {
            // 1. Leer cabecera de factura con JOIN a clientes, tipo_cliente y paises
            val header = loadFacturaHeaderRow(invoiceId)
            val facturaRow = header.row
            val cajaId = facturaRow[FEFacturaReadTable.idCaja]
            val idSucursal = facturaRow[FEFacturaReadTable.idSucursal]

            // 2. Leer configuración PAC desde parametros_generales
            val configRow =
                FEParametrosReadTable
                    .selectAll()
                    .orderBy(FEParametrosReadTable.codEmpresa)
                    .limit(1)
                    .firstOrNull()
                    ?: throw FEConfigurationException("No se encontró parametros_generales para FE")

            val config = mapConfig(configRow)

            logger.info(
                "[FE] config loaded: tokenEmpresa=${config.tokenEmpresa.take(
                    LOG_TOKEN_PREFIX_LENGTH,
                )}... api_thefactoryhka=${config.apiTheFactoryHka} tipoEmision=${config.tipoEmision}",
            )

            // 3. Resolver código sucursal emisor y punto facturación fiscal
            val (codigoSucursal, puntoFacturacion) =
                resolveCodigoSucursalYPuntoFacturacion(
                    cajaId = cajaId,
                    idSucursal = idSucursal,
                    codigoSucursalFallback = config.codigoSucursalEmisorFallback,
                    puntoFacturacionFallback = config.puntoFacturacionFiscalFallback,
                )

            logger.info(
                "[FE] cajaId=$cajaId idSucursal=$idSucursal -> codigoSucursalEmisor=$codigoSucursal " +
                    "puntoFacturacionFiscal=$puntoFacturacion",
            )

            // 4. Número de documento fiscal: reutilizar el persistido en la
            // factura (reintentos); sólo consumir correlativos si nunca se
            // asignó (Q1, anti-1513).
            val numeroDocFiscal =
                resolverNumeroDocumentoFiscal(
                    persistido = facturaRow[FEFacturaReadTable.numeroDocumentoFiscal],
                    siguienteCorrelativo = ::resolveNumeroDocumentoFiscal,
                )
            logger.info("[FE] numeroDocumentoFiscal=$numeroDocFiscal")

            if (facturaRow[FEFacturaReadTable.numeroDocumentoFiscal].isNullOrBlank()) {
                FacturasTablePA.update({ FacturasTablePA.idFactura eq invoiceId }) {
                    it[numeroDocumentoFiscal] = numeroDocFiscal
                }
            }

            // 5. Mapear factura y cliente (JOIN con paises)
            val factura = mapFactura(facturaRow, numeroDocFiscal)
            val cliente = mapCliente(facturaRow, header.paisLocal, header.paisExtranjero)
            logger.info(
                "[FE] cliente: tipoClienteFE=${cliente.tipoClienteFE} identificacion=${cliente.identificacion} " +
                    "nombre=${cliente.nombre} pais=${cliente.paisIso}",
            )

            // 6. Leer detalle de factura con JOIN a unidad de medida
            val detalles = loadDetallesFE(invoiceId)
            logger.info("[FE] detalles cargados: ${detalles.size} items")

            // 7. Leer formas de pago
            val formasPago = loadFormasPago(invoiceId)
            logger.info(
                "[FE] formasPago cargadas: ${formasPago.size} -> ${formasPago.map {
                    "${it.descripcion}(${it.formaPagoFact ?: "?"})=${it.monto}"
                }}",
            )

            // 8. Leer retención y totales de pago
            val (retencion, montoCancelar, vuelto) = loadPaymentTotals(invoiceId)

            InvoiceFEContext(
                config = config,
                factura = factura,
                cliente = cliente,
                detalles = detalles,
                formasPago = formasPago,
                retencion = retencion,
                montoCancelar = montoCancelar,
                codigoSucursalEmisor = codigoSucursal,
                puntoFacturacionFiscal = puntoFacturacion,
                vuelto = vuelto,
            )
        }

    private fun loadPaymentTotals(invoiceId: String): Triple<FERetencionData?, Double?, Double?> {
        val retencion = loadRetencion(invoiceId)
        logger.info(
            "[FE] retencion=${retencion?.codigoRetencion ?: "none"} monto=${retencion?.montoRetencion ?: 0.0}",
        )

        val montoCancelar = loadMontoCancelar(invoiceId)
        logger.info("[FE] montoCancelar=$montoCancelar")

        val vuelto = loadVuelto(invoiceId)
        logger.info("[FE] vuelto=$vuelto")
        return Triple(retencion, montoCancelar, vuelto)
    }

    /**
     * Carga el contexto inmutable de una NC PA ya preparada.
     *
     * El correlativo fiscal de la NC debe llegar resuelto por el llamador. La
     * consulta sólo reutiliza el contexto FE de la factura original y sustituye
     * cabecera/líneas por los valores calculados de la devolución.
     */
    suspend fun loadCreditNoteContext(
        database: Database,
        creditNoteId: String,
        numeroDocumentoFiscal: String,
    ): PanamaCreditNotePayloadContext {
        val normalizedDocumentNumber = requireNumeroDocumentoFiscal(numeroDocumentoFiscal)
        val sourceInvoiceId = loadSourceInvoiceId(database, creditNoteId)
        val invoiceContext = loadInvoiceContext(database, sourceInvoiceId)
        val originalFiscal = loadOriginalFiscal(database, sourceInvoiceId)
        requireOriginalFiscalData(originalFiscal, sourceInvoiceId)

        val creditNoteData = loadCreditNoteData(database, creditNoteId)

        return buildPanamaCreditNoteContext(
            invoiceContext = invoiceContext,
            creditNoteData = creditNoteData,
            creditNoteId = creditNoteId,
            normalizedDocumentNumber = normalizedDocumentNumber,
            originalFiscal = originalFiscal,
        )
    }

    /**
     * Actualiza la factura con los datos retornados por el PAC tras un envío exitoso.
     */
    open suspend fun updateInvoiceWithFEResponse(
        database: Database,
        update: FeResponseUpdate,
    ) = dbQuery(database) {
        logger.info("Actualizando factura {} con CUFE={}", update.invoiceId, update.cufe)

        FacturasTablePA.update({ FacturasTablePA.idFactura eq update.invoiceId }) {
            it[FacturasTablePA.numeroDocumentoFiscal] = update.numeroDocumentoFiscal
            it[FacturasTablePA.puntoFacturacionFiscal] = update.puntoFacturacionFiscal
            it[FacturasTablePA.cufe] = update.cufe
            if (update.fechaRecepcionDGI != null) {
                it[FacturasTablePA.fechaRecepcionDGI] = formatFechaRecepcion(update.fechaRecepcionDGI)
            }
            if (update.qr != null) {
                it[FacturasTablePA.qr] = update.qr
            }
            if (update.nroProtocolo != null) {
                it[FacturasTablePA.nroProtocoloAutorizacion] = update.nroProtocolo
            }
            if (update.fechaLimite != null) {
                it[FacturasTablePA.fechaLimite] = formatFechaRecepcion(update.fechaLimite)
            }
        }
    }

    /**
     * Incrementa el correlativo del número de documento fiscal en la tabla `correlativos`.
     */
    open suspend fun incrementNumeroDocumentoFiscal(database: Database) =
        dbQuery(database) {
            val state = ensureFECorrelativoExists()
            val maxFactura = resolveMaxExistingNumeroDocumentoFiscal()
            val targetNext = maxOf(state.contador, maxFactura) + 1L

            val updated =
                FECorrelativosTable.update({
                    FECorrelativosTable.campo eq "numeroDocumentoFiscal"
                }) {
                    it[contador] = targetNext.toInt()
                }

            if (updated == 0) {
                logger.warn("No se encontró registro de correlativos para 'numeroDocumentoFiscal', insertando...")
                val nextId = (FECorrelativosTable.selectAll().map { it[FECorrelativosTable.id] }.maxOrNull() ?: 0) + 1
                FECorrelativosTable.insert {
                    it[id] = nextId
                    it[campo] = "numeroDocumentoFiscal"
                    it[contador] = targetNext.toInt()
                }
            }
        }

    open suspend fun getInvoiceCufe(
        database: Database,
        invoiceId: String,
    ): String? =
        dbQuery(database) {
            FacturasTablePA
                .select(FacturasTablePA.cufe)
                .where { FacturasTablePA.idFactura eq invoiceId }
                .limit(1)
                .firstOrNull()
                ?.get(FacturasTablePA.cufe)
                ?.takeIf { it.isNotBlank() }
        }

    open suspend fun getInvoiceNumeroDocumentoFiscal(
        database: Database,
        invoiceId: String,
    ): String? =
        dbQuery(database) {
            FacturasTablePA
                .select(FacturasTablePA.numeroDocumentoFiscal)
                .where { FacturasTablePA.idFactura eq invoiceId }
                .limit(1)
                .firstOrNull()
                ?.get(FacturasTablePA.numeroDocumentoFiscal)
                ?.takeIf { it.isNotBlank() }
        }
}

/** Campos de la factura PA que se persisten tras una respuesta exitosa del PAC. */
data class FeResponseUpdate(
    val invoiceId: String,
    val numeroDocumentoFiscal: String,
    val puntoFacturacionFiscal: String,
    val cufe: String,
    val qr: String?,
    val fechaRecepcionDGI: String?,
    val nroProtocolo: String?,
    val fechaLimite: String?,
)
