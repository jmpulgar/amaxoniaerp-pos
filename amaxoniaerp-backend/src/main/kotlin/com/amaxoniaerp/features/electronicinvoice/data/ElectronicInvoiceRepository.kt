package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.creditnotes.data.CreditNoteDetailTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteHeaderTablePA
import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PanamaCreditNotePayloadContext
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.get
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.trim
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Repositorio de solo-lectura + actualización post-envío para Facturación Electrónica.
 *
 * Lee datos de la DB existente para construir el contexto de FE,
 * y escribe los resultados del PAC (CUFE, QR, fecha DGI) de vuelta.
 *
 * NO modifica la lógica de escritura de facturas existente (eso lo hace ProcessSaleTransactionalRepository).
 */
class ElectronicInvoiceRepository {
    private val logger = LoggerFactory.getLogger(ElectronicInvoiceRepository::class.java)

    /**
     * Carga todos los datos necesarios para construir el payload de FE
     * a partir de una factura existente en la DB.
     */
    suspend fun loadInvoiceContext(
        database: Database,
        invoiceId: String,
    ): InvoiceFEContext =
        dbQuery(database) {
            // 1. Leer cabecera de factura con JOIN a clientes, tipo_cliente y paises
            val paisLocal = FEPaisesReadTable.alias("pais_local")
            val paisExtranjero = FEPaisesReadTable.alias("pais_ext")

            val facturaRow =
                FEFacturaReadTable
                    .join(FECientesReadTable, JoinType.LEFT, FEFacturaReadTable.idCliente, FECientesReadTable.idCliente)
                    .join(FETipoClienteReadTable, JoinType.LEFT, FECientesReadTable.codTipoCliente, FETipoClienteReadTable.codTipoCliente)
                    .join(paisLocal, JoinType.LEFT, FECientesReadTable.pais, paisLocal[FEPaisesReadTable.id])
                    .join(paisExtranjero, JoinType.LEFT, FECientesReadTable.paisExtranjero, paisExtranjero[FEPaisesReadTable.id])
                    .selectAll()
                    .where { FEFacturaReadTable.idFactura eq invoiceId }
                    .limit(1)
                    .firstOrNull()
                    ?: throw FEInvoiceNotFoundException("Factura no encontrada: $invoiceId")

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
                    8,
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

            // 4. Leer número de documento fiscal desde tabla correlativos
            val numeroDocFiscal = resolveNumeroDocumentoFiscal()
            logger.info("[FE] numeroDocumentoFiscal=$numeroDocFiscal")

            // 5. Mapear factura
            val factura = mapFactura(facturaRow, numeroDocFiscal)

            // 6. Mapear cliente (JOIN con paises)
            val cliente = mapCliente(facturaRow, paisLocal, paisExtranjero)
            logger.info(
                "[FE] cliente: tipoClienteFE=${cliente.tipoClienteFE} identificacion=${cliente.identificacion} " +
                    "nombre=${cliente.nombre} pais=${cliente.paisIso}",
            )

            // 7. Leer detalle de factura con JOIN a unidad de medida
            val detalles = loadDetalles(invoiceId)
            logger.info("[FE] detalles cargados: ${detalles.size} items")

            // 8. Leer formas de pago
            val formasPago = loadFormasPago(invoiceId)
            logger.info(
                "[FE] formasPago cargadas: ${formasPago.size} -> ${formasPago.map {
                    "${it.descripcion}(${it.formaPagoFact ?: "?"})=${it.monto}"
                }}",
            )

            // 9. Leer retención y totales de pago
            val retencion = loadRetencion(invoiceId)
            logger.info(
                "[FE] " +
                    "retencion=${retencion?.codigoRetencion ?: "none"} monto=${retencion?.montoRetencion ?: 0.0}",
            )

            val montoCancelar = loadMontoCancelar(invoiceId)
            logger.info("[FE] montoCancelar=$montoCancelar")

            val vuelto = loadVuelto(invoiceId)
            logger.info("[FE] vuelto=$vuelto")

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
        val normalizedDocumentNumber = numeroDocumentoFiscal.trim()
        if (normalizedDocumentNumber.isBlank()) {
            throw FEConfigurationException("numeroDocumentoFiscal de NC no resuelto")
        }

        val sourceInvoiceId =
            dbQuery(database) {
                CreditNoteHeaderTablePA
                    .select(CreditNoteHeaderTablePA.codFactura)
                    .where { CreditNoteHeaderTablePA.idDevolucion eq creditNoteId }
                    .limit(1)
                    .firstOrNull()
                    ?.get(CreditNoteHeaderTablePA.codFactura)
            } ?: throw FEInvoiceNotFoundException("Nota de crédito no encontrada: $creditNoteId")

        val invoiceContext = loadInvoiceContext(database, sourceInvoiceId)
        val originalFiscal =
            dbQuery(database) {
                FacturasTablePA
                    .select(
                        FacturasTablePA.numeroDocumentoFiscal,
                        FacturasTablePA.fechaFactura,
                        FacturasTablePA.cufe,
                    ).where { FacturasTablePA.idFactura eq sourceInvoiceId }
                    .limit(1)
                    .firstOrNull()
                    ?.let { row ->
                        OriginalInvoiceFiscalData(
                            numeroDocumentoFiscal = row[FacturasTablePA.numeroDocumentoFiscal].orEmpty(),
                            fechaFactura = row[FacturasTablePA.fechaFactura].orEmpty(),
                            cufe = row[FacturasTablePA.cufe].orEmpty(),
                        )
                    }
            } ?: throw FEInvoiceNotFoundException("Factura original no encontrada: $sourceInvoiceId")

        if (originalFiscal.cufe.isBlank()) {
            throw FEConfigurationException("La factura original no tiene CUFE: $sourceInvoiceId")
        }
        if (originalFiscal.numeroDocumentoFiscal.isBlank()) {
            throw FEConfigurationException("La factura original no tiene número fiscal: $sourceInvoiceId")
        }

        val creditNoteData =
            dbQuery(database) {
                val header =
                    CreditNoteHeaderTablePA
                        .selectAll()
                        .where { CreditNoteHeaderTablePA.idDevolucion eq creditNoteId }
                        .limit(1)
                        .firstOrNull()
                        ?: throw FEInvoiceNotFoundException("Nota de crédito no encontrada: $creditNoteId")

                val details =
                    CreditNoteDetailTable
                        .join(
                            FEFacturaDetalleReadTable,
                            JoinType.INNER,
                            onColumn = CreditNoteDetailTable.idDetalleFactura,
                            otherColumn = FEFacturaDetalleReadTable.idDetalleFactura,
                        ).join(
                            FEItemReadTable,
                            JoinType.LEFT,
                            onColumn = CreditNoteDetailTable.idItem,
                            otherColumn = FEItemReadTable.idItem,
                        ).join(
                            FEUnidadEmpaquesReadTable,
                            JoinType.LEFT,
                            onColumn = FEItemReadTable.unidadMedida,
                            otherColumn = FEUnidadEmpaquesReadTable.codUnidad,
                        ).selectAll()
                        .where { CreditNoteDetailTable.idDevolucion eq creditNoteId }
                        .orderBy(CreditNoteDetailTable.idDevolucionDetalle)
                        .map { row ->
                            FEDetalleData(
                                descripcion = row[FEFacturaDetalleReadTable.itemDescripcion],
                                codigo = row[CreditNoteDetailTable.itemCodigo],
                                unidadMedida =
                                    row
                                        .getOrNull(FEUnidadEmpaquesReadTable.simbolo)
                                        ?.takeIf { it.isNotBlank() } ?: "und",
                                codigoCPBS = row.getOrNull(FEFacturaDetalleReadTable.idFamilia)?.toString(),
                                codigoCPBSAbrev = row.getOrNull(FEFacturaDetalleReadTable.idSegmento)?.toString(),
                                cantidad = row[CreditNoteDetailTable.itemCantidad].toDouble(),
                                precioSinIva = row[CreditNoteDetailTable.itemPrecioSinIva].toDouble(),
                                montoDescuento = row[CreditNoteDetailTable.itemMontoDescuento].toDouble(),
                                piva = row[CreditNoteDetailTable.itemPIva].toDouble(),
                                totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva].toDouble(),
                                totalConIva = row[CreditNoteDetailTable.itemTotalConIva].toDouble(),
                                porcentajeIsc = null,
                                importeIsc = null,
                                idOti = null,
                                importeOti = null,
                            )
                        }

                CreditNotePayloadData(
                    codigo = header[CreditNoteHeaderTablePA.codDevolucion],
                    fecha = header[CreditNoteHeaderTablePA.fechaDevolucion].toString(),
                    observacion = header[CreditNoteHeaderTablePA.observacion],
                    subtotal = header[CreditNoteHeaderTablePA.subtotal],
                    impuesto = header[CreditNoteHeaderTablePA.impuesto],
                    total = header[CreditNoteHeaderTablePA.total],
                    descuentoGlobal = header[CreditNoteHeaderTablePA.descuentoGlobal] ?: BigDecimal.ZERO,
                    idCaja = header[CreditNoteHeaderTablePA.idCaja],
                    naturalezaOperacion = header[CreditNoteHeaderTablePA.naturalezaOperacion],
                    tipoOperacion = header[CreditNoteHeaderTablePA.tipoOperacion].toString(),
                    formatoCAFE = header[CreditNoteHeaderTablePA.formatoCAFE].toString(),
                    entregaCAFE = header[CreditNoteHeaderTablePA.entregaCAFE].toString(),
                    envioContenedor = header[CreditNoteHeaderTablePA.envioContenedor].toString(),
                    tipoVenta = header[CreditNoteHeaderTablePA.tipoVenta].toString(),
                    detalles = details,
                )
            }

        val creditNoteInvoice =
            invoiceContext.factura.copy(
                idFactura = creditNoteId,
                codFactura = creditNoteData.codigo,
                numeroDocumentoFiscal = normalizedDocumentNumber,
                fechaFactura = creditNoteData.fecha,
                tipoDocumento = "04",
                naturalezaOperacion = creditNoteData.naturalezaOperacion,
                tipoOperacion = creditNoteData.tipoOperacion,
                formatoCAFE = creditNoteData.formatoCAFE,
                entregaCAFE = creditNoteData.entregaCAFE,
                envioContenedor = creditNoteData.envioContenedor,
                tipoVenta = creditNoteData.tipoVenta,
                tipoFactura = "nota_credito",
                observacion = creditNoteData.observacion,
                montoItemsFactura = creditNoteData.subtotal.toDouble(),
                ivaTotalFactura = creditNoteData.impuesto.toDouble(),
                totalTotalFactura = creditNoteData.total.toDouble(),
                totalizarDescuentoGlobal = creditNoteData.descuentoGlobal.toDouble(),
                cajaId = creditNoteData.idCaja ?: invoiceContext.factura.cajaId,
            )

        return PanamaCreditNotePayloadContext(
            invoice =
                invoiceContext.copy(
                    factura = creditNoteInvoice,
                    detalles = creditNoteData.detalles,
                    formasPago = emptyList(),
                    retencion = null,
                    montoCancelar = null,
                    vuelto = null,
                ),
            originalInvoiceCufe = originalFiscal.cufe,
            originalInvoiceDate =
                originalFiscal.fechaFactura.ifBlank {
                    invoiceContext.factura.fechaFactura.orEmpty()
                },
            originalInvoiceFiscalNumber = originalFiscal.numeroDocumentoFiscal,
        )
    }

    /**
     * Actualiza la factura con los datos retornados por el PAC tras un envío exitoso.
     */
    suspend fun updateInvoiceWithFEResponse(
        database: Database,
        invoiceId: String,
        numeroDocumentoFiscal: String,
        puntoFacturacionFiscal: String,
        cufe: String,
        qr: String?,
        fechaRecepcionDGI: String?,
        nroProtocolo: String?,
        fechaLimite: String?,
    ) = dbQuery(database) {
        logger.info("Actualizando factura {} con CUFE={}", invoiceId, cufe)

        FacturasTablePA.update({ FacturasTablePA.idFactura eq invoiceId }) {
            it[FacturasTablePA.numeroDocumentoFiscal] = numeroDocumentoFiscal
            it[FacturasTablePA.puntoFacturacionFiscal] = puntoFacturacionFiscal
            it[FacturasTablePA.cufe] = cufe
            if (fechaRecepcionDGI != null) {
                it[FacturasTablePA.fechaRecepcionDGI] = formatFechaRecepcion(fechaRecepcionDGI)
            }
            if (qr != null) {
                it[FacturasTablePA.qr] = qr
            }
            if (nroProtocolo != null) {
                it[FacturasTablePA.nroProtocoloAutorizacion] = nroProtocolo
            }
            if (fechaLimite != null) {
                it[FacturasTablePA.fechaLimite] = formatFechaRecepcion(fechaLimite)
            }
        }
    }

    /**
     * Incrementa el correlativo del número de documento fiscal en la tabla `correlativos`.
     */
    suspend fun incrementNumeroDocumentoFiscal(database: Database) =
        dbQuery(database) {
            val updated =
                FECorrelativosTable.update({
                    FECorrelativosTable.campo eq "numeroDocumentoFiscal"
                }) {
                    with(SqlExpressionBuilder) {
                        it.update(FECorrelativosTable.contador, FECorrelativosTable.contador + 1)
                    }
                }

            if (updated == 0) {
                logger.warn("No se encontró registro de correlativos para 'numeroDocumentoFiscal'")
            }
        }

    suspend fun getInvoiceCufe(
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

    // ─── Mappers privados ────────────────────────────────────────────────────

    private fun mapConfig(row: ResultRow): FEConfigData {
        val tokenEmpresa =
            row[FEParametrosReadTable.tokenEmpresa]
                ?: throw FEConfigurationException("token_empresa no configurado en parametros_generales")
        val tokenPassword =
            row[FEParametrosReadTable.tokenPassword]
                ?: throw FEConfigurationException("token_password no configurado en parametros_generales")
        val apiTheFactoryHka =
            row[FEParametrosReadTable.api_thefactoryhka]
                ?: throw FEConfigurationException("api_thefactoryhka no configurado en parametros_generales")

        return FEConfigData(
            tokenEmpresa = tokenEmpresa,
            tokenPassword = tokenPassword,
            apiTheFactoryHka = apiTheFactoryHka.trimEnd('/'),
            tipoEmision = row[FEParametrosReadTable.tipoEmision] ?: "01",
            destinoOperacion = row[FEParametrosReadTable.destinoOperacion] ?: "1",
            procesoGeneracion = row[FEParametrosReadTable.procesoGeneracion] ?: "01",
            codigoSucursalEmisorFallback = row[FEParametrosReadTable.codigoSucursalEmisor] ?: "0000",
            puntoFacturacionFiscalFallback = row[FEParametrosReadTable.puntoFacturacionFiscal] ?: "001",
            fechaInicioContingencia = row[FEParametrosReadTable.fechaInicioContingencia],
            motivoContingencia = row[FEParametrosReadTable.motivoContingencia],
            tipoFacturacion = row[FEParametrosReadTable.tipoFacturacion],
        )
    }

    private fun mapFactura(
        row: ResultRow,
        numeroDocFiscal: String,
    ): FEFacturaData =
        FEFacturaData(
            idFactura = row[FEFacturaReadTable.idFactura],
            codFactura = row[FEFacturaReadTable.codFactura],
            numeroDocumentoFiscal = numeroDocFiscal,
            fechaFactura = row[FEFacturaReadTable.fechaFactura],
            tipoDocumento = row[FEFacturaReadTable.tipoDocumento] ?: "01",
            naturalezaOperacion = row[FEFacturaReadTable.naturalezaOperacion] ?: "01",
            tipoOperacion = row[FEFacturaReadTable.tipoOperacion] ?: "1",
            formatoCAFE = row[FEFacturaReadTable.formatoCAFE] ?: "1",
            entregaCAFE = row[FEFacturaReadTable.entregaCAFE] ?: "1",
            envioContenedor = row[FEFacturaReadTable.envioContenedor] ?: "1",
            tipoVenta = row[FEFacturaReadTable.tipoVenta] ?: "1",
            tipoFactura = row[FEFacturaReadTable.tipoFactura],
            observacion = row[FEFacturaReadTable.observacion],
            montoItemsFactura = row[FEFacturaReadTable.montoItemsFactura].toDouble(),
            ivaTotalFactura = row[FEFacturaReadTable.ivaTotalFactura].toDouble(),
            totalTotalFactura = row[FEFacturaReadTable.totalTotalFactura].toDouble(),
            totalizarDescuentoGlobal = row[FEFacturaReadTable.totalizarDescuentoGlobal].toDouble(),
            cajaId = row[FEFacturaReadTable.idCaja],
        )

    private fun mapCliente(
        row: ResultRow,
        paisLocal: Alias<FEPaisesReadTable>,
        paisExtranjero: Alias<FEPaisesReadTable>,
    ): FEClienteData {
        val nombreCompleto =
            buildString {
                append(row.getOrNull(FECientesReadTable.nombre)?.trim().orEmpty())
                val apellido = row.getOrNull(FECientesReadTable.apellido)?.trim().orEmpty()
                if (apellido.isNotBlank()) append(" ").append(apellido)
            }.ifBlank { "CONSUMIDOR FINAL" }

        val paisIso = row.getOrNull(paisLocal[FEPaisesReadTable.iso]) ?: "PA"
        val paisExtIso = row.getOrNull(paisExtranjero[FEPaisesReadTable.iso])

        return FEClienteData(
            tipoClienteFE = row.getOrNull(FETipoClienteReadTable.tipoClienteFE) ?: "02",
            tipoContribuyente = row.getOrNull(FECientesReadTable.tipoContribuyente)?.toString() ?: "1",
            identificacion = row.getOrNull(FECientesReadTable.rif) ?: "",
            dv = row.getOrNull(FECientesReadTable.dv) ?: "",
            nombre = nombreCompleto,
            codigoUbicacion = row.getOrNull(FECientesReadTable.direccionNivel3),
            telefono = row.getOrNull(FECientesReadTable.telefonos),
            correo = row.getOrNull(FECientesReadTable.email),
            direccion = row.getOrNull(FECientesReadTable.direccion),
            paisIso = paisIso,
            paisExtranjeroIso = paisExtIso,
        )
    }

    private fun loadDetalles(invoiceId: String): List<FEDetalleData> =
        FEFacturaDetalleReadTable
            .join(FEItemReadTable, JoinType.LEFT, FEFacturaDetalleReadTable.idItem, FEItemReadTable.idItem)
            .join(FEUnidadEmpaquesReadTable, JoinType.LEFT, FEItemReadTable.unidadMedida, FEUnidadEmpaquesReadTable.codUnidad)
            .selectAll()
            .where { FEFacturaDetalleReadTable.idFactura eq invoiceId }
            .map { row ->
                FEDetalleData(
                    descripcion = row[FEFacturaDetalleReadTable.itemDescripcion],
                    codigo = row[FEFacturaDetalleReadTable.itemCodigo],
                    unidadMedida =
                        row
                            .getOrNull(FEUnidadEmpaquesReadTable.simbolo)
                            ?.takeIf { it.isNotBlank() } ?: "und",
                    codigoCPBS = row.getOrNull(FEFacturaDetalleReadTable.idFamilia)?.toString(),
                    codigoCPBSAbrev = row.getOrNull(FEFacturaDetalleReadTable.idSegmento)?.toString(),
                    cantidad = row[FEFacturaDetalleReadTable.itemCantidad].toDouble(),
                    precioSinIva = row[FEFacturaDetalleReadTable.itemPrecioSinIva].toDouble(),
                    montoDescuento = row[FEFacturaDetalleReadTable.itemMontoDescuento].toDouble(),
                    piva = row[FEFacturaDetalleReadTable.itemPiva].toDouble(),
                    totalSinIva = row[FEFacturaDetalleReadTable.itemTotalSinIva].toDouble(),
                    totalConIva = row[FEFacturaDetalleReadTable.itemTotalConIva].toDouble(),
                    porcentajeIsc = row[FEFacturaDetalleReadTable.porcentajeIsc]?.toDouble(),
                    importeIsc = row[FEFacturaDetalleReadTable.importeIsc]?.toDouble(),
                    idOti = row[FEFacturaDetalleReadTable.idOti],
                    importeOti = row[FEFacturaDetalleReadTable.importeOti]?.toDouble(),
                )
            }

    private fun loadRetencion(invoiceId: String): FERetencionData? {
        val safeInvoiceId = invoiceId.replace("'", "''")
        val result =
            TransactionManager.current().exec(
                """
                SELECT codigo_retencion, totalizar_monto_retencion
                FROM factura_detalle_formapago
                WHERE id_factura = '$safeInvoiceId'
                LIMIT 1
                """.trimIndent(),
            ) { rs ->
                if (!rs.next()) return@exec null
                val codigo = rs.getString("codigo_retencion").safeIntOrZero()
                val monto = rs.getString("totalizar_monto_retencion").safeDoubleOrZero()
                codigo to monto
            } ?: return null

        val (codigo, monto) = result
        if (codigo == 0 || monto <= 0.0) return null

        return FERetencionData(
            codigoRetencion = codigo.toString(),
            montoRetencion = monto,
        )
    }

    private fun loadFormasPago(invoiceId: String): List<FEFormaPagoData> {
        // Buscar el caja_id asociado a esta factura en caja_nueva
        val cajaRow =
            FECajaNuevaReadTable
                .selectAll()
                .where { FECajaNuevaReadTable.idFactura eq invoiceId }
                .limit(1)
                .firstOrNull()

        if (cajaRow == null) {
            logger.warn("No se encontró registro en caja_nueva para factura {}", invoiceId)
            return emptyList()
        }

        val cajaId = cajaRow[FECajaNuevaReadTable.cajaId]

        // JOIN caja_nueva_detalle con caja_forma_pago para obtener siglas y formaPagoFact
        return FECajaNuevaDetalleReadTable
            .join(
                CajaFormaPagoTable,
                JoinType.LEFT,
                onColumn = FECajaNuevaDetalleReadTable.idFormaPago,
                otherColumn = CajaFormaPagoTable.idFormaPago,
            ).selectAll()
            .where { FECajaNuevaDetalleReadTable.cajaId eq cajaId }
            .mapNotNull { row ->
                val monto = row[FECajaNuevaDetalleReadTable.monto]?.toDouble() ?: return@mapNotNull null
                if (monto <= 0) return@mapNotNull null

                val siglas = row.getOrNull(CajaFormaPagoTable.siglas)
                val formaPagoFact = row.getOrNull(CajaFormaPagoTable.formaPagoFact)
                val descripcion = row.getOrNull(CajaFormaPagoTable.descripcion) ?: "Pago"

                FEFormaPagoData(
                    siglas = siglas,
                    formaPagoFact = formaPagoFact,
                    descripcion = descripcion,
                    monto = monto,
                    esCash = siglas?.uppercase()?.trim() in setOf("EF", "CASH", "EFECTIVO"),
                )
            }
    }

    private fun loadVuelto(invoiceId: String): Double? =
        FEFacturaDetalleFormaPagoReadTable
            .select(FEFacturaDetalleFormaPagoReadTable.totalizarCambio)
            .where { FEFacturaDetalleFormaPagoReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?.get(FEFacturaDetalleFormaPagoReadTable.totalizarCambio)
            ?.toDouble()
            ?.takeIf { it > 0 }

    private fun loadMontoCancelar(invoiceId: String): Double? =
        FEFacturaDetalleFormaPagoReadTable
            .select(FEFacturaDetalleFormaPagoReadTable.totalizarMontoCancelar)
            .where { FEFacturaDetalleFormaPagoReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?.get(FEFacturaDetalleFormaPagoReadTable.totalizarMontoCancelar)
            ?.toDouble()
            ?.takeIf { it > 0 }

    private fun resolveCodigoSucursalYPuntoFacturacion(
        cajaId: String,
        idSucursal: Int,
        codigoSucursalFallback: String,
        puntoFacturacionFallback: String,
    ): Pair<String, String> {
        // 1. Intentar leer de la caja
        val cajaRow =
            FECajaReadTable
                .selectAll()
                .where { FECajaReadTable.id eq cajaId }
                .limit(1)
                .firstOrNull()

        if (cajaRow == null) {
            logger.warn("[FE] No se encontró caja con id=$cajaId")
        }

        val codigoFromCaja =
            cajaRow
                ?.get(FECajaReadTable.codigoSucursalEmisor)
                ?.takeIf { it.isNotBlank() }
        val puntoFromCaja =
            cajaRow
                ?.get(FECajaReadTable.puntoFacturacionFiscal)
                ?.takeIf { it.isNotBlank() }

        logger.info("[FE] caja lookup: codigoFromCaja=$codigoFromCaja puntoFromCaja=$puntoFromCaja")

        // 2. Si la caja no lo tiene, intentar desde sucursal
        val codigoFromSucursal =
            if (codigoFromCaja == null) {
                FESucursalReadTable
                    .select(FESucursalReadTable.codigoSucursalEmisor)
                    .where { FESucursalReadTable.id eq idSucursal }
                    .limit(1)
                    .firstOrNull()
                    ?.get(FESucursalReadTable.codigoSucursalEmisor)
                    ?.takeIf { it.isNotBlank() }
                    .also { logger.info("[FE] sucursal lookup id=$idSucursal -> codigoSucursalEmisor=$it") }
            } else {
                null
            }

        // 3. Fallback a parametros_generales
        val codigoFinal = codigoFromCaja ?: codigoFromSucursal ?: codigoSucursalFallback
        val puntoFinal = puntoFromCaja ?: puntoFacturacionFallback

        if (codigoFromCaja == null && codigoFromSucursal == null) {
            logger.warn(
                "[FE] codigoSucursalEmisor no encontrado ni en caja ni en sucursal, usando fallback de parametros_generales: " +
                    "$codigoSucursalFallback",
            )
        }
        if (puntoFromCaja == null) {
            logger.warn(
                "[FE] puntoFacturacionFiscal no encontrado en caja, usando fallback de parametros_generales: " +
                    "$puntoFacturacionFallback",
            )
        }

        return codigoFinal to puntoFinal
    }

    private fun resolveNumeroDocumentoFiscal(): String {
        val row =
            FECorrelativosTable
                .selectAll()
                .where { FECorrelativosTable.campo eq "numeroDocumentoFiscal" }
                .limit(1)
                .firstOrNull()

        if (row == null) {
            logger.warn("No se encontró registro de correlativos para 'numeroDocumentoFiscal', usando 1")
            return "1"
        }

        return (row[FECorrelativosTable.contador] + 1).toString()
    }

    private fun formatFechaRecepcion(isoDate: String): String =
        try {
            isoDate
                .substringBefore("-05:00")
                .substringBefore("-04:00")
                .replace("T", " ")
                .take(19)
        } catch (_: Exception) {
            isoDate.take(19).replace("T", " ")
        }

    private fun String?.safeIntOrZero(): Int =
        this
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
            ?: 0

    private fun String?.safeDoubleOrZero(): Double =
        this
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
            ?: 0.0

    private data class OriginalInvoiceFiscalData(
        val numeroDocumentoFiscal: String,
        val fechaFactura: String,
        val cufe: String,
    )

    private data class CreditNotePayloadData(
        val codigo: String,
        val fecha: String,
        val observacion: String?,
        val subtotal: BigDecimal,
        val impuesto: BigDecimal,
        val total: BigDecimal,
        val descuentoGlobal: BigDecimal,
        val idCaja: String?,
        val naturalezaOperacion: String,
        val tipoOperacion: String,
        val formatoCAFE: String,
        val entregaCAFE: String,
        val envioContenedor: String,
        val tipoVenta: String,
        val detalles: List<FEDetalleData>,
    )
}
