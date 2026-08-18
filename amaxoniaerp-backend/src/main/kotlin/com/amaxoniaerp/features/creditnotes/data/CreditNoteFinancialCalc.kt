package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import java.math.BigDecimal
import java.math.RoundingMode

internal fun normalizeRequestedLines(
    request: CreateCreditNoteRequest,
    invoiceLines: List<SourceInvoiceLine>,
): Map<String, BigDecimal> {
    val requestLines =
        if (request.detalle.isEmpty() && request.anular) {
            invoiceLines
                .filter { it.availableQuantity > BigDecimal.ZERO }
                .map { source -> RequestedLine(source.idDetalleFactura, source.availableQuantity.toDouble()) }
        } else {
            request.detalle.map { RequestedLine(it.idDetalleFactura, it.cantidad) }
        }

    if (requestLines.isEmpty()) {
        throw CreditNoteValidationException("Debes indicar al menos una línea a devolver")
    }

    return requestLines
        .groupBy { it.idDetalleFactura }
        .mapValues { (_, rows) ->
            rows.fold(BigDecimal.ZERO) { acc, line ->
                val quantity = BigDecimal.valueOf(line.cantidad).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
                if (quantity <= BigDecimal.ZERO) {
                    throw CreditNoteValidationException("Las cantidades a devolver deben ser mayores a cero")
                }
                acc + quantity
            }
        }
}

internal fun buildProcessedLines(
    invoiceLines: List<SourceInvoiceLine>,
    requestedLines: Map<String, BigDecimal>,
): List<ProcessedLine> {
    val linesById = invoiceLines.associateBy { it.idDetalleFactura }
    return requestedLines
        .map { (idDetalleFactura, quantity) ->
            val sourceLine =
                linesById[idDetalleFactura]
                    ?: throw CreditNoteValidationException(
                        "La línea $idDetalleFactura no pertenece a la factura origen",
                    )
            if (quantity > sourceLine.availableQuantity) {
                throw CreditNoteValidationException(
                    "La cantidad a devolver para ${sourceLine.descripcion} " +
                        "excede lo disponible (${sourceLine.availableQuantity.toDouble()})",
                )
            }

            val unitTotalSinIva =
                divideSafe(sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE)
            val unitTotalConIva =
                divideSafe(sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE)

            ProcessedLine(
                sourceLine = sourceLine,
                quantity = quantity,
                discountAmount = sourceLine.unitDiscountAmount.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                totalSinIva = unitTotalSinIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                totalConIva = unitTotalConIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                globalDiscountAmount = BigDecimal.ZERO.setScale(2),
            )
        }.sortedBy { it.sourceLine.idDetalleFactura }
}

/** Indica si tras aplicar las líneas devueltas la factura queda totalmente devuelta. */
internal fun resolvesFullReturn(
    invoiceLines: List<SourceInvoiceLine>,
    processedLines: List<ProcessedLine>,
): Boolean =
    invoiceLines.all { sourceLine ->
        val returnedInThisRequest =
            processedLines
                .firstOrNull { it.sourceLine.idDetalleFactura == sourceLine.idDetalleFactura }
                ?.quantity
                ?: BigDecimal.ZERO
        sourceLine.availableQuantity.subtract(returnedInThisRequest).isEffectivelyZero()
    }

internal fun calculateFinancials(
    invoice: InvoiceHeader,
    invoiceLines: List<SourceInvoiceLine>,
    processedLines: List<ProcessedLine>,
    previousTotals: PreviousCreditNoteTotals,
    allReturnedAfterOperation: Boolean,
): CreditNoteFinancials {
    val originalGlobalDiscount = invoice.totalizarDescuentoGlobal.coerceAtLeastZero(2)
    val baseBeforeGlobal =
        invoice.totalizarTotalOperacion.takeIf { it > BigDecimal.ZERO }
            ?: invoiceLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIvaOriginal }
    val remainingGlobalDiscount = (originalGlobalDiscount - previousTotals.globalDiscount).coerceAtLeastZero(2)
    val processedBase = processedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIva }
    val proportionalCurrentDiscount =
        proportionalAmount(
            amount = originalGlobalDiscount,
            numerator = processedBase,
            denominator = baseBeforeGlobal,
        )
    val currentGlobalDiscount =
        if (allReturnedAfterOperation) {
            remainingGlobalDiscount
        } else {
            minBigDecimal(proportionalCurrentDiscount, remainingGlobalDiscount)
        }
    val adjustedLines =
        allocateGlobalDiscountAndAdjust(
            GlobalDiscountAllocationInput(
                invoice = invoice,
                processedLines = processedLines,
                currentGlobalDiscount = currentGlobalDiscount,
                previousTotals = previousTotals,
                processedBase = processedBase,
                allReturnedAfterOperation = allReturnedAfterOperation,
            ),
        )

    return CreditNoteFinancials(
        lines = adjustedLines,
        totals = calculateTotals(adjustedLines),
        globalDiscount = currentGlobalDiscount,
    )
}

