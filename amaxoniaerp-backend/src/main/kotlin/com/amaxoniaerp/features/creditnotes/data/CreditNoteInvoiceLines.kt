package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode

internal fun loadInvoiceLines(
    countryCode: String?,
    invoiceId: String,
): List<SourceInvoiceLine> {
    val detailRows =
        CreditNoteFacturaDetalleTable
            .selectAll()
            .where { CreditNoteFacturaDetalleTable.idFactura eq invoiceId }
            .orderBy(CreditNoteFacturaDetalleTable.idDetalleFactura)
            .toList()
    if (detailRows.isEmpty()) return emptyList()

    val returnedByDetail = returnedQuantityByDetail(countryCode, detailRows)

    return detailRows.map { row -> mapSourceInvoiceLine(row, returnedByDetail) }
}

private fun returnedQuantityByDetail(
    countryCode: String?,
    detailRows: List<ResultRow>,
): Map<String, BigDecimal> {
    val detailIds = detailRows.map { it[CreditNoteFacturaDetalleTable.idDetalleFactura] }
    val allReturnedRows =
        CreditNoteDetailTable
            .selectAll()
            .where { CreditNoteDetailTable.idDetalleFactura inList detailIds }
            .toList()
    val returnedRows =
        if (countryCode.equals("PA", ignoreCase = true)) {
            allReturnedRows.filterNot { it[CreditNoteDetailTable.idDevolucion] in rejectedCreditNoteIdsPA() }
        } else {
            allReturnedRows
        }
    return returnedRows
        .groupBy { it[CreditNoteDetailTable.idDetalleFactura] }
        .mapValues { (_, rows) ->
            rows.fold(
                BigDecimal.ZERO,
            ) { acc, row ->
                acc + row[CreditNoteDetailTable.itemCantidad].setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
            }
        }
}

// Algunos esquemas de pruebas sólo tienen las tablas comunes.
private fun rejectedCreditNoteIdsPA(): Set<String> =
    try {
        CreditNoteHeaderTablePA
            .selectAll()
            .mapNotNull { row ->
                row[CreditNoteHeaderTablePA.codDevolucionFiscal]
                    .orEmpty()
                    .trim()
                    .takeIf { it.equals(REJECTED_FISCAL_CODE, ignoreCase = true) }
                    ?.let { row[CreditNoteHeaderTablePA.idDevolucion] }
            }.toSet()
    } catch (_: ExposedSQLException) {
        emptySet()
    }

private fun mapSourceInvoiceLine(
    row: ResultRow,
    returnedByDetail: Map<String, BigDecimal>,
): SourceInvoiceLine {
    val quantityOriginal =
        row[CreditNoteFacturaDetalleTable.itemCantidadTotal].setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
    val returned =
        returnedByDetail[row[CreditNoteFacturaDetalleTable.idDetalleFactura]]
            ?: BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
    val available = quantityOriginal.subtract(returned).coerceAtLeastZero(QUANTITY_SCALE)
    val unitTotalSinIva =
        divideSafe(
            row[CreditNoteFacturaDetalleTable.itemTotalSinIva],
            quantityOriginal,
            UNIT_CALCULATION_SCALE,
        )
    val unitTotalConIva =
        divideSafe(
            row[CreditNoteFacturaDetalleTable.itemTotalConIva],
            quantityOriginal,
            UNIT_CALCULATION_SCALE,
        )
    val unitDiscount =
        divideSafe(
            row[CreditNoteFacturaDetalleTable.itemMontoDescuento],
            quantityOriginal,
            UNIT_CALCULATION_SCALE,
        )

    return SourceInvoiceLine(
        idDetalleFactura = row[CreditNoteFacturaDetalleTable.idDetalleFactura],
        idItem = row[CreditNoteFacturaDetalleTable.idItem],
        descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
        codigo = row[CreditNoteFacturaDetalleTable.itemCodigo].orEmpty(),
        referencia = row[CreditNoteFacturaDetalleTable.itemReferencia].orEmpty(),
        quantityOriginal = quantityOriginal,
        returnedQuantity = returned,
        availableQuantity = available,
        precioSinIva = row[CreditNoteFacturaDetalleTable.itemPrecioSinIva],
        descuentoPorcentaje = row[CreditNoteFacturaDetalleTable.itemDescuento],
        descuentoMontoTotal = row[CreditNoteFacturaDetalleTable.itemMontoDescuento],
        pIva = row[CreditNoteFacturaDetalleTable.itemPIva],
        totalSinIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalSinIva],
        totalConIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalConIva],
        availableTotalSinIva = unitTotalSinIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
        availableTotalConIva = unitTotalConIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
        unitDiscountAmount = unitDiscount,
        almacen = row[CreditNoteFacturaDetalleTable.itemAlmacen],
        codVendedor = row[CreditNoteFacturaDetalleTable.codVendedor],
    )
}

internal fun loadPreparedLines(
    creditNoteId: String,
    invoiceLines: List<SourceInvoiceLine>,
): List<ProcessedLine> {
    val sourceLinesById = invoiceLines.associateBy { it.idDetalleFactura }
    return CreditNoteDetailTable
        .selectAll()
        .where { CreditNoteDetailTable.idDevolucion eq creditNoteId }
        .orderBy(CreditNoteDetailTable.idDevolucionDetalle)
        .map { row ->
            val sourceLine =
                sourceLinesById[row[CreditNoteDetailTable.idDetalleFactura]]
                    ?: throw CreditNoteValidationException("La línea preparada ya no pertenece a la factura origen")
            ProcessedLine(
                sourceLine = sourceLine,
                quantity = row[CreditNoteDetailTable.itemCantidad],
                discountAmount = row[CreditNoteDetailTable.itemMontoDescuento],
                totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva],
                totalConIva = row[CreditNoteDetailTable.itemTotalConIva],
                globalDiscountAmount = BigDecimal.ZERO.setScale(2),
            )
        }
}
