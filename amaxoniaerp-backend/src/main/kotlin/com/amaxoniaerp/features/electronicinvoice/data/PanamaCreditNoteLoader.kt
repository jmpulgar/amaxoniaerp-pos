package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.creditnotes.data.CreditNoteDetailTable
import com.amaxoniaerp.features.creditnotes.data.CreditNoteHeaderTablePA
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PanamaCreditNotePayloadContext
import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.get
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal

internal data class OriginalInvoiceFiscalData(
    val numeroDocumentoFiscal: String,
    val fechaFactura: String,
    val cufe: String,
)

internal data class CreditNotePayloadData(
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

internal fun requireNumeroDocumentoFiscal(numeroDocumentoFiscal: String): String {
    val normalized = numeroDocumentoFiscal.trim()
    if (normalized.isBlank()) {
        throw FEConfigurationException("numeroDocumentoFiscal de NC no resuelto")
    }
    return normalized
}

internal suspend fun loadSourceInvoiceId(
    database: Database,
    creditNoteId: String,
): String =
    dbQuery(database) {
        CreditNoteHeaderTablePA
            .select(CreditNoteHeaderTablePA.codFactura)
            .where { CreditNoteHeaderTablePA.idDevolucion eq creditNoteId }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteHeaderTablePA.codFactura)
    } ?: throw FEInvoiceNotFoundException("Nota de crédito no encontrada: $creditNoteId")

internal suspend fun loadOriginalFiscal(
    database: Database,
    sourceInvoiceId: String,
): OriginalInvoiceFiscalData =
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

internal fun requireOriginalFiscalData(
    originalFiscal: OriginalInvoiceFiscalData,
    sourceInvoiceId: String,
) {
    if (originalFiscal.cufe.isBlank()) {
        throw FEConfigurationException("La factura original no tiene CUFE: $sourceInvoiceId")
    }
    if (originalFiscal.numeroDocumentoFiscal.isBlank()) {
        throw FEConfigurationException("La factura original no tiene número fiscal: $sourceInvoiceId")
    }
}

internal suspend fun loadCreditNoteData(
    database: Database,
    creditNoteId: String,
): CreditNotePayloadData =
    dbQuery(database) {
        val header =
            CreditNoteHeaderTablePA
                .selectAll()
                .where { CreditNoteHeaderTablePA.idDevolucion eq creditNoteId }
                .limit(1)
                .firstOrNull()
                ?: throw FEInvoiceNotFoundException("Nota de crédito no encontrada: $creditNoteId")

        val details = loadCreditNoteDetails(creditNoteId)

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

private fun loadCreditNoteDetails(creditNoteId: String): List<FEDetalleData> =
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
            val (codigoCPBS, codigoCPBSAbrev) =
                resolverCpbs(
                    idFamiliaDetalle = row.getOrNull(FEFacturaDetalleReadTable.idFamilia),
                    idSegmentoDetalle = row.getOrNull(FEFacturaDetalleReadTable.idSegmento),
                    idFamiliaGobItem = row.getOrNull(FEItemReadTable.idFamiliaGob),
                    idSegmentoGobItem = row.getOrNull(FEItemReadTable.idSegmentoGob),
                )
            FEDetalleData(
                descripcion = row[FEFacturaDetalleReadTable.itemDescripcion],
                codigo = row[CreditNoteDetailTable.itemCodigo],
                unidadMedida =
                    row
                        .getOrNull(FEUnidadEmpaquesReadTable.simbolo)
                        ?.takeIf { it.isNotBlank() } ?: "und",
                codigoCPBS = codigoCPBS,
                codigoCPBSAbrev = codigoCPBSAbrev,
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

internal fun buildPanamaCreditNoteContext(
    invoiceContext: InvoiceFEContext,
    creditNoteData: CreditNotePayloadData,
    creditNoteId: String,
    normalizedDocumentNumber: String,
    originalFiscal: OriginalInvoiceFiscalData,
): PanamaCreditNotePayloadContext {
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