/** Entrada del reparto proporcional del descuento global entre líneas. */
private class GlobalDiscountAllocationInput(
    val invoice: InvoiceHeader,
    val processedLines: List<ProcessedLine>,
    val currentGlobalDiscount: BigDecimal,
    val previousTotals: PreviousCreditNoteTotals,
    val processedBase: BigDecimal,
    val allReturnedAfterOperation: Boolean,
)

private fun allocateGlobalDiscountAndAdjust(input: GlobalDiscountAllocationInput): List<ProcessedLine> {
    var allocatedGlobalDiscount = BigDecimal.ZERO.setScale(2)
    var adjustedLines =
        input.processedLines.mapIndexed { index, line ->
            val allocation =
                globalDiscountAllocationFor(
                    LineAllocationInput(
                        line = line,
                        index = index,
                        lines = input.processedLines,
                        processedBase = input.processedBase,
                        currentGlobalDiscount = input.currentGlobalDiscount,
                        allocatedGlobalDiscount = allocatedGlobalDiscount,
                    ),
                )
            allocatedGlobalDiscount += allocation
            val subtotal = (line.totalSinIva - allocation).setScale(2, RoundingMode.HALF_UP)
            val tax = calculateLineTax(subtotal, line.sourceLine.pIva)
            line.copy(
                totalSinIva = subtotal,
                totalConIva = subtotal + tax,
                globalDiscountAmount = allocation,
            )
        }

    if (input.allReturnedAfterOperation && adjustedLines.isNotEmpty()) {
        adjustedLines = alignLastLineWithOriginalTotals(input.invoice, input.previousTotals, adjustedLines)
    }
    return adjustedLines
}

private class LineAllocationInput(
    val line: ProcessedLine,
    val index: Int,
    val lines: List<ProcessedLine>,
    val processedBase: BigDecimal,
    val currentGlobalDiscount: BigDecimal,
    val allocatedGlobalDiscount: BigDecimal,
)

private fun globalDiscountAllocationFor(input: LineAllocationInput): BigDecimal =
    when {
        input.currentGlobalDiscount.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO.setScale(2)
        input.index == input.lines.lastIndex ->
            input.currentGlobalDiscount - input.allocatedGlobalDiscount
        input.processedBase.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO.setScale(2)
        else ->
            minBigDecimal(
                input.currentGlobalDiscount
                    .multiply(input.line.totalSinIva)
                    .divide(input.processedBase, FINANCIAL_DIVISION_SCALE, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP),
                input.currentGlobalDiscount - input.allocatedGlobalDiscount,
            )
    }

// En devolución total: alinea la última línea con el residual de la factura
// original para que la NC cuadre exactamente con lo facturado.
private fun alignLastLineWithOriginalTotals(
    invoice: InvoiceHeader,
    previousTotals: PreviousCreditNoteTotals,
    adjustedLines: List<ProcessedLine>,
): List<ProcessedLine> {
    val originalTotal =
        invoice.totalTotalFactura.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
            ?: invoice.totalizarTotalGeneral
    val targetTotal = (originalTotal - previousTotals.total).setScale(2, RoundingMode.HALF_UP)
    val targetTax = (invoice.totalizarMontoIva - previousTotals.tax).setScale(2, RoundingMode.HALF_UP)
    val targetSubtotal = targetTotal - targetTax
    val currentSubtotal = adjustedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIva }
    val currentTotal = adjustedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalConIva }
    val subtotalResidual = targetSubtotal - currentSubtotal
    val totalResidual = targetTotal - currentTotal
    val lastIndex = adjustedLines.lastIndex
    return adjustedLines.mapIndexed { index, line ->
        if (index != lastIndex) {
            line
        } else {
            line.copy(
                totalSinIva = (line.totalSinIva + subtotalResidual).setScale(2, RoundingMode.HALF_UP),
                totalConIva = (line.totalConIva + totalResidual).setScale(2, RoundingMode.HALF_UP),
            )
        }
    }
}

internal fun proportionalAmount(
    amount: BigDecimal,
    numerator: BigDecimal,
    denominator: BigDecimal,
): BigDecimal {
    if (amount.compareTo(BigDecimal.ZERO) == 0 ||
        numerator.compareTo(BigDecimal.ZERO) <= 0 ||
        denominator.compareTo(BigDecimal.ZERO) <= 0
    ) {
        return BigDecimal.ZERO.setScale(2)
    }
    val boundedNumerator = minBigDecimal(numerator, denominator)
    return amount
        .multiply(boundedNumerator)
        .divide(denominator, FINANCIAL_DIVISION_SCALE, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP)
}

internal fun calculateLineTax(
    subtotal: BigDecimal,
    taxRate: BigDecimal,
): BigDecimal {
    if (subtotal.compareTo(BigDecimal.ZERO) == 0 || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
        return BigDecimal.ZERO.setScale(2)
    }
    return subtotal
        .multiply(taxRate)
        .divide(BigDecimal("100"), FINANCIAL_DIVISION_SCALE, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP)
}

internal fun calculateTotals(lines: List<ProcessedLine>): CreditNoteTotals {
    val subtotal = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalSinIva }
    val total = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalConIva }
    val tax = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP)
    return CreditNoteTotals(subtotal = subtotal, tax = tax, total = total)
}
