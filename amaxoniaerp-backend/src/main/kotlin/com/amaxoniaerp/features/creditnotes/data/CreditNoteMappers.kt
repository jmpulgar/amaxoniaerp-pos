package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalDocument
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSummary
import org.jetbrains.exposed.sql.ResultRow

internal fun buildDetailResponse(
    header: CreditNoteHeaderContext,
    lines: List<CreditNoteDetailLine>,
): CreditNoteDetailResponse =
    CreditNoteDetailResponse(
        id = header.id,
        codigo = header.codigo,
        facturaId = header.facturaId,
        facturaCodigo = header.facturaCodigo,
        fecha = formatDate(header.fecha),
        periodo = header.periodo,
        observacion = header.observacion,
        clienteNombre = header.clienteNombre,
        clienteIdentificacion = header.clienteIdentificacion,
        subtotal = header.subtotal.toDouble(),
        impuesto = header.impuesto.toDouble(),
        total = header.total.toDouble(),
        fiscalStatus = header.fiscalStatus,
        fiscalNumber = header.fiscalNumber,
        printerSerial = header.printerSerial,
        anulaFacturaCompleta = header.anulaFacturaCompleta,
        lines = lines,
        fiscalDocument =
            CreditNoteFiscalDocument(
                creditNoteId = header.id,
                creditNoteCode = header.codigo,
                date = formatDate(header.fecha),
                customerName = header.clienteNombre,
                customerIdentifier = header.clienteIdentificacion,
                customerAddress = header.clienteDireccion,
                customerPhone = header.clienteTelefono,
                originalInvoiceCode = header.facturaCodigo,
                originalFiscalNumber = header.originalFiscalNumber,
                originalInvoiceDate = formatDate(header.originalInvoiceDate),
                printerSerial = header.printerSerial,
                comment = header.observacion,
                lines =
                    lines.map { line ->
                        CreditNoteFiscalLine(
                            description = line.descripcion,
                            quantity = line.cantidad,
                            unitPriceWithoutTax = line.precioSinIva,
                            totalWithTax = line.totalConIva,
                            taxRate = line.pIva,
                        )
                    },
            ),
    )

internal fun mapSummaryRow(
    row: ResultRow,
    countryCode: String,
): CreditNoteSummary {
    val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
    val clienteNombre =
        listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "CONSUMIDOR FINAL" }
    val fiscalNumber =
        resolveDisplayFiscalNumber(
            codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty(),
            numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty(),
        )

    return CreditNoteSummary(
        id = row[headerTable.idDevolucion],
        codigo = row[headerTable.codDevolucion],
        facturaId = row[headerTable.codFactura],
        facturaCodigo = row[CreditNoteFacturaTable.codFactura],
        fecha = formatDate(row[headerTable.fechaDevolucion]),
        fechaCreacion = formatDateTime(row[headerTable.fechaCreacion]),
        clienteNombre = clienteNombre,
        clienteIdentificacion = row[ClientsTable.rif],
        total = row[headerTable.total].toDouble(),
        subtotal = row[headerTable.subtotal].toDouble(),
        impuesto = row[headerTable.impuesto].toDouble(),
        fiscalStatus =
            resolveFiscalStatus(
                codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty(),
                numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty(),
            ),
        fiscalNumber = fiscalNumber,
        printerSerial =
            if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",
        observacion = row[headerTable.observacion].orEmpty(),
    )
}

internal fun mapHeaderContext(
    row: ResultRow,
    countryCode: String,
): CreditNoteHeaderContext {
    val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
    val clienteNombre =
        listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "CONSUMIDOR FINAL" }

    val codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty()
    val numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty()

    return CreditNoteHeaderContext(
        id = row[headerTable.idDevolucion],
        codigo = row[headerTable.codDevolucion],
        facturaId = row[headerTable.codFactura],
        facturaCodigo = row[CreditNoteFacturaTable.codFactura],
        fecha = row[headerTable.fechaDevolucion],
        fechaCreacion = row[headerTable.fechaCreacion] ?: BusinessClock.nowForCountry(countryCode),
        periodo = row[headerTable.periodoDevolucion].orEmpty(),
        observacion = row[headerTable.observacion].orEmpty(),
        clienteNombre = clienteNombre,
        clienteIdentificacion = row[ClientsTable.rif],
        clienteDireccion = row[CreditNoteFacturaTable.facturarADireccion],
        clienteTelefono = row[CreditNoteFacturaTable.facturarATelefono],
        subtotal = row[headerTable.subtotal],
        impuesto = row[headerTable.impuesto],
        total = row[headerTable.total],
        fiscalStatus = resolveFiscalStatus(codDevolucionFiscal, numeroDocumentoFiscal),
        fiscalNumber = resolveDisplayFiscalNumber(codDevolucionFiscal, numeroDocumentoFiscal),
        printerSerial =
            if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",
        originalFiscalNumber =
            row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty().ifBlank {
                row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty()
            },
        originalInvoiceDate = row[CreditNoteFacturaTable.fechaFactura],
        anulaFacturaCompleta = row[headerTable.total].compareTo(row[CreditNoteFacturaTable.totalTotalFactura]) == 0,
    )
}

internal fun mapDetailLine(row: ResultRow): CreditNoteDetailLine =
    CreditNoteDetailLine(
        id = row[CreditNoteDetailTable.idDevolucionDetalle],
        idDetalleFactura = row[CreditNoteDetailTable.idDetalleFactura],
        idItem = row[CreditNoteDetailTable.idItem],
        descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
        codigo = row[CreditNoteDetailTable.itemCodigo].orEmpty(),
        referencia = row[CreditNoteDetailTable.itemReferencia].orEmpty(),
        cantidad = row[CreditNoteDetailTable.itemCantidad].toDouble(),
        precioSinIva = row[CreditNoteDetailTable.itemPrecioSinIva].toDouble(),
        descuentoPorcentaje = row[CreditNoteDetailTable.itemDescuento].toDouble(),
        descuentoMonto = row[CreditNoteDetailTable.itemMontoDescuento].toDouble(),
        pIva = row[CreditNoteDetailTable.itemPIva].toDouble(),
        totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva].toDouble(),
        totalConIva = row[CreditNoteDetailTable.itemTotalConIva].toDouble(),
    )

internal fun ProcessedLine.toDetailLine(): CreditNoteDetailLine =
    CreditNoteDetailLine(
        id = "",
        idDetalleFactura = sourceLine.idDetalleFactura,
        idItem = sourceLine.idItem,
        descripcion = sourceLine.descripcion,
        codigo = sourceLine.codigo,
        referencia = sourceLine.referencia,
        cantidad = quantity.toDouble(),
        precioSinIva = sourceLine.precioSinIva.toDouble(),
        descuentoPorcentaje = sourceLine.descuentoPorcentaje.toDouble(),
        descuentoMonto = discountAmount.toDouble(),
        pIva = sourceLine.pIva.toDouble(),
        totalSinIva = totalSinIva.toDouble(),
        totalConIva = totalConIva.toDouble(),
    )
